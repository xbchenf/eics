package com.eics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eics.entity.EkDocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EkDocumentChunkMapper extends BaseMapper<EkDocumentChunk> {

    /** 根据文档ID查询所有切片（按序号排序） */
    @Select("SELECT * FROM ek_document_chunk WHERE document_id = #{documentId} ORDER BY chunk_index")
    List<EkDocumentChunk> selectByDocumentId(@Param("documentId") Long documentId);
}
