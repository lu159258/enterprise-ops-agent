package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.AgentTaskStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务步骤 Mapper
 */
@Mapper
public interface AgentTaskStepMapper extends BaseMapper<AgentTaskStep> {
}
