package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.ChatMessage;
import com.luzihao.infra.entity.SysRole;
import com.luzihao.infra.mapper.ChatMessageMapper;
import com.luzihao.infra.mapper.SysRoleMapper;
import com.luzihao.infra.service.ChatMessageService;
import com.luzihao.infra.service.SysRoleService;
import org.springframework.stereotype.Service;

/**
 * Agent 任务 Service 实现
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {
}
