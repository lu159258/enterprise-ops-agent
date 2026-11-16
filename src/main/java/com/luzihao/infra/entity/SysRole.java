package com.luzihao.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId
    private String id;
    private String code;
    private String name;
    private String description;
    private Date createdAt;
    private String allowedServices;
}
