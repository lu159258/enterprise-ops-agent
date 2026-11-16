package com.luzihao.api.controller;

import com.luzihao.api.Eumn.HttpStatusEnum;
import com.luzihao.api.request.ApprovalDecisionRequest;
import com.luzihao.api.result.ApprovalResult;
import com.luzihao.api.result.Result;
import com.luzihao.core.diagnose.ApprovalService;
import com.luzihao.core.diagnose.TaskStatus;
import com.luzihao.core.ops.ActionExecutor;
import com.luzihao.core.ops.ActionRequest;
import com.luzihao.core.ops.ActionResult;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.entity.AgentTaskStep;
import com.luzihao.infra.service.AgentTaskService;
import com.luzihao.infra.service.AgentTaskStepService;
import com.luzihao.utils.KeyUtils;
import com.luzihao.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/tasks/approval")
public class ApprovalController {
    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentTaskStepService agentTaskStepService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ActionExecutor actionExecutor;

    /**
     * 审批决定：true=批准执行，false=拒绝建议
     */
    @PostMapping
    public Result decide(@RequestBody ApprovalDecisionRequest decision) {
        UserInfo userInfo = UserContext.require();
        AgentTask task = agentTaskService.getById(decision.taskId());
        if (null == task){
           return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR.getCode(), "任务不存在");
        }

        // 审批人不能是发起人（发起人自批 → 403）
        if (task.getUserId().equals(userInfo.getUserId())) {
            return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR.getCode(), "发起人不能审批自己的任务");

        }
        // 审批人须具备 APPROVER 或 ADMIN 角色（RBAC 集合判断）
        if (!userInfo.hasRole("APPROVER") && !userInfo.hasRole("ADMIN")) {
            return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR.getCode(), "无审批权限");

        }

        if (!Objects.equals(task.getStatus(), TaskStatus.WAITING_APPROVAL.name())){
            return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR.getCode(), "任务不在待审批状态");


        }

        AgentTaskStep step = new AgentTaskStep();
        step.setId(KeyUtils.get32UUID());
        step.setTaskId(decision.taskId());
        step.setStepType("APPROVAL");
        step.setToolName("mockActionExecutor");
        step.setStepNo(99);
        step.setStatus(TaskStatus.PENDING.name());
        step.setStartedAt(new Date());
        agentTaskStepService.save(step);
        step.recordInput("approved=" + decision.approved() + " operator=" + userInfo.getUserId());
        try {
            if (decision.approved()){
                ActionRequest request = new ActionRequest(decision.taskId(), userInfo.getUserId(), "restart", task.getServiceName(), task.getId() + "-restart");
                ActionResult result = actionExecutor.execute(request);
                step.succeed("[%s] %s".formatted(result.mode(), result.detail()));
            }else {
                step.succeed("用户拒绝执行建议");
            }
            agentTaskStepService.updateById(step);
            task.markSucceeded(task.getResultJson());
            agentTaskService.updateById(task);
            approvalService.clear(decision.taskId());
            ApprovalResult approvalResult = new ApprovalResult(task.getStatus(), decision.approved());
            return ResultUtil.successResult(HttpStatusEnum.OK,approvalResult);

        }catch (RuntimeException e){
            step.fail("APPROVAL_FAILED", e.getMessage());
            agentTaskStepService.updateById(step);
            return ResultUtil.errorResult(HttpStatusEnum.INTERNAL_SERVER_ERROR);
        }

    }


}
