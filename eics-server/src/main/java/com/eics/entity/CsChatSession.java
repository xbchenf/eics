package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话主表 — cs_chat_session
 */
@Data
@TableName("cs_chat_session")
public class CsChatSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户标识（前端传入，如设备ID/Cookie） */
    private String userId;

    /** 会话状态：BOT(机器人接待) / WAITING(待人工接入) / AGENT(人工处理中) / CLOSED(已关闭) */
    private String status;

    /** 分配的坐席 ID（转人工后赋值） */
    private Long agentId;

    /** Rasa sender_id（与 Rasa 会话关联） */
    private String rasaSenderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;
}
