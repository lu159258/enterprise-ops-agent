package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务 Mapper
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
