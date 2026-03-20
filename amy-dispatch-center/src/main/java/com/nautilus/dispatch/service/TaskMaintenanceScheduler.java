package com.nautilus.dispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMaintenanceScheduler {

    private final ISysMediaTaskService taskService;

    @Scheduled(fixedDelayString = "${task.reclaim-interval-ms:30000}")
    public void reclaimTimedOutRunningTasks() {
        try {
            int reclaimed = taskService.reclaimTimedOutRunningTasks();
            if (reclaimed > 0) {
                log.warn("Timed-out RUNNING tasks reclaimed: {}", reclaimed);
            }
        } catch (Exception e) {
            log.error("Failed to reclaim timed-out RUNNING tasks", e);
        }
    }
}
