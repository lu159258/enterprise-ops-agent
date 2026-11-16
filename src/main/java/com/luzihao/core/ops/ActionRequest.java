package com.luzihao.core.ops;

public record ActionRequest(String taskId,           // 关联诊断任务
                            String approverId,       // 谁批的（01 篇后是真实身份）
                            String action,           // 动作名：restart / rollback / scale ...
                            String serviceName,      // 目标服务（白名单已校验过）
                            String idempotencyKey) {
}
