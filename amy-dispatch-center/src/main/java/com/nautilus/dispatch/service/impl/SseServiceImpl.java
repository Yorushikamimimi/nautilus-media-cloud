package com.nautilus.dispatch.service.impl;

import com.nautilus.dispatch.domain.event.TaskUpdateEvent;
import com.nautilus.dispatch.service.ISseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 服务实现类
 *
 * @author Nautilus Media Cloud
 */
@Slf4j
@Service
public class SseServiceImpl implements ISseService {

    // 保存所有的 SseEmitter，Key 为自定义区分标识（如时间戳/UUID）
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter createConnect(String clientId) {
        // 设置超时时间，这里设为 0 表示永不超时 (实际受 Nginx 等反代配置限制)
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(clientId, emitter);

        log.info("夜行 - 新的 SSE 客户端连接接入: {}", clientId);

        Runnable onCompletionAndTimeout = () -> {
            log.info("思想犯 - SSE 客户端连接断开/超时: {}", clientId);
            emitters.remove(clientId);
        };

        emitter.onCompletion(onCompletionAndTimeout);
        emitter.onTimeout(onCompletionAndTimeout);
        emitter.onError(e -> {
            log.error("春泥棒 - SSE 客户端连接异常: {}，错误: {}", clientId, e.getMessage());
            emitters.remove(clientId);
        });

        return emitter;
    }

    @Override
    public void broadcastEvent(Object payload) {
        if (emitters.isEmpty()) {
            return;
        }

        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("task-update")
                        .data(payload));
            } catch (IOException e) {
                log.warn("又三郎 - SSE 推送失败，移除失效连接: {}", clientId);
                emitters.remove(clientId);
            }
        });
    }

    /**
     * 监听 Spring 内部的 TaskUpdateEvent
     *
     * @param event 任务更新事件
     */
    @EventListener
    public void handleTaskUpdateEvent(TaskUpdateEvent event) {
        if (event.getSysMediaTask() != null) {
            broadcastEvent(event.getSysMediaTask());
        }
    }
}
