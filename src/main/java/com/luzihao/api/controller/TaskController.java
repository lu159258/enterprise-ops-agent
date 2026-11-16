package com.luzihao.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luzihao.api.dto.StepView;
import com.luzihao.api.dto.TaskDetail;
import com.luzihao.core.diagnose.DiagnoseReport;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.entity.AgentTaskStep;
import com.luzihao.infra.service.AgentTaskService;
import com.luzihao.infra.service.AgentTaskStepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentTaskStepService agentTaskStepService;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 查询任务状态、报告与步骤审计。只能看自己的任务（数据权限） */
    @GetMapping("/{id}")
    public TaskDetail get(@PathVariable("id") String id){
        UserInfo userInfo = UserContext.require();
        AgentTask task = agentTaskService.getById(id);
        if (null == task){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }

        if (!task.getUserId().equals(userInfo.getUserId())){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该任务");

        }
        DiagnoseReport report = null;
        if (task.getResultJson() != null){
            try {
                report =  JSON.readValue(task.getResultJson(),DiagnoseReport.class);

            }catch (Exception e){

            }
        }

        List<AgentTaskStep> stepList = agentTaskStepService.list(new LambdaQueryWrapper<AgentTaskStep>()
                .eq(AgentTaskStep::getTaskId,id)
                .orderByAsc(AgentTaskStep::getStepNo)
        );
        return new TaskDetail(task.getId(), task.getStatus(), task.getInputSummary(),
                task.getRetryCount(), task.getErrorCode(), report, task.getResultJson(),
                task.getCreatedAt(), task.getFinishedAt(),
                stepList.stream().map(StepView::from).toList());

    }
}
