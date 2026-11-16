package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.luzihao.core.diagnose.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Agent 任务主表
 */
@Data
@TableName("agent_task")
public class AgentTask {

    /** 主键（雪花 ID，字符串存储） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /** 发起用户标识 */
    private String userId;

    /** 任务类型，如 OPS_ANALYSIS / REPORT_GEN */
    private String taskType;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** 输入摘要（最长 1000 字） */
    private String inputSummary;

    /** 执行结果 JSON */
    private String resultJson;

    /** 失败错误码 */
    private String errorCode;

    /** 已重试次数 */
    private Integer retryCount;

    /** 幂等键（唯一约束） */
    private String idempotencyKey;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;

    /** 开始执行时间 */
    private Date startedAt;

    /** 结束时间 */
    private Date finishedAt;

    private String serviceName;

    public void markRunning() {
        this.status = String.valueOf(TaskStatus.RUNNING);
        this.startedAt = new Date();
        touch();
    }

    public void markSucceeded(String resultJson) {
        this.status = String.valueOf(TaskStatus.SUCCEEDED);
        this.resultJson = resultJson;
        this.finishedAt =  new Date();
        touch();
    }

    public void markFailed(TaskStatus status, String errorCode) {
        this.status = status.name();        // FAILED 或 TIMED_OUT
        this.errorCode = errorCode;
        this.finishedAt =  new Date();
        touch();
    }

    public void increaseRetry() { this.retryCount++; touch(); }

    private void touch() { this.updatedAt =  new Date(); }
    /** 第 07 章：报告先保存（供审批查看），任务挂起 WAITING_APPROVAL */
    public void holdForApproval(String resultJson) {
        this.status = String.valueOf(TaskStatus.WAITING_APPROVAL);
        this.resultJson = resultJson;
        touch();
    }

}
