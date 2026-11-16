package com.luzihao.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.infra.entity.KnowledgeDocument;
import com.luzihao.infra.service.KnowledgeDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class KnowledgeService {

    private final KnowledgeDocumentService documentService;
    private final Optional<KnowledgeVectorStore> vectorStore;
    private final String mode;
    private final int topK;
    private final int maxContentLength = 50_000;

    public KnowledgeService(KnowledgeDocumentService documentService, Optional<KnowledgeVectorStore> vectorStore,
                            @Value("${app.rag.mode:keyword}") String mode, @Value("${app.rag.top-k:5}") int topK) {
        this.documentService = documentService;
        this.vectorStore = vectorStore;
        this.mode = mode;
        this.topK = topK;
    }

    /**
     * 入库：校验 → checksum 查重 → 存表 → （向量模式）分块入向量库
     */
    public KnowledgeDocument ingest(String tenantId, String title, String content,
                                    String permissionTag, String sourceUri) {
        if (!StringUtils.hasText(title) || title.length() > 255)
            throw new IllegalArgumentException("title 必填且不超过 255 字符");
        if (!StringUtils.hasText(content))
            throw new IllegalArgumentException("content 不能为空");
        if (content.length() > maxContentLength)
            throw new IllegalArgumentException("content 超过 " + maxContentLength + " 字符上限");
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

        return "vector".equals(mode) && vectorStore.isPresent() ? searchWithFallback(tenantId,query) : keywordSearch(tenantId,query);
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
     * 找到第一个包含关键词的段落，截 300 字符作为片段
     */
    private String locateSnippet(String content, String keyword) {
        for (String para : content.split("\n\n")) {
            if (para.toLowerCase().contains(keyword)) {
                return para.length() > 300 ? para.substring(0, 300) + "..." : para;
            }
        }
        return content.length() > 300 ? content.substring(0, 300) + "..." : content;


    }
}