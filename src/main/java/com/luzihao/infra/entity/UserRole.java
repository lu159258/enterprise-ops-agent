package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_role")
public class UserRole {
    @TableId
    private String id;
    private String userId;
    private String roleId;
    private Date createdAt;
}
