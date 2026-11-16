package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.mapper.AgentTaskMapper;
import com.luzihao.infra.service.AgentTaskService;
import org.springframework.stereotype.Service;

/**
 * Agent 任务 Service 实现
 */
@Service
public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {
}
