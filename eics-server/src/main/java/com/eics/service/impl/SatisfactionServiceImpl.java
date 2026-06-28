package com.eics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eics.entity.CsChatSession;
import com.eics.entity.CsSatisfaction;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.mapper.CsSatisfactionMapper;
import com.eics.service.SatisfactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SatisfactionServiceImpl implements SatisfactionService {

    private final CsSatisfactionMapper satisfactionMapper;
    private final CsChatSessionMapper sessionMapper;

    @Override
    @Transactional
    public Map<String, Object> submit(String sessionId, Long userId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }

        // 查会话获取 agentId
        CsChatSession session = sessionMapper.selectById(sessionId);

        CsSatisfaction sat = new CsSatisfaction();
        sat.setSessionId(sessionId);
        sat.setUserId(userId);
        sat.setRating(rating);
        sat.setComment(comment != null && comment.length() > 500 ? comment.substring(0, 500) : comment);
        sat.setSource("SESSION");

        if (session != null) {
            sat.setAgentId(session.getAgentId());
        }

        try {
            satisfactionMapper.insert(sat);
            log.info("满意度评价提交: session={}, userId={}, rating={}", sessionId, userId, rating);
        } catch (DuplicateKeyException e) {
            log.warn("会话 {} 已评价，重复提交被忽略", sessionId);
            return Map.of("duplicate", true, "message", "该会话已评价过");
        }

        return Map.of("duplicate", false, "message", "感谢您的评价！");
    }

    @Override
    public Map<String, Object> stats(Long agentId) {
        // 全局统计
        List<CsSatisfaction> all = satisfactionMapper.selectList(null);
        double avgRating = all.stream().mapToInt(CsSatisfaction::getRating).average().orElse(0.0);
        Map<Integer, Long> distribution = all.stream()
                .collect(Collectors.groupingBy(CsSatisfaction::getRating, Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
        result.put("totalCount", all.size());
        result.put("distribution", distribution);

        // 坐席个人统计
        if (agentId != null) {
            List<CsSatisfaction> myList = satisfactionMapper.selectList(
                    new LambdaQueryWrapper<CsSatisfaction>().eq(CsSatisfaction::getAgentId, agentId));
            double myAvg = myList.stream().mapToInt(CsSatisfaction::getRating).average().orElse(0.0);
            result.put("myAvgRating", Math.round(myAvg * 10.0) / 10.0);
            result.put("myTotalCount", myList.size());
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> pending(Long userId) {
        String userIdStr = String.valueOf(userId);

        // 查该用户所有已关闭会话
        List<CsChatSession> closedSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CsChatSession>()
                        .eq(CsChatSession::getUserId, userIdStr)
                        .eq(CsChatSession::getStatus, "CLOSED"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CsChatSession s : closedSessions) {
            // 检查是否已评价（每会话最多一条评价）
            Long count = satisfactionMapper.selectCount(
                    new LambdaQueryWrapper<CsSatisfaction>().eq(CsSatisfaction::getSessionId, s.getId()));
            if (count > 0) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("sessionId", s.getId());
            item.put("closeTime", s.getCloseTime() != null ? s.getCloseTime().toString() : "");
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> myRatings(Long agentId, int page, int size) {
        Page<CsSatisfaction> p = new Page<>(page, size);
        Page<CsSatisfaction> pageResult = satisfactionMapper.selectPage(p,
                new LambdaQueryWrapper<CsSatisfaction>()
                        .eq(CsSatisfaction::getAgentId, agentId)
                        .orderByDesc(CsSatisfaction::getCreateTime));

        return pageResult.getRecords().stream().map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("sessionId", s.getSessionId());
            item.put("rating", s.getRating());
            item.put("comment", s.getComment());
            item.put("createTime", s.getCreateTime() != null ? s.getCreateTime().toString() : "");
            return item;
        }).collect(Collectors.toList());
    }
}
