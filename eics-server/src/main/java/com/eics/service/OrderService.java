package com.eics.service;

import com.eics.entity.CsServiceOrder;

import java.util.Map;

/**
 * 客服工单服务
 */
public interface OrderService {

    /** 创建工单（Rasa Action 调用） */
    CsServiceOrder createOrder(Map<String, String> params);

    /** 查询工单详情 */
    CsServiceOrder getById(String orderId);

    /** 受理工单 */
    void assign(String orderId, Long agentId);

    /** 解决/关闭工单 */
    void resolve(String orderId);
}
