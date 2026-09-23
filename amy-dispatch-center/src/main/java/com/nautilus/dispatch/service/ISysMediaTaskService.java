package com.nautilus.dispatch.service;

import com.nautilus.dispatch.domain.entity.SysMediaTask;

import java.util.List;
import java.util.Map;

public interface ISysMediaTaskService {

    SysMediaTask pullPendingTask(String workerNode);

    boolean reportTaskStatus(Long taskId, String status, String errorLog, Map<String, Object> metaInfo,
            String progress, String workerNode, Long claimVersion);

    List<SysMediaTask> listTasks(String status);

    SysMediaTask getTaskById(Long taskId);

    boolean createTask(SysMediaTask task);

    boolean deleteTask(Long taskId);

    /**
     * Manually retry one task by resetting it to PENDING.
     */
    boolean retryTask(Long taskId);

    /**
     * Manually retry tasks in batch by IDs.
     *
     * @return number of tasks reset successfully
     */
    int retryTasks(List<Long> taskIds);

    /**
     * Reclaim timed-out RUNNING tasks and requeue/final-fail them.
     *
     * @return number of tasks reclaimed
     */
    int reclaimTimedOutRunningTasks();
}
