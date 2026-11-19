package com.luzihao.core.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.core.user.UserSessionRegistry;
import com.luzihao.infra.service.ChatMessageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class PgChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private UserSessionRegistry userSessionRegistry;

    /** 还原某会话的全部历史消息（LangChain4j 要求按时间由旧到新） */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = (String) memoryId;
        return chatMessageService.list(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>()
                        .eq(com.luzihao.infra.entity.ChatMessage::getSessionId, sessionId)
                        .orderByAsc(com.luzihao.infra.entity.ChatMessage::getId))
                .stream()
                .flatMap(row -> deserialize(row).stream())
                .toList();
    }

    @Override
    public void updateMessages(Object o, List<ChatMessage> list) {
        String sessionId = String.valueOf(o);
        String userId = resolveUserId(sessionId);
        chatMessageService.remove(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>().eq(com.luzihao.infra.entity.ChatMessage::getSessionId,sessionId));

        for (ChatMessage msg : list) {
            com.luzihao.infra.entity.ChatMessage row = new com.luzihao.infra.entity.ChatMessage();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setMsgType(msg.type().name());
            // 官方序列化器：JSON 内含类型信息，完整支持 SYSTEM/工具调用等所有消息类型
            row.setContent(ChatMessageSerializer.messageToJson(msg));
            row.setCreatedAt(new Date());
            chatMessageService.save(row);
        }
    }

    @Override
    public void deleteMessages(Object o) {
        String sessionId = String.valueOf(o);
        chatMessageService.remove(new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>()
                .eq(com.luzihao.infra.entity.ChatMessage::getSessionId, sessionId));
    }

    /**
     * 消息 ↔ 存储格式：
     * 新格式：ChatMessageSerializer 输出的 JSON（以 { 或 [ 开头），保留完整类型与工具调用信息；
     * 存量兼容：早期 USER/AI 存的纯文本按 msgType 还原；旧 hutool JSON 无法解析时跳过。
     */
    private List<ChatMessage> deserialize(com.luzihao.infra.entity.ChatMessage row) {
        String content = row.getContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            if (content.startsWith("[")) {
                return ChatMessageDeserializer.messagesFromJson(content);
            }
            if (content.startsWith("{")) {
                return List.of(ChatMessageDeserializer.messageFromJson(content));
            }
        } catch (Exception e) {
            // 含早期 hutool 写入的无效 JSON（如 "{}"），跳过该条
            log.warn("历史消息反序列化失败跳过 id={}", row.getId(), e);
            return List.of();
        }
        return switch (row.getMsgType()) {
            case "USER" -> List.of(UserMessage.from(content));
            case "AI" -> List.of(AiMessage.from(content));
            // 存量 SYSTEM 等旧格式跳过：系统提示词由 @SystemMessage 每次会话重新注入，不影响上下文
            default -> List.of();
        };
    }

    private String resolveUserId(String sessionId) {
        com.luzihao.infra.entity.ChatMessage chatMessage = chatMessageService.getOne(
                new LambdaQueryWrapper<com.luzihao.infra.entity.ChatMessage>()
                        .eq(com.luzihao.infra.entity.ChatMessage::getSessionId, sessionId)
                        .last("LIMIT 1"));
        if (null != chatMessage) {
            return chatMessage.getUserId();
        }
        // 首次会话无历史消息：流式链路此处运行在 SDK 回调线程，ThreadLocal 不可用，优先查注册表
        UserInfo userInfo = userSessionRegistry.resolve(sessionId);
        if (null != userInfo) {
            return userInfo.getUserId();
        }
        return UserContext.require().getUserId();
    }
}
