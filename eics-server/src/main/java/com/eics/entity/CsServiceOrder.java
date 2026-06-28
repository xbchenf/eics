package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服工单表 — cs_service_order
 */
@Data
@TableName("cs_service_order")
public class CsServiceOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联会话 ID */
    private String sessionId;

    /** 用户手机号（脱敏存储） */
    private String phone;

    /** 故障描述 */
    private String faultDescription;

    /** 设备编号 */
    private String deviceId;

    /** 问题类型：HARDWARE / SOFTWARE / NETWORK / PERMISSION / OTHER */
    private String issueType;

    /** 工单状态：PENDING(待处理) / PROCESSING(处理中) / RESOLVED(已解决) / CLOSED(已关闭) */
    private String status;

    /** 优先级：P0(紧急)/P1(高)/P2(中)/P3(低) */
    private String priority;

    /** SLA 截止时间 */
    private LocalDateTime slaDeadline;

    /** 首次响应时间（坐席认领时记录） */
    private LocalDateTime firstResponseTime;

    /** 受理坐席 ID */
    private Long agentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 解决时间 */
    private LocalDateTime resolveTime;
}
