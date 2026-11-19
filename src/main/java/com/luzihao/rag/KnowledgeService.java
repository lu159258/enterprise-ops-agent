package com.luzihao.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.api.result.StatusUpdateResult;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.KnowledgeDocument;
import com.luzihao.infra.service.KnowledgeDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.*;

@Service
@Slf4j
public class KnowledgeService {

    @Autowired
    private KnowledgeDocumentService documentService;
    @Autowired
    private  Optional<KnowledgeVectorStore> vectorStore;
    @Value("${app.rag.mode}")
    private  String mode;

    @Value("${app.rag.top-k}")
    int topK;

    @Value("${app.rag.rrf-k}")
    int rrfK;
/*
    public KnowledgeService(KnowledgeDocumentService documentService, Optional<KnowledgeVectorStore> vectorStore,
                            @Value("${app.rag.mode:keyword}") String mode, @Value("${app.rag.top-k:5}") int topK) {
        this.documentService = documentService;
        this.vectorStore = vectorStore;
        this.mode = mode;
        this.topK = topK;
    }*/

    /**
     * 入库：校验 → checksum 查重 → 存表 → （向量模式）分块入向量库
     */
    public KnowledgeDocument ingest(String tenantId, String title, String content,
                                    String permissionTag, String sourceUri) {
        if (!StringUtils.hasText(title) || title.length() > 255)
            throw new IllegalArgumentException("title 必填且不超过 255 字符");
        if (!StringUtils.hasText(content))
            throw new IllegalArgumentException("content 不能为空");

        String tag = StringUtils.hasText(permissionTag) ? permissionTag : "PUBLIC";

        String checkSum = sha256(content);
        boolean[] isNewDoc = {false};

        KnowledgeDocument knowledgeDocument = documentService.getOne(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getChecksum, checkSum));
        if (null == knowledgeDocument) {
            isNewDoc[0] = true;
            knowledgeDocument = new KnowledgeDocument();
            knowledgeDocument.setTenantId(tenantId);
            knowledgeDocument.setTitle(title);
            knowledgeDocument.setSourceUri(sourceUri);
            knowledgeDocument.setChecksum(checkSum);
            knowledgeDocument.setContent(content);
            knowledgeDocument.setPermissionTag(tag);
            knowledgeDocument.setCreatedAt(new Date());
            knowledgeDocument.setUpdatedAt(new Date());
            documentService.save(knowledgeDocument);
        } else {
            knowledgeDocument.reimport();
            documentService.updateById(knowledgeDocument);
        }

        if (isNewDoc[0] && "vector".equals(mode)) {
            KnowledgeDocument finalKnowledgeDocument = knowledgeDocument;
            vectorStore.ifPresent(s -> s.ingest(finalKnowledgeDocument));
        }


