package com.luzihao.core.ai;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luzihao.core.user.UserContext;
import com.luzihao.infra.service.ChatMessageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class PgChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ObjectMapper objectMapper;

    /** 还原某会话的全部历史消息（LangChain4j 要求按时间由旧到新） */

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = (String) memoryId;
        return chatMessageService.list(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>()
                .eq(com.luzihao.infra.entity.ChatMessage::getSessionId,sessionId)
                .orderByAsc(com.luzihao.infra.entity.ChatMessage::getId)

        ).stream().map(this::deserialize).filter(Objects::nonNull).toList();
    }

    @Override
    public void updateMessages(Object o, List<ChatMessage> list) {

        String sessionId = String.valueOf(o);
        String userId = resolveUserId(sessionId);
        chatMessageService.remove(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>().eq(com.luzihao.infra.entity.ChatMessage::getSessionId,sessionId));


        for (ChatMessage chatMessage : list) {
            com.luzihao.infra.entity.ChatMessage row = new com.luzihao.infra.entity.ChatMessage();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setMsgType(chatMessage.type().name());
            row.setContent(serialize(chatMessage));
            row.setCreatedAt(new Date());
            chatMessageService.save(row);
        }


    }
    /** 消息 ↔ 存储格式：UserMessage/AiMessage 存纯文本；工具消息存 JSON（含工具名/参数/结果） */
    private String serialize(ChatMessage msg)  {
        return switch (msg){
            case UserMessage u -> u.singleText();
            case AiMessage a -> a.text() == null ? JSONUtil.toJsonStr(a) : a.text();
            default -> JSONUtil.toJsonStr(msg);
        };
    }

    @Override
    public void deleteMessages(Object o) {
        String sessionId = String.valueOf(o);

        chatMessageService.remove(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>().eq(com.luzihao.infra.entity.ChatMessage::getSessionId,sessionId));

    }

    private ChatMessage deserialize(com.luzihao.infra.entity.ChatMessage row) {
        try {
            return switch (row.getMsgType()) {
                case "USER"  -> UserMessage.from(row.getContent());
                case "AI"    -> row.getContent().startsWith("{")
                        ? objectMapper.readValue(row.getContent(), AiMessage.class)
                        : AiMessage.from(row.getContent());
                default -> objectMapper.readValue(row.getContent(), ChatMessage.class); // Jackson 多态反序列化
            };
        } catch (Exception e) {
            log.warn("历史消息反序列化失败跳过 id={}", row.getId(), e);
            return null;
        }
    }

    private String resolveUserId(String sessionId){
        com.luzihao.infra.entity.ChatMessage chatMessage = chatMessageService.getOne(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>()
                .eq(com.luzihao.infra.entity.ChatMessage::getSessionId, sessionId)
                .last("LIMIT 1")
        );

        if (null != chatMessage){
            return chatMessage.getUserId();
        }

        return UserContext.require().getUserId();
    }
}
