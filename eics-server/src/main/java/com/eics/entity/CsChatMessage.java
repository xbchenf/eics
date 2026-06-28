package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息明细表 — cs_chat_message
 */
@Data
@TableName("cs_chat_message")
public class CsChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID */
    private String sessionId;

    /** 发送方：USER(用户) / BOT(机器人) / AGENT(人工坐席) */
    private String senderType;

    /** 消息内容 */
    private String content;

    /** 消息类型：TEXT / IMAGE / FILE */
    private String msgType;

    /** 附加元数据（JSON，如知识库溯源链接、工单编号等） */
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
