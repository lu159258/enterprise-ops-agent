package com.luzihao.api.controller;

import com.luzihao.api.dto.ChatRequest;
import com.luzihao.api.dto.ChatResponse;
import com.luzihao.core.ChatService;
import com.luzihao.core.ai.OpsAssistant;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import dev.langchain4j.service.TokenStream;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;
    @Autowired
    private OpsAssistant opsAssistant;

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest chatRequest){
        String chat = chatService.chat(chatRequest.sessionId(), chatRequest.message());
        return new ChatResponse(chatRequest.sessionId(),chat, Instant.now());
    }

    @PostMapping(value = "/chat/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req){
        UserInfo userInfo = UserContext.require();
        SseEmitter emitter = new SseEmitter(120_000L);
        TokenStream stream = opsAssistant.streamChat(req.sessionId(), req.message());
        stream.onPartialResponse(token -> send(emitter,"token", Map.of("delta",token)))
                .onToolExecuted(spec -> send(emitter,"tool",Map.of("name",spec.request().name())))
                .onCompleteResponse(resp ->{
                    send(emitter,"done",Map.of("finishReason",resp.finishReason().name()));
                }).onError(t ->{
                    send(emitter,"error",Map.of("message","生成失败,稍后尝试"));
                }).start();
        return emitter;

    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) { /* 客户端断开：静默，等待超时回收 */ }
    }
}
