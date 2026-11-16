package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
