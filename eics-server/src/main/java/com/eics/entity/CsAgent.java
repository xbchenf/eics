package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工坐席表 — cs_agent
 */
@Data
@TableName("cs_agent")
public class CsAgent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 坐席姓名（显示用） */
    private String name;

    /** 角色：ADMIN / AGENT */
    private String role;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
