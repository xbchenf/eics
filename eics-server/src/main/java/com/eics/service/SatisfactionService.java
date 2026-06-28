package com.eics.service;

import java.util.Map;

/**
 * 满意度评价服务
 */
public interface SatisfactionService {

    /** 用户提交评价 */
    Map<String, Object> submit(String sessionId, Long userId, Integer rating, String comment);

    /** 查询满意度统计（管理员视角全局 / 坐席视角含个人） */
    Map<String, Object> stats(Long agentId);

    /** 用户查询待评价的已关闭会话 */
    java.util.List<Map<String, Object>> pending(Long userId);

    /** 坐席查看自己被评价的记录 */
    java.util.List<Map<String, Object>> myRatings(Long agentId, int page, int size);
}
