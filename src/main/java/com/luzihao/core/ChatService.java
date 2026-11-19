package com.luzihao.core;

import com.luzihao.core.ai.OpsAssistant;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatService {
    @Autowired
    private OpsAssistant opsAssistant;
    @Autowired
    private UserSessionRegistry userSessionRegistry;

    public String chat(String sessionId,String message){
        if (!StringUtils.hasText(sessionId)) throw new IllegalArgumentException("sessionId 不能为空");
        if (!StringUtils.hasText(message)) throw new IllegalArgumentException("message 不能为空");

        sessionId = sessionId.trim();
        message = message.trim();
        userSessionRegistry.register(sessionId, UserContext.require());
        return opsAssistant.chat(sessionId,message);
    }
}
