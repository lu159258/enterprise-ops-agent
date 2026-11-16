package com.luzihao.core.diagnose;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.service.AgentTaskService;
import com.luzihao.utils.KeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
@Slf4j
public class DiagnoseService {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private DiagnoseExecutor executor;

    /**
     * 创建诊断任务。
     * 幂等：同 Idempotency-Key 重复请求返回同一个任务（设计文档可靠性第 1 条）。
     */
    public AgentTask start(UserInfo userInfo, String question,
                           String serviceName, String idempotencyKey){
        // 流程第 1 步：校验（身份已由拦截器保证，这里校验业务字段与权限）
        if (!StringUtils.hasText(question)) throw new IllegalArgumentException("question 不能为空");
        if (question.length() > 2000) throw new IllegalArgumentException("question 不能超过 2000 字符");
        String target = serviceName == null ? null : serviceName.trim().toLowerCase();
        if (StringUtils.hasText(target) && !userInfo.getAllowedServices().contains(target)) {
            throw new IllegalArgumentException("无权诊断服务：" + target);
        }

        // 流程第 2 步：创建 task，PENDING
        if (StringUtils.hasText(idempotencyKey)){
            AgentTask agentTask = agentTaskService.getOne(new LambdaQueryWrapper<AgentTask>().eq(AgentTask::getIdempotencyKey, idempotencyKey));
            if (null != agentTask){
                return agentTask;
            }
        }

        AgentTask agentTask = new AgentTask();
        agentTask.setId(KeyUtils.get32UUID());
        agentTask.setUserId(userInfo.getUserId());
        agentTask.setTaskType("DIAGNOSE");
        agentTask.setInputSummary(truncate(question,1000));
        agentTask.setIdempotencyKey(idempotencyKey);
        agentTask.setStatus(TaskStatus.PENDING.name());
        agentTask.setCreatedAt(new Date());
        agentTask.setUpdatedAt(new Date());
        agentTask.setServiceName(serviceName);
        agentTaskService.save(agentTask);
        executor.execute(agentTask.getId(), userInfo.getUserId(), userInfo.getTenantId(), question,target);
        return agentTask;
    }

    private String truncate(String s, int max) { return s.substring(0, Math.min(s.length(), max)); }

}
