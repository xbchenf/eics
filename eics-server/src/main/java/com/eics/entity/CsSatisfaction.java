package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cs_satisfaction")
public class CsSatisfaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long orderId;
    private Long userId;
    private Long agentId;
    private Integer rating;         // 1-5
    private String comment;         // 选填留言
    private String source;          // SESSION / ORDER / MANUAL
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
