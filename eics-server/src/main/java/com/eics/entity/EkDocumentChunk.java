package com.eics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片向量表 — ek_document_chunk
 */
@Data
@TableName("ek_document_chunk")
public class EkDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID */
    private Long documentId;

    /** 切片序号（从 0 开始） */
    private Integer chunkIndex;

    /** 切片文本内容 */
    private String content;

    /** 切片字符数 */
    private Integer charCount;

    /** Milvus 向量 ID（写入 Milvus 后回填，用于检索定位） */
    private Long milvusId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
