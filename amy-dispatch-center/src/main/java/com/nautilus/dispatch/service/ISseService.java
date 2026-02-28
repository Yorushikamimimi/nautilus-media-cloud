package com.nautilus.dispatch.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 服务接口
 */
public interface ISseService {

    /**
     * 创建 SSE 连接
     * 
     * @param clientId 客户端区分ID (可选)
     * @return SseEmitter 实例
     */
    SseEmitter createConnect(String clientId);

    /**
     * 发送事件给所有连接的客户端
     *
     * @param payload 发生变化的任务实体等载荷数据
     */
    void broadcastEvent(Object payload);
}
