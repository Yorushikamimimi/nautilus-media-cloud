package com.nautilus.dispatch.controller;

import com.nautilus.common.core.domain.AjaxResult;
import com.nautilus.common.exception.ServiceException;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import com.nautilus.dispatch.service.ISseService;
import com.nautilus.dispatch.service.ISysMediaTaskService;
import com.nautilus.dispatch.service.WorkerRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class MediaTaskController {

    private final ISysMediaTaskService taskService;
    private final ISseService sseService;
    private final WorkerRegistry workerRegistry;
    private final JdbcTemplate jdbcTemplate;

    @Value("${nautilus.download.base-dir:./downloads}")
    private String downloadBaseDir;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamTasks() {
        String clientId = UUID.randomUUID().toString();
        return sseService.createConnect(clientId);
    }

    @GetMapping("/pending")
    public AjaxResult pullTask(@RequestParam(name = "workerNode") @NotBlank(message = "workerNode cannot be blank") String workerNode) {
        try {
            workerRegistry.heartbeat(workerNode);
            SysMediaTask task = taskService.pullPendingTask(workerNode);
            if (task == null) {
                return AjaxResult.noContent("No pending task");
            }
            return AjaxResult.success("Task pulled", task);
        } catch (ServiceException e) {
            log.error("Pull task business error: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Pull task system error", e);
            return AjaxResult.error("Pull task failed");
        }
    }

    @PutMapping("/{taskId}/status")
    public AjaxResult reportStatus(
            @PathVariable @NotNull(message = "taskId cannot be null") Long taskId,
            @RequestBody Map<String, Object> requestBody) {

        try {
            String status = requestBody.get("status") != null ? requestBody.get("status").toString() : null;
            String errorLog = requestBody.get("errorLog") != null ? requestBody.get("errorLog").toString() : null;
            String progress = requestBody.get("progress") != null ? requestBody.get("progress").toString() : null;
            String workerNode = requestBody.get("workerNode") != null ? requestBody.get("workerNode").toString() : null;
            Long claimVersion = requestBody.get("claimVersion") != null
                    ? Long.valueOf(requestBody.get("claimVersion").toString()) : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> metaInfo = requestBody.get("metaInfo") instanceof Map
                    ? (Map<String, Object>) requestBody.get("metaInfo")
                    : null;

            if (status == null || status.trim().isEmpty()) {
                return AjaxResult.error("status cannot be blank");
            }
            if (!SysMediaTask.TaskStatus.SUCCESS.equals(status)
                    && !SysMediaTask.TaskStatus.FAILED.equals(status)
                    && !SysMediaTask.TaskStatus.RUNNING.equals(status)) {
                return AjaxResult.error("status must be RUNNING, SUCCESS or FAILED");
            }

            boolean success = taskService.reportTaskStatus(
                    taskId, status, errorLog, metaInfo, progress, workerNode, claimVersion);
            if (!success) {
                return AjaxResult.error("Update task status failed");
            }
            return AjaxResult.success("Task status updated");
        } catch (ServiceException e) {
            log.error("Report status business error, taskId={}, msg={}", taskId, e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Report status system error, taskId={}", taskId, e);
            return AjaxResult.error("Report status failed");
        }
    }

    @GetMapping("/stats")
    public AjaxResult getStats() {
        // SQL 聚合替代全量拉取+内存 stream，避免数据量大时 OOM
        Map<String, Object> statusCounts = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                    COUNT(*) FILTER (WHERE status = 'RUNNING') AS running,
                    COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success,
                    COUNT(*) FILTER (WHERE status = 'FAILED') AS failed
                FROM sys_media_task
                """);

        long total = ((Number) statusCounts.getOrDefault("total", 0)).longValue();
        long pending = ((Number) statusCounts.getOrDefault("pending", 0)).longValue();
        long running = ((Number) statusCounts.getOrDefault("running", 0)).longValue();
        long success = ((Number) statusCounts.getOrDefault("success", 0)).longValue();
        long failed = ((Number) statusCounts.getOrDefault("failed", 0)).longValue();

        LocalDate today = LocalDate.now();
        Long todayTrafficBytes = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM((meta_info->>'fileSize')::bigint), 0)
                FROM sys_media_task
                WHERE status = 'SUCCESS'
                  AND updated_at::date = ?
                """, Long.class, today);
        if (todayTrafficBytes == null) {
            todayTrafficBytes = 0L;
        }

        int onlineWorkers = workerRegistry.getOnlineWorkerCount();

        Map<String, Object> stats = Map.of(
                "totalTasks", total,
                "pendingTasks", pending,
                "runningTasks", running,
                "successTasks", success,
                "failedTasks", failed,
                "todayTrafficBytes", todayTrafficBytes,
                "onlineWorkers", onlineWorkers);
        return AjaxResult.success(stats);
    }

    @GetMapping("/{taskId}")
    public AjaxResult getTaskDetail(@PathVariable @NotNull(message = "taskId cannot be null") Long taskId) {
        try {
            SysMediaTask task = taskService.getTaskById(taskId);
            if (task == null) {
                return AjaxResult.error("Task not found");
            }
            return AjaxResult.success("Task detail", task);
        } catch (Exception e) {
            log.error("Get task detail error, taskId={}", taskId, e);
            return AjaxResult.error("Get task detail failed");
        }
    }

    @PostMapping
    public AjaxResult createTask(@RequestBody @Validated SysMediaTask task) {
        try {
            if (task.getTaskName() == null || task.getTaskName().trim().isEmpty()) {
                return AjaxResult.error("taskName cannot be blank");
            }
            if (task.getTargetUrl() == null || task.getTargetUrl().trim().isEmpty()) {
                return AjaxResult.error("targetUrl cannot be blank");
            }

            boolean success = taskService.createTask(task);
            if (success) {
                return AjaxResult.success("Task created", task);
            }
            return AjaxResult.error("Create task failed");
        } catch (ServiceException e) {
            log.error("Create task business error: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Create task system error", e);
            return AjaxResult.error("Create task failed");
        }
    }

    @DeleteMapping("/{taskId}")
    public AjaxResult deleteTask(@PathVariable @NotNull(message = "taskId cannot be null") Long taskId) {
        try {
            boolean success = taskService.deleteTask(taskId);
            if (success) {
                return AjaxResult.success("Task deleted");
            }
            return AjaxResult.error("Delete task failed or task not found");
        } catch (ServiceException e) {
            log.error("Delete task business error: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Delete task system error", e);
            return AjaxResult.error("Delete task failed");
        }
    }

    @PostMapping("/{taskId}/retry")
    public AjaxResult retryTask(@PathVariable @NotNull(message = "taskId cannot be null") Long taskId) {
        try {
            boolean success = taskService.retryTask(taskId);
            if (success) {
                return AjaxResult.success("Task retried");
            }
            return AjaxResult.error("Retry task failed");
        } catch (ServiceException e) {
            log.error("Retry task business error, taskId={}, msg={}", taskId, e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Retry task system error, taskId={}", taskId, e);
            return AjaxResult.error("Retry task failed");
        }
    }

    @PostMapping("/retry/batch")
    public AjaxResult retryTasksBatch(@RequestBody Map<String, Object> requestBody) {
        try {
            Object rawTaskIds = requestBody.get("taskIds");
            if (!(rawTaskIds instanceof List<?> rawList) || rawList.isEmpty()) {
                return AjaxResult.error("taskIds cannot be empty");
            }

            List<Long> taskIds = new ArrayList<>();
            for (Object raw : rawList) {
                if (raw == null) {
                    continue;
                }
                try {
                    taskIds.add(Long.parseLong(raw.toString()));
                } catch (Exception ignored) {
                    // skip invalid id
                }
            }
            if (taskIds.isEmpty()) {
                return AjaxResult.error("No valid taskIds");
            }

            int retried = taskService.retryTasks(taskIds);
            int requested = taskIds.size();
            int skipped = Math.max(0, requested - retried);

            return AjaxResult.success("Batch retry completed")
                    .put("requested", requested)
                    .put("retried", retried)
                    .put("skipped", skipped);
        } catch (ServiceException e) {
            log.error("Batch retry business error: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Batch retry system error", e);
            return AjaxResult.error("Batch retry failed");
        }
    }

    @GetMapping("/list")
    public AjaxResult listTasks(@RequestParam(name = "status", required = false) String status) {
        try {
            List<SysMediaTask> list = taskService.listTasks(status);
            return AjaxResult.success("Task list", list);
        } catch (Exception e) {
            log.error("List tasks error", e);
            return AjaxResult.error("List tasks failed");
        }
    }

    @GetMapping("/health")
    public AjaxResult healthCheck() {
        boolean databaseUp = isDatabaseUp();
        int onlineWorkers = workerRegistry.getOnlineWorkerCount();

        long failedTasks = 0L;
        Long latestFailedTaskId = null;
        String latestFailedReason = null;

        try {
            List<SysMediaTask> failedList = taskService.listTasks(SysMediaTask.TaskStatus.FAILED);
            failedTasks = failedList.size();
            if (!failedList.isEmpty()) {
                SysMediaTask latestFailed = failedList.get(0);
                latestFailedTaskId = latestFailed.getTaskId();
                latestFailedReason = latestFailed.getErrorLog();
            }
        } catch (Exception e) {
            log.warn("Health check failed to query failed task summary: {}", e.getMessage());
        }

        String serviceStatus = databaseUp ? "RUNNING" : "DEGRADED";
        String msg = databaseUp ? "Service running" : "Service degraded (database down)";

        return AjaxResult.success(msg)
                .put("service", "Amy Dispatch Center")
                .put("theme", "Yorushika")
                .put("status", serviceStatus)
                .put("database", databaseUp ? "UP" : "DOWN")
                .put("onlineWorkers", onlineWorkers)
                .put("failedTasks", failedTasks)
                .put("latestFailedTaskId", latestFailedTaskId)
                .put("latestFailedReason", latestFailedReason)
                .put("serverTime", LocalDateTime.now().toString());
    }

    private boolean isDatabaseUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            log.warn("Database probe failed: {}", e.getMessage());
            return false;
        }
    }

    @GetMapping("/{taskId}/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            @PathVariable @NotNull(message = "taskId cannot be null") Long taskId) {
        try {
            SysMediaTask task = taskService.getTaskById(taskId);
            if (task == null || !SysMediaTask.TaskStatus.SUCCESS.equals(task.getStatus())) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            Map<String, Object> metaInfo = task.getMetaInfo();
            if (metaInfo == null || !metaInfo.containsKey("filePath")) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            String filePath = String.valueOf(metaInfo.get("filePath"));
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("File not found on disk: {}", filePath);
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            // 路径穿越防护：规范化后校验必须在允许的下载基目录内
            Path baseDir = Paths.get(downloadBaseDir).toAbsolutePath().normalize();
            Path resolved = file.toPath().toRealPath();
            if (!resolved.startsWith(baseDir)) {
                log.error("Path traversal attempt blocked: {} not under base dir {}", resolved, baseDir);
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);

            String contentType = "application/octet-stream";
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".mp3")) {
                contentType = "audio/mpeg";
            } else if (fileName.endsWith(".flac")) {
                contentType = "audio/flac";
            } else if (fileName.endsWith(".mp4")) {
                contentType = "video/mp4";
            } else if (fileName.endsWith(".webm")) {
                contentType = "video/webm";
            }

            String encodedFileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + encodedFileName)
                    .header(org.springframework.http.HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (Exception e) {
            log.error("Download stream failed", e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
