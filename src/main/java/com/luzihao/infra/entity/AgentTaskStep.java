package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Agent 任务执行步骤表
 */
@Data
@TableName("agent_task_step")
public class AgentTaskStep {

    /** 主键（雪花 ID，字符串存储） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 关联任务 ID（注意：表中为 bigint 类型） */
    private String taskId;

    /** 步骤序号 */
    private Integer stepNo;

    /** 步骤类型：LLM_CALL / TOOL_CALL */
    private String stepType;

    /** 调用的工具名称 */
    private String toolName;

    /** 步骤状态：RUNNING / SUCCESS / FAILED / SKIPPED */
    private String status;

    /** 输入摘要（最长 500 字） */
    private String inputSummary;

    /** 输出摘要（最长 500 字） */
    private String outputSummary;

    /** 失败错误码 */
    private String errorCode;

    /** 开始时间 */
    private Date startedAt;

    /** 结束时间 */
    private Date finishedAt;

    public void recordInput(String inputSummary) { this.inputSummary = truncate(inputSummary); }

    /** 设计文档要求：审计摘要截断，敏感内容不入库 */
    private String truncate(String s) { return s == null ? null : s.substring(0, Math.min(s.length(), 500)); }

    public void succeed(String outputSummary) {
        this.status = "SUCCEEDED";
        this.outputSummary = truncate(outputSummary);
        this.finishedAt = new Date();
    }

    public void skip(String reason) {
        this.status = "SKIPPED";
        this.outputSummary = truncate(reason);
        this.finishedAt = new Date();
    }

    public void fail(String errorCode, String detail) {
        this.status = "FAILED";
        this.errorCode = errorCode;
        this.outputSummary = truncate(detail);
        this.finishedAt = new Date();
    }
}
