package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档主表 — ek_document
 */
@Data
@TableName("ek_document")
public class EkDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    private String title;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：pdf / docx / txt */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MinIO 存储路径 */
    private String minioPath;

    /** 文档分类 */
    private String category;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档状态：PENDING(待解析) / PARSING(解析中) / READY(可用) / FAILED(失败) */
    private String status;

    /** 切片总数 */
    private Integer chunkCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
