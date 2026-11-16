package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("action_execution")
public class ActionExecution {
    @TableId
    private String id;
    private String taskId;
    private String approverId;
    private String action;
    private String serviceName;
    private String mode;
    private String result;
    private String detail;
    private String idempotencyKey;
    private Date createdAt;
}
