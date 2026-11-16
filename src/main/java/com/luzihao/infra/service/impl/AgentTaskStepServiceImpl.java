package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.AgentTaskStep;
import com.luzihao.infra.mapper.AgentTaskStepMapper;
import com.luzihao.infra.service.AgentTaskStepService;
import org.springframework.stereotype.Service;

/**
 * Agent 任务步骤 Service 实现
 */
@Service
public class AgentTaskStepServiceImpl extends ServiceImpl<AgentTaskStepMapper, AgentTaskStep> implements AgentTaskStepService {
}
