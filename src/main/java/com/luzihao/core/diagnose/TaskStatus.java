package com.luzihao.core.diagnose;

public enum TaskStatus {

    PENDING,           // 已创建，等待执行
    RUNNING,           // 执行中
    SUCCEEDED,         // 成功，报告已生成
    FAILED,            // 失败（重试耗尽或不可重试错误）
    TIMED_OUT,         // 超过最大耗时
    WAITING_APPROVAL   // 高风险动作等待人工审批（第 07 章）
}
