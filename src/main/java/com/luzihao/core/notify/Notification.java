package com.luzihao.core.notify;

import com.luzihao.core.ops.ServiceStatus;
import com.luzihao.infra.entity.AgentTask;

public record Notification(String type,        // TASK_CREATED / APPROVAL_REQUIRED
                           String taskId,
                           String serviceName,
                           String summary) {

    public static Notification taskCreated(AgentTask task, ServiceStatus status) {
        return new Notification("TASK_CREATED", task.getId(), task.getServiceName(),
                "巡检发现异常状态：" + status.status());
    }

    public static Notification approvalRequired(AgentTask task, String summary) {
        return new Notification("APPROVAL_REQUIRED", task.getId(), task.getServiceName(), summary);
    }
}
