package com.nautilus.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nautilus.common.exception.ServiceException;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import com.nautilus.dispatch.domain.event.TaskUpdateEvent;
import com.nautilus.dispatch.mapper.SysMediaTaskMapper;
import com.nautilus.dispatch.service.ISysMediaTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMediaTaskServiceImpl extends ServiceImpl<SysMediaTaskMapper, SysMediaTask>
        implements ISysMediaTaskService {

    private static final int MAX_ERROR_LOG_LENGTH = 4000;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${task.retry.default-max:3}")
    private int defaultMaxRetry;

    @Value("${task.retry.base-delay-seconds:30}")
    private long retryBaseDelaySeconds;

    @Value("${task.retry.max-delay-seconds:1800}")
    private long retryMaxDelaySeconds;

    @Value("${task.running-timeout-seconds:900}")
    private int runningTimeoutSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMediaTask pullPendingTask(String workerNode) {
        if (workerNode == null || workerNode.trim().isEmpty()) {
            throw new ServiceException("workerNode cannot be blank");
        }

        try {
            SysMediaTask task = baseMapper.selectOnePendingForUpdate();
            if (task == null) {
                return null;
            }

            int affectedRows = baseMapper.updateTaskToRunning(task.getTaskId(), workerNode);
            if (affectedRows <= 0) {
                return null;
            }
            task.setStatus(SysMediaTask.TaskStatus.RUNNING);
            task.setWorkerNode(workerNode);
            task.setClaimVersion(safeNonNegativeLong(task.getClaimVersion(), 0L) + 1L);
            task.setNextRetryAt(null);
            task.setUpdatedAt(LocalDateTime.now());

            eventPublisher.publishEvent(new TaskUpdateEvent(this, task));
            log.info("Task pulled: taskId={}, workerNode={}", task.getTaskId(), workerNode);
            return task;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to pull pending task, workerNode={}", workerNode, e);
            throw new ServiceException("Failed to pull pending task: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reportTaskStatus(Long taskId, String status, String errorLog, Map<String, Object> metaInfo,
            String progress, String workerNode, Long claimVersion) {
        if (taskId == null) {
            throw new ServiceException("taskId cannot be null");
        }

        if (!SysMediaTask.TaskStatus.SUCCESS.equals(status)
                && !SysMediaTask.TaskStatus.FAILED.equals(status)
                && !SysMediaTask.TaskStatus.RUNNING.equals(status)) {
            throw new ServiceException("status must be RUNNING, SUCCESS or FAILED");
        }

        if (workerNode == null || workerNode.trim().isEmpty() || claimVersion == null || claimVersion <= 0) {
            return false;
        }

        try {
            SysMediaTask existingTask = baseMapper.selectById(taskId);
            if (existingTask == null) {
                throw new ServiceException("task not found: " + taskId);
            }

            if (SysMediaTask.TaskStatus.RUNNING.equals(status)) {
                int affectedRows = baseMapper.touchTaskHeartbeat(taskId, workerNode, claimVersion);
                if (affectedRows <= 0) {
                    return false;
                }
                existingTask.setProgress(progress);
                existingTask.setUpdatedAt(LocalDateTime.now());
                eventPublisher.publishEvent(new TaskUpdateEvent(this, existingTask));
                return true;
            }

            if (SysMediaTask.TaskStatus.SUCCESS.equals(status)) {
                int affectedRows = baseMapper.updateTaskStatus(
                        taskId, status, null, metaInfo, workerNode, claimVersion);
                if (affectedRows <= 0) {
                    return false;
                }

                existingTask.setStatus(SysMediaTask.TaskStatus.SUCCESS);
                existingTask.setErrorLog(null);
                existingTask.setNextRetryAt(null);
                existingTask.setUpdatedAt(LocalDateTime.now());
                if (metaInfo != null) {
                    existingTask.setMetaInfo(metaInfo);
                }
                eventPublisher.publishEvent(new TaskUpdateEvent(this, existingTask));
                log.info("Task completed: taskId={}", taskId);
                return true;
            }

            if (!matchesClaim(existingTask, workerNode, claimVersion)) {
                return false;
            }
            return handleFailureWithRetry(existingTask, errorLog, "worker");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to report status, taskId={}, status={}", taskId, status, e);
            throw new ServiceException("Failed to report task status: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reclaimTimedOutRunningTasks() {
        List<SysMediaTask> timedOutTasks = baseMapper.selectTimedOutRunningForUpdate(runningTimeoutSeconds);
        if (timedOutTasks.isEmpty()) {
            return 0;
        }

        int reclaimed = 0;
        for (SysMediaTask task : timedOutTasks) {
            String timeoutReason = "RUNNING timeout exceeded: " + runningTimeoutSeconds + "s";
            boolean updated = handleFailureWithRetry(task, timeoutReason, "timeout-reclaimer");
            if (updated) {
                reclaimed++;
            }
        }

        if (reclaimed > 0) {
            log.warn("Reclaimed timed-out running tasks: {}", reclaimed);
        }
        return reclaimed;
    }

    @Override
    public List<SysMediaTask> listTasks(String status) {
        LambdaQueryWrapper<SysMediaTask> wrapper = new LambdaQueryWrapper<SysMediaTask>()
                .orderByDesc(SysMediaTask::getCreatedAt);
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(SysMediaTask::getStatus, status.trim().toUpperCase());
        }
        return this.list(wrapper);
    }

    @Override
    public SysMediaTask getTaskById(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("taskId cannot be null");
        }

        return baseMapper.selectById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createTask(SysMediaTask task) {
        if (task == null) {
            throw new ServiceException("task cannot be null");
        }

        task.setStatus(SysMediaTask.TaskStatus.PENDING);
        task.setTaskId(null);
        task.setWorkerNode(null);
        task.setClaimVersion(0L);
        task.setErrorLog(null);
        task.setRetryCount(0);
        task.setMaxRetry(resolveMaxRetry(task.getMaxRetry()));
        task.setNextRetryAt(null);

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        try {
            boolean saved = this.save(task);
            if (!saved) {
                return false;
            }

            eventPublisher.publishEvent(new TaskUpdateEvent(this, task));
            return true;
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            String rootMsg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            String lowerMsg = rootMsg.toLowerCase();

            if (lowerMsg.contains("connection refused")
                    || lowerMsg.contains("the connection attempt failed")
                    || lowerMsg.contains("failed to obtain jdbc connection")) {
                throw new ServiceException("Database connection failed, please start PostgreSQL first");
            }

            if (lowerMsg.contains("relation") && lowerMsg.contains("sys_media_task") && lowerMsg.contains("does not exist")) {
                throw new ServiceException("Table sys_media_task does not exist, please run db/schema.sql");
            }

            throw new ServiceException("Failed to create task: " + rootMsg);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean retryTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("taskId cannot be null");
        }
        SysMediaTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("task not found: " + taskId);
        }
        return resetTaskForManualRetry(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int retryTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long taskId : taskIds) {
            if (taskId != null) {
                uniqueIds.add(taskId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return 0;
        }

        int retried = 0;
        for (Long taskId : uniqueIds) {
            SysMediaTask task = baseMapper.selectById(taskId);
            if (task == null) {
                continue;
            }
            if (resetTaskForManualRetry(task)) {
                retried++;
            }
        }
        return retried;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("taskId cannot be null");
        }
        try {
            return this.removeById(taskId);
        } catch (Exception e) {
            throw new ServiceException("Failed to delete task: " + e.getMessage());
        }
    }

    private boolean resetTaskForManualRetry(SysMediaTask task) {
        if (task == null || task.getTaskId() == null) {
            return false;
        }

        if (SysMediaTask.TaskStatus.RUNNING.equals(task.getStatus())) {
            throw new ServiceException("task is RUNNING and cannot be retried manually: " + task.getTaskId());
        }

        String oldError = task.getErrorLog();
        String retryLog = null;
        if (oldError != null && !oldError.trim().isEmpty()) {
            retryLog = truncateErrorLog("[manual-retry] " + oldError.trim());
        }

        task.setStatus(SysMediaTask.TaskStatus.PENDING);
        task.setWorkerNode(null);
        task.setRetryCount(0);
        task.setMaxRetry(resolveMaxRetry(task.getMaxRetry()));
        task.setNextRetryAt(null);
        task.setErrorLog(retryLog);
        task.setUpdatedAt(LocalDateTime.now());

        boolean updated = this.updateById(task);
        if (updated) {
            eventPublisher.publishEvent(new TaskUpdateEvent(this, task));
        }
        return updated;
    }

    private boolean handleFailureWithRetry(SysMediaTask task, String errorLog, String source) {
        int retryCount = safeNonNegative(task.getRetryCount(), 0);
        int maxRetry = resolveMaxRetry(task.getMaxRetry());
        String normalizedReason = normalizeErrorLog(errorLog);

        if (retryCount < maxRetry) {
            int nextRetryCount = retryCount + 1;
            long backoffSeconds = computeBackoffSeconds(nextRetryCount);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds);

            String retryLog = truncateErrorLog(String.format(
                    "[%s] %s | retry %d/%d in %ds at %s",
                    source,
                    normalizedReason,
                    nextRetryCount,
                    maxRetry,
                    backoffSeconds,
                    nextRetryAt));

            int affectedRows = baseMapper.scheduleTaskRetry(task.getTaskId(), nextRetryCount, nextRetryAt,
                    retryLog, task.getWorkerNode(), task.getClaimVersion());
            if (affectedRows <= 0) {
                return false;
            }

            task.setStatus(SysMediaTask.TaskStatus.PENDING);
            task.setWorkerNode(null);
            task.setRetryCount(nextRetryCount);
            task.setMaxRetry(maxRetry);
            task.setNextRetryAt(nextRetryAt);
            task.setErrorLog(retryLog);
            task.setUpdatedAt(LocalDateTime.now());
            eventPublisher.publishEvent(new TaskUpdateEvent(this, task));

            log.warn("Task requeued: taskId={}, retry={}/{}, nextRetryAt={}",
                    task.getTaskId(), nextRetryCount, maxRetry, nextRetryAt);
            return true;
        }

        String finalLog = truncateErrorLog(String.format(
                "[%s] %s | max retry reached (%d/%d)",
                source,
                normalizedReason,
                retryCount,
                maxRetry));

        int affectedRows = baseMapper.markTaskFinalFailed(
                task.getTaskId(), finalLog, task.getWorkerNode(), task.getClaimVersion());
        if (affectedRows <= 0) {
            return false;
        }

        task.setStatus(SysMediaTask.TaskStatus.FAILED);
        task.setWorkerNode(null);
        task.setMaxRetry(maxRetry);
        task.setNextRetryAt(null);
        task.setErrorLog(finalLog);
        task.setUpdatedAt(LocalDateTime.now());
        eventPublisher.publishEvent(new TaskUpdateEvent(this, task));

        log.error("Task failed permanently: taskId={}, retry={}/{}, reason={}",
                task.getTaskId(), retryCount, maxRetry, normalizedReason);
        return true;
    }

    private int resolveMaxRetry(Integer configuredMaxRetry) {
        int value = safeNonNegative(configuredMaxRetry, defaultMaxRetry);
        return value <= 0 ? Math.max(defaultMaxRetry, 1) : value;
    }

    private int safeNonNegative(Integer value, int defaultValue) {
        if (value == null || value < 0) {
            return defaultValue;
        }
        return value;
    }

    private long safeNonNegativeLong(Long value, long defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private boolean matchesClaim(SysMediaTask task, String workerNode, Long claimVersion) {
        return task != null
                && SysMediaTask.TaskStatus.RUNNING.equals(task.getStatus())
                && workerNode.equals(task.getWorkerNode())
                && claimVersion.equals(task.getClaimVersion());
    }

    private long computeBackoffSeconds(int retryAttempt) {
        int safeAttempt = Math.max(retryAttempt, 1);
        long factor = 1L << Math.min(safeAttempt - 1, 20);
        long raw = retryBaseDelaySeconds * factor;
        return Math.min(raw, retryMaxDelaySeconds);
    }

    private String normalizeErrorLog(String errorLog) {
        if (errorLog == null || errorLog.trim().isEmpty()) {
            return "unknown worker error";
        }
        return errorLog.trim();
    }

    private String truncateErrorLog(String errorLog) {
        if (errorLog == null) {
            return null;
        }
        if (errorLog.length() <= MAX_ERROR_LOG_LENGTH) {
            return errorLog;
        }
        return errorLog.substring(0, MAX_ERROR_LOG_LENGTH) + "...";
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
