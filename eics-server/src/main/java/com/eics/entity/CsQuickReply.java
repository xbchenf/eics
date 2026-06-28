package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cs_quick_reply")
public class CsQuickReply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;       // NULL=公用
    private String title;
    private String content;
    private String category;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
