package com.eics.service.impl;

import com.eics.entity.EkDocument;
import com.eics.entity.EkDocumentChunk;
import com.eics.mapper.EkDocumentChunkMapper;
import com.eics.mapper.EkDocumentMapper;
import com.eics.service.DocumentService;
import com.eics.service.SmartChunker;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档管理服务 — 上传到 MinIO → Tika 解析 → SmartChunker 切片 → Milvus 向量化入库
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final EkDocumentMapper documentMapper;
    private final EkDocumentChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final SmartChunker chunker;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentServiceImpl(EkDocumentMapper documentMapper,
                               EkDocumentChunkMapper chunkMapper,
                               VectorStore vectorStore,
                               SmartChunker chunker,
                               MinioClient minioClient) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.minioClient = minioClient;
    }

    @Override
    public EkDocument upload(MultipartFile file) {
        // 确保 Bucket 存在
        ensureBucket();

        // 上传到 MinIO
        String objectKey = uploadToMinIO(file);

        EkDocument doc = new EkDocument();
        doc.setTitle(file.getOriginalFilename());
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(getFileType(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setMinioPath(objectKey);    // 存储 MinIO 对象 Key
        doc.setStatus("PARSING");
        documentMapper.insert(doc);
        log.info("文档入库: id={}, name={}, minioKey={}", doc.getId(), doc.getFileName(), objectKey);

        try {
            ingest(doc, file);
        } catch (Exception e) {
            log.error("文档摄取失败: id={}", doc.getId(), e);
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
        }
        return doc;
    }

    /**
     * Tika 解析 + 切片 + 向量化
     * 直接从 MultipartFile 字节流解析，不依赖本地文件
     */
    private void ingest(EkDocument doc, MultipartFile file) {
        List<String> chunks;
        java.nio.file.Path tempFile = null;
        try {
            // 写临时文件以保留文件名（Tika 需要文件名判断格式）
            String originalName = file.getOriginalFilename();
            String suffix = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".tmp";
            tempFile = java.nio.file.Files.createTempFile("eics-", suffix);
            file.transferTo(tempFile.toFile());

            TikaDocumentReader reader = new TikaDocumentReader(
                    new org.springframework.core.io.FileSystemResource(tempFile.toFile()));
            String fullText = reader.get().stream()
                    .map(Document::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);
            doc.setStatus("PARSING");
            documentMapper.updateById(doc);
            log.info("Tika 解析完成: {} 字", fullText.length());

            chunks = chunker.chunk(fullText);
            log.info("切片完成: {} 片", chunks.size());
        } catch (Exception e) {
            log.error("Tika 解析/切片失败", e);
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
            return;
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }

        if (chunks.isEmpty()) {
            doc.setStatus("FAILED");
            documentMapper.updateById(doc);
            return;
        }

        // 批量写入 MySQL 切片 + Milvus 向量
        List<Document> vectorDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            EkDocumentChunk chunk = new EkDocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setCharCount(chunkText.length());
            chunkMapper.insert(chunk);

            vectorDocs.add(new Document(chunkText,
                    Map.of("document_id", doc.getId().toString(),
                           "title", doc.getTitle(),
                           "chunk_index", String.valueOf(i))));
        }

        vectorStore.add(vectorDocs);
        log.info("向量写入 Milvus 完成: {} 条", vectorDocs.size());

        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);
    }

    @Override
    public void parseDocument(Long documentId) {
        // re-parse 需要先从 MinIO 下载文件，当前简化处理
        log.warn("re-parse 暂不支持 (documentId={}), 请重新上传文档", documentId);
    }

    @Override
    public void chunkAndVectorize(Long documentId) {
        log.warn("re-chunk 暂不支持 (documentId={}), 请重新上传文档", documentId);
    }

    // ==================== MinIO 操作 ====================

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO Bucket '{}' 已创建", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO Bucket 初始化失败: " + bucket, e);
        }
    }

    /**
     * 上传文件到 MinIO，返回对象 Key
     * Key 格式: yyyyMMdd/uuid_filename
     */
    private String uploadToMinIO(MultipartFile file) {
        try {
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String objectKey = dateDir + "/" + UUID.randomUUID().toString().substring(0, 8)
                    + "_" + file.getOriginalFilename();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(file.getBytes()), file.getSize(), -1)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build());
            log.info("文件已上传 MinIO: bucket={}, key={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 上传失败", e);
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        String name = fileName.toLowerCase();
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".docx") || name.endsWith(".doc")) return "docx";
        if (name.endsWith(".txt")) return "txt";
        if (name.endsWith(".md")) return "md";
        return "unknown";
    }
}
