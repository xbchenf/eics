package com.eics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eics.entity.CsChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CsChatMessageMapper extends BaseMapper<CsChatMessage> {

    /** 根据会话ID查询全部消息（按时间正序） */
    @Select("SELECT * FROM cs_chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<CsChatMessage> selectBySessionId(@Param("sessionId") String sessionId);
}
