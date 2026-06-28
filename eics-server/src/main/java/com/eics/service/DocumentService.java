package com.eics.service;

import com.eics.entity.EkDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理服务 — 上传、解析、切片、写入向量库
 */
public interface DocumentService {

    /**
     * 上传文档（保存到 MinIO + 入库 + 异步解析切片）
     * @return 文档实体
     */
    EkDocument upload(MultipartFile file);

    /**
     * 解析文档内容（Tika 提取文本）
     * @param documentId 文档 ID
     */
    void parseDocument(Long documentId);

    /**
     * 对文档进行文本切片 + 写入 Milvus 向量库
     * @param documentId 文档 ID
     */
    void chunkAndVectorize(Long documentId);
}
