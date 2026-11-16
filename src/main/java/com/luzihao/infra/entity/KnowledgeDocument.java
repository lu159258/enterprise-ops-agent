package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 知识文档表
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 主键（雪花 ID，字符串存储） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 租户标识（多租户隔离） */
    private String tenantId;

    /** 文档标题 */
    private String title;

    /** 文档来源地址 */
    private String sourceUri;

    /** 内容校验和（判重/变更检测） */
    private String checksum;

    /** 文档版本号，默认 1 */
    private Integer version;

    /** 权限标签，默认 PUBLIC */
    private String permissionTag;

    /** 状态：ACTIVE / INACTIVE，默认 ACTIVE */
    private String status;

    /** 文档正文内容 */
    private String content;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    public void reimport() { this.version++; this.updatedAt = new Date(); }

}