        return knowledgeDocument;
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("checksum 计算失败", e);
        }
    }

    /**
     * 检索：租户隔离在所有路径上强制执行
     */
    public List<KnowledgeHit> search(String tenantId, String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query 不能为空");
        }

        List<KnowledgeHit> byVector = tryVectorSearch(tenantId, query, topK);
        List<KnowledgeHit> byKetWord = keywordSearch(tenantId, query);


        //RRF融合
        Map<String,Double> scores = new HashMap<>(); // key = hit 的 documentId
        Map<String,List<KnowledgeHit>> byId = new HashMap<>();
        rank(byVector,scores,byId,rrfK);
        rank(byKetWord,scores,byId,rrfK);
        return  scores.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed())
                .limit(topK).map(e -> byId.get(e.getKey()).getFirst())
                .toList();

    }



    /** RRF 核心：一路结果的每个文档按名次累加 1/(k+rank) */
    private void rank(List<KnowledgeHit> ranked,Map<String,Double> scores,Map<String,List<KnowledgeHit>> byId,int k){
        for (int i = 0; i < ranked.size(); i++) {
            String docId = ranked.get(i).documentId();
            scores.merge(docId,1.0/(k+i+1),Double::sum);
            byId.computeIfAbsent(docId, x ->new ArrayList<>()).add(ranked.get(i));
        }
    }

    /**
     * 设计文档测试场景 9：向量库不可用时降级关键词，返回清晰结果
     */

    private List<KnowledgeHit> searchWithFallback(String tenantId, String query) {
        try {

            List<KnowledgeHit> hitList = vectorStore.get().search(tenantId, query, topK);
            return hitList.isEmpty() ? keywordSearch(tenantId,query) : hitList;
        } catch (Exception e) {
            log.info("向量检索失败，降级为关键词检索: {}", e.getMessage());
            return keywordSearch(tenantId, query);
        }
    }

    /**
     * 关键词检索：命中文档 → 段落切片定位片段
     */
    private List<KnowledgeHit> keywordSearch(String tenantId, String query) {
        String keyword = query.toLowerCase();
        return documentService.searchByKeyword(tenantId, keyword, topK).stream().map(
                doc -> {
                    KnowledgeHit knowledgeHit = new KnowledgeHit(
                            "doc-" + doc.getId(),
                            doc.getId(),
                            doc.getTitle(),
                            locateSnippet(doc.getContent(), keyword),
                            doc.getSourceUri(),
                            doc.getPermissionTag(),
                            1.0);
                    return knowledgeHit;
                }
        ).toList();
    }

    /**
     * 找到第一个包含关键词的段落，截 800 字符作为片段（片段过短会导致模型拿不到回答所需的完整上下文）
     */
    private String locateSnippet(String content, String keyword) {
        for (String para : content.split("\n\n")) {
            if (para.toLowerCase().contains(keyword)) {
                return para.length() > 800 ? para.substring(0, 800) + "..." : para;
            }
        }
        return content.length() > 800 ? content.substring(0, 800) + "..." : content;


    }

    /** 混合模式的向量路——失败/不可用一律返回空集，由 RRF 融合层兜底。
     *  注意这里不降级 keywordSearch：关键词已是独立另一路在跑，
     *  再降级会产生双份关键词结果污染排序（两路命中同一文档分数虚高） */
    private List<KnowledgeHit> tryVectorSearch(String tenantId,String query,int topK){
        if (vectorStore.isEmpty()){
            return List.of();
        }

        try {
           return vectorStore.get().search(tenantId,query,topK);
        }catch (Exception e){
            log.warn("混合模式向量路失败，本轮由关键词路独扛: {}", e.getMessage());
            return List.of();
        }
    }

    public StatusUpdateResult updateStatus(String id,String status){
        UserInfo userInfo = UserContext.require();
        KnowledgeDocument doc = documentService.getById(id);
        if (null == doc || !doc.getTenantId().equals(userInfo.getTenantId())){
            throw new IllegalArgumentException("文档不存在或无权操作: " + id);

        }

        doc.setStatus(status);
        documentService.updateById(doc);
        // 停用 → 删向量  恢复 ACTIVE → 先清后写回
        boolean esSynced = "INACTIVE".equals(status) ? deleteVectorsFromEs(doc.getId()) : reingestVectors(doc);
        return new StatusUpdateResult(doc.getId(),doc.getStatus(),esSynced);

    }
    /** 恢复 ACTIVE 时向量写回：先清旧向量再重灌，避免 ES 残留重复 chunk */

    private boolean reingestVectors(KnowledgeDocument doc){
        if (vectorStore.isEmpty()){
            return true;
        }

        try {
            vectorStore.get().removeByDocId(doc.getId());
            vectorStore.get().ingest(doc);
            return true;
        }catch (Exception e){
            log.error("ES 向量写回失败 docId={}，待对账补偿", doc.getId(), e);
            return false;
        }
    }

    private boolean deleteVectorsFromEs(String docId) {
        if (vectorStore.isEmpty()) return true;
        try {
            vectorStore.get().removeByDocId(docId);
            return true;
        } catch (Exception e) {
            log.error("ES 向量删除失败 docId={}，待对账补偿", docId, e);
            return false;
            // 不回滚 PG：状态已变更是事实；残留向量由对账 Runner 收拾
        }
    }

    /** 删除：PG 删行 + ES 尽力删，全程幂等 */
    public void delete(String id) {
        String tenantId = UserContext.require().getTenantId();
        KnowledgeDocument doc = documentService.getById(id);
        if (doc == null || !tenantId.equals(doc.getTenantId())) {
            return;                                         // 不存在/无权 → 幂等返回（重复删 204）
        }
        documentService.removeById(id);
        deleteVectorsFromEs(doc.getId());                   // 失败留给对账 Runner，不回滚 PG
    }
}