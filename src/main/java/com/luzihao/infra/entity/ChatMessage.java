package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)                    // GENERATED ALWAYS AS IDENTITY → 自增
    private Long id;
    private String sessionId;
    private String userId;
    private String msgType;
    private String content;
    private Date createdAt;
}
