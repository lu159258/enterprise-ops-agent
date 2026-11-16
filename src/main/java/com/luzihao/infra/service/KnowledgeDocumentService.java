package com.luzihao.infra.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.luzihao.infra.entity.KnowledgeDocument;

import java.util.List;

/**
 * 知识文档 Service
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 按关键词检索当前租户的活跃文档（标题或正文，忽略大小写），
     * 按更新时间倒序，最多返回 limit 条
     *
     * @param tenantId 租户标识
     * @param keyword  关键词（忽略大小写模糊匹配）
     * @param limit    最大返回条数
     */
    List<KnowledgeDocument> searchByKeyword(String tenantId, String keyword, int limit);
}
