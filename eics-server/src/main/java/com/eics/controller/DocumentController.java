package com.eics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eics.common.Result;
import com.eics.entity.EkDocument;
import com.eics.entity.EkDocumentChunk;
import com.eics.mapper.EkDocumentChunkMapper;
import com.eics.mapper.EkDocumentMapper;
import com.eics.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "知识库文档管理", description = "文档上传 / 列表 / 删除（需坐席登录）")
@Slf4j
@RestController
@RequestMapping("/api/v1/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final EkDocumentMapper documentMapper;
    private final EkDocumentChunkMapper chunkMapper;

    @Operation(summary = "上传文档", description = "上传 PDF/Word/TXT/MD，自动解析、切片、向量化入库")
    @PostMapping("/upload")
    public Result<EkDocument> upload(@RequestParam("file") MultipartFile file) {
        log.info("文档上传: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        EkDocument doc = documentService.upload(file);
        return Result.ok("文档上传成功，正在后台解析", doc);
    }

    @Operation(summary = "文档列表", description = "分页查询，支持状态/分类筛选和关键词搜索（需登录）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        LambdaQueryWrapper<EkDocument> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(EkDocument::getStatus, status);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(EkDocument::getCategory, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(EkDocument::getTitle, keyword);
        }
        wrapper.orderByDesc(EkDocument::getCreateTime);
        Page<EkDocument> pageResult = documentMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", page,
                "size", size
        ));
    }

    @Operation(summary = "更新文档元数据", description = "更新文档分类和标签")
    @PutMapping("/{id}/meta")
    public Result<Void> updateMeta(@PathVariable Long id, @RequestBody Map<String, String> req) {
        EkDocument doc = documentMapper.selectById(id);
        if (doc == null) return Result.notFound("文档不存在");
        if (req.containsKey("category")) doc.setCategory(req.get("category"));
        if (req.containsKey("tags")) doc.setTags(req.get("tags"));
        documentMapper.updateById(doc);
        return Result.ok(null);
    }

    @Operation(summary = "删除文档", description = "逻辑删除文档并清除关联的切片数据")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        EkDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        // 1. 物理删除关联切片（向量暂不同步清理，Milvus 中成为孤立向量）
        chunkMapper.delete(new LambdaQueryWrapper<EkDocumentChunk>()
                .eq(EkDocumentChunk::getDocumentId, id));
        // 2. 逻辑删除文档主记录
        documentMapper.deleteById(id);
        log.info("文档已删除: id={}, title={}", id, doc.getTitle());
        return Result.ok("文档已删除", null);
    }
}
