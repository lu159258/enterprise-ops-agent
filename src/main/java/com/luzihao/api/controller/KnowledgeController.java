package com.luzihao.api.controller;

import com.luzihao.api.Eumn.HttpStatusEnum;
import com.luzihao.api.dto.IngestRequest;
import com.luzihao.api.dto.IngestResult;
import com.luzihao.api.dto.KnowledgeSearchResponse;
import com.luzihao.api.request.StatusUpdateRequest;
import com.luzihao.api.result.Result;
import com.luzihao.api.result.StatusUpdateResult;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.KnowledgeDocument;
import com.luzihao.rag.KnowledgeService;
import com.luzihao.utils.ResultUtil;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.store.embedding.IngestionResult;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {
    @Autowired
    private KnowledgeService knowledgeService;



    /** 检索：返回带 evidenceId 与来源的命中列表 */
    @GetMapping("/search")
    public KnowledgeSearchResponse search(@RequestParam String query) {
        var user = UserContext.require();
        return new KnowledgeSearchResponse(query, knowledgeService.search(user.getTenantId(), query));
    }
    /** 上传文档：；租户取当前用户，不能由请求指定（防跨租户写入） */
    @PostMapping("/upload")
    public Result upload(@RequestParam("file")MultipartFile file,

                         @RequestParam(value = "permissionTag",required = false) String permissionTag
                         ){
        try {
            String filename = file.getOriginalFilename();
            UserInfo user = UserContext.require();

            //String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            Document parse = new ApacheTikaDocumentParser().parse(file.getInputStream());
            String content = parse.text();
            KnowledgeDocument document = knowledgeService.ingest(user.getTenantId(), filename, content, permissionTag, "upload:" + filename);
            IngestResult result = new IngestResult(document.getId(), document.getTitle(), document.getVersion(), document.getChecksum());
            return ResultUtil.successResult(HttpStatusEnum.OK,result);
        }catch (Exception e){
            log.error("上传文件到知识库失败:",e);
            return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR);

        }
    }

    /** 停用/恢复文档：PG status 变更 + ES 向量同步删/写回 */
    @PostMapping("/documents/status")
    public Result updateStatus(@RequestBody StatusUpdateRequest request){
        StatusUpdateResult result = knowledgeService.updateStatus(request.docId(), request.status());
        return ResultUtil.successResult(HttpStatusEnum.OK,result);
    }
    /** 删除文档（§4.B2）：PG 删行 + ES 向量删除，幂等 */
    @GetMapping("/delete/doc/{id}")
    public Result deleteDoc(@PathVariable("id") String id){
        knowledgeService.delete(id);
        return ResultUtil.successResult(HttpStatusEnum.OK);
    }

}
