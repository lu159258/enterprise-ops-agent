package com.luzihao.api.controller;

import com.luzihao.api.Eumn.HttpStatusEnum;
import com.luzihao.api.result.Result;
import com.luzihao.core.ChatSessionService;
import com.luzihao.infra.entity.ChatMessage;
import com.luzihao.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionManageController {
    @Autowired
    private ChatSessionService chatSessionService;

    @GetMapping
    public Result list(){
        Map<String, Object> map = Map.of("sessions", chatSessionService.listSessions(),
                "total", chatSessionService.listSessions().size());
        return ResultUtil.successResult(HttpStatusEnum.OK,map);
    }

    @GetMapping("/{id}/message")
    public Result messages(@PathVariable("id") String id){
        List<ChatMessage> msgs = chatSessionService.messages(id);
        if (msgs.isEmpty()) {
            return ResultUtil.errorResult(HttpStatusEnum.BAD_REQUEST,"会话不存在");
        }

        return ResultUtil.successResult(HttpStatusEnum.OK,msgs);

    }

    @GetMapping("/delete/{id}")
    public Result delete(@PathVariable("id") String id) {   // 返回 204（@ResponseStatus(HttpStatus.NO_CONTENT)）
        chatSessionService.deleteSession(id);
        return ResultUtil.successResult(HttpStatusEnum.OK);
    }
}
