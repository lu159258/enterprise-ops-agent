package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.KnowledgeDocument;
import com.luzihao.infra.mapper.KnowledgeDocumentMapper;
import com.luzihao.infra.service.KnowledgeDocumentService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识文档 Service 实现
 */
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> implements KnowledgeDocumentService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Override
    public List<KnowledgeDocument> searchByKeyword(String tenantId, String keyword, int limit) {
        // PostgreSQL 的 ILIKE 等价于 LOWER(x) LIKE LOWER(y)，忽略大小写模糊匹配
        String likeKeyword = "%" + keyword + "%";
        return list(
                // searchCount=false：只需 LIMIT 截断，跳过 count 查询，减少一次全表扫描
                new Page<KnowledgeDocument>(1, limit, false),
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getTenantId, tenantId)
                        .eq(KnowledgeDocument::getStatus, STATUS_ACTIVE)
                        // and(...) 保证 OR 条件被括号包裹，不破坏外层 AND 语义
                        .and(w -> w.apply("title ILIKE {0}", likeKeyword)
                                .or()
                                .apply("content ILIKE {0}", likeKeyword))
                        .orderByDesc(KnowledgeDocument::getUpdatedAt));
    }
}
