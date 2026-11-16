package com.luzihao.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.core.ai.PgChatMemoryStore;
import com.luzihao.core.user.UserContext;
import com.luzihao.infra.entity.ChatMessage;
import com.luzihao.infra.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatSessionService {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private PgChatMemoryStore pgChatMemoryStore;

    /** 会话列表（当前用户）：按 sessionId 分组取最后一条消息 + 条数 */
    public List<Map<String,Object>> listSessions(){
        String userId = UserContext.require().getUserId();
        List<ChatMessage> all = chatMessageService.list(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getUserId, userId).orderByAsc(ChatMessage::getId));
        // 分组聚合：sessionId → (lastMessage, count, lastActiveAt)——数据量小直接内存分组
        Map<String,List<ChatMessage>> bySession = new LinkedHashMap<>();
        all.forEach(m -> bySession.computeIfAbsent(m.getSessionId(), k -> new ArrayList<>()).add(m));
        return bySession.entrySet().stream().map( e -> {
            List<ChatMessage> msgs = e.getValue();
            return Map.<String, Object>of(
                    "sessionId", e.getKey(),
                    "lastMessage", msgs.get(msgs.size() - 1).getContent(),
                    "messageCount", msgs.size(),
                    "lastActiveAt", msgs.get(msgs.size() - 1).getCreatedAt().toString());

        } ).sorted((a, b) -> b.get("lastActiveAt").toString()
                        .compareTo(a.get("lastActiveAt").toString()))   // 最后活跃倒序
                .toList();
    }

    /** 历史消息（由旧到新）；非本人会话返回空（Controller 转 404） */
    public List<ChatMessage> messages(String sessionId){
        String userId = UserContext.require().getUserId();
        return chatMessageService.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId,sessionId).eq(ChatMessage::getUserId,userId).orderByAsc(ChatMessage::getId));
    }
    /** 删除会话：清表 + 清 LangChain4j 记忆；幂等 */
    public void deleteSession(String sessionId){
        String userId = UserContext.require().getUserId();
        chatMessageService.remove(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId,sessionId).eq(ChatMessage::getUserId,userId));
        pgChatMemoryStore.deleteMessages(sessionId);
    }

}
