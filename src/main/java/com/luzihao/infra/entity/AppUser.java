package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("app_user")
public class AppUser {

    @TableId
    private String id;
    private String username;
    private String passwordHash;
    private String tenantId;
    private Boolean enabled;
    private Date createdAt;
    private Date updatedAt;
}
