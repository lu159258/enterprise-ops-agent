package com.luzihao.api.controller;

import com.luzihao.api.dto.IngestRequest;
import com.luzihao.api.dto.IngestResult;
import com.luzihao.api.dto.KnowledgeSearchResponse;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.KnowledgeDocument;
import com.luzihao.rag.KnowledgeService;
import dev.langchain4j.store.embedding.IngestionResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    @Autowired
    private KnowledgeService knowledgeService;

    /** 导入文档：租户取自当前用户，不能由请求指定（防跨租户写入） */
    @PostMapping("/ingest")
    public IngestResult ingest(@Valid @RequestBody IngestRequest request){
        UserInfo userInfo = UserContext.require();
        KnowledgeDocument doc = knowledgeService.ingest(userInfo.getTenantId(), request.title(), request.content(), request.permissionTag(), request.sourceUri());
        return new IngestResult(doc.getId(), doc.getTitle(), doc.getVersion(), doc.getChecksum());
    }

    /** 检索：返回带 evidenceId 与来源的命中列表 */
    @GetMapping("/search")
    public KnowledgeSearchResponse search(@RequestParam String query) {
        var user = UserContext.require();
        return new KnowledgeSearchResponse(query, knowledgeService.search(user.getTenantId(), query));
    }
}
