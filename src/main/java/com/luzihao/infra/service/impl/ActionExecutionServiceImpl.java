package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.ActionExecution;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.mapper.ActionExecutionMapper;
import com.luzihao.infra.mapper.AgentTaskMapper;
import com.luzihao.infra.service.ActionExecutionService;
import com.luzihao.infra.service.AgentTaskService;
import org.springframework.stereotype.Service;

/**
 * Agent 任务 Service 实现
 */
@Service
public class ActionExecutionServiceImpl extends ServiceImpl<ActionExecutionMapper, ActionExecution> implements ActionExecutionService {
}
