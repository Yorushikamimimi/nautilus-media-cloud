package com.nautilus.dispatch.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作节点注册表，用于监控在线状态
 */
@Component
public class WorkerRegistry {

    private final ConcurrentHashMap<String, Long> workerHeartbeats = new ConcurrentHashMap<>();

    /**
     * 记录心跳
     * 
     * @param workerId 节点ID
     */
    public void heartbeat(String workerId) {
        if (workerId != null && !workerId.isBlank()) {
            workerHeartbeats.put(workerId, System.currentTimeMillis());
        }
    }

    /**
     * 获取当前在线的节点数量
     * (10秒内有心跳视作在线)
     */
    public int getOnlineWorkerCount() {
        long now = System.currentTimeMillis();
        return (int) workerHeartbeats.values().stream()
                .filter(lastSeen -> (now - lastSeen) < 10000)
                .count();
    }
}
