package com.nautilus.dispatch.controller;

import com.nautilus.common.core.domain.AjaxResult;
import com.nautilus.common.exception.ServiceException;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import com.nautilus.dispatch.service.ISysMediaTaskService;
import com.nautilus.dispatch.service.ISseService;
import com.nautilus.dispatch.service.WorkerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 媒体任务控制器
 * 提供任务拉取与状态回报的 REST API
 *
 * @author Nautilus Media Cloud
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class MediaTaskController {

    private final ISysMediaTaskService taskService;
    private final ISseService sseService;
    private final WorkerRegistry workerRegistry;

    /**
     * SSE 任务状态实时流
     * GET /api/v1/tasks/stream
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamTasks() {
        String clientId = UUID.randomUUID().toString();
        return sseService.createConnect(clientId);
    }

    /**
     * 拉取待处理任务
     * 
     * GET /api/v1/tasks/pending?workerNode=worker-node-01
     *
     * 响应码:
     * - 200: 成功拉取到任务
     * - 204: 当前无可用任务
     * - 500: 服务器内部错误
     *
     * @param workerNode 工作节点标识
     * @return AjaxResult 封装的任务信息
     */
    @GetMapping("/pending")
    public AjaxResult pullTask(@RequestParam(name = "workerNode") @NotBlank(message = "工作节点标识不能为空") String workerNode) {
        try {
            // 记录节点心跳
            workerRegistry.heartbeat(workerNode);

            SysMediaTask task = taskService.pullPendingTask(workerNode);

            if (task == null) {
                // 无可用任务,返回 204 警告状态
                return AjaxResult.noContent("思想犯 - 当前无待处理任务");
            }

            // 成功拉取任务
            return AjaxResult.success("夜行 - 任务拉取成功", task);

        } catch (ServiceException e) {
            log.error("思想犯 - 任务拉取业务异常: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("思想犯 - 任务拉取系统异常: {}", e.getMessage(), e);
            return AjaxResult.error("思想犯 - 系统异常,请稍后重试");
        }
    }

    /**
     * 回报任务状态
     *
     * PUT /api/v1/tasks/{taskId}/status
     *
     * 请求体示例:
     * {
     * "status": "SUCCESS",
     * "errorLog": "春泥棒 - 网络超时", // 可选,仅失败时需要
     * "metaInfo": { "title": "...", "duration": 123, "uploader": "...",
     * "thumbnail": "..." } // 可选,成功时 Worker 回填
     * }
     *
     * @param taskId      任务ID
     * @param requestBody 请求体,包含 status、errorLog、metaInfo(可选)
     * @return AjaxResult 封装的更新结果
     */
    @PutMapping("/{taskId}/status")
    public AjaxResult reportStatus(
            @PathVariable @NotNull(message = "任务ID不能为空") Long taskId,
            @RequestBody Map<String, Object> requestBody) {

        try {
            String status = requestBody.get("status") != null ? requestBody.get("status").toString() : null;
            String errorLog = requestBody.get("errorLog") != null ? requestBody.get("errorLog").toString() : null;
            String progress = requestBody.get("progress") != null ? requestBody.get("progress").toString() : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> metaInfo = requestBody.get("metaInfo") instanceof Map
                    ? (Map<String, Object>) requestBody.get("metaInfo")
                    : null;

            if (status == null || status.trim().isEmpty()) {
                return AjaxResult.error("思想犯 - 任务状态不能为空");
            }

            if (!SysMediaTask.TaskStatus.SUCCESS.equals(status)
                    && !SysMediaTask.TaskStatus.FAILED.equals(status)
                    && !SysMediaTask.TaskStatus.RUNNING.equals(status)) {
                return AjaxResult.error("思想犯 - 任务状态只能为 RUNNING, SUCCESS 或 FAILED");
            }

            boolean success = taskService.reportTaskStatus(taskId, status, errorLog, metaInfo, progress);

            if (success) {
                if (SysMediaTask.TaskStatus.SUCCESS.equals(status)) {
                    return AjaxResult.success("夜行 - 节点状态更新成功");
                } else if (SysMediaTask.TaskStatus.RUNNING.equals(status)) {
                    return AjaxResult.success("靴の花火 - 节点进度同步成功");
                } else {
                    return AjaxResult.success("春泥棒 - 任务失败状态已记录");
                }
            } else {
                return AjaxResult.error("思想犯 - 状态更新失败");
            }

        } catch (ServiceException e) {
            log.error("思想犯 - 状态回报业务异常,taskId: {}, 错误: {}", taskId, e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("思想犯 - 状态回报系统异常,taskId: {}, 错误: {}", taskId, e.getMessage(), e);
            return AjaxResult.error("思想犯 - 系统异常,请稍后重试");
        }
    }

    /**
     * 获取调度中心数据统计
     * GET /api/v1/tasks/stats
     */
    @GetMapping("/stats")
    public AjaxResult getStats() {
        List<SysMediaTask> list = taskService.listTasks(null);
        long total = list.size();
        long pending = list.stream().filter(t -> "PENDING".equals(t.getStatus())).count();
        long running = list.stream().filter(t -> "RUNNING".equals(t.getStatus())).count();
        long success = list.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
        long failed = list.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

        // 计算今日下行流量 (只算今天完成的成功任务且有 fileSize 的)
        LocalDate today = LocalDate.now();
        long todayTrafficBytes = list.stream()
                .filter(t -> "SUCCESS".equals(t.getStatus()))
                .filter(t -> t.getUpdatedAt() != null && t.getUpdatedAt().toLocalDate().isEqual(today))
                .mapToLong(t -> {
                    if (t.getMetaInfo() != null && t.getMetaInfo().get("fileSize") != null) {
                        try {
                            return Long.parseLong(t.getMetaInfo().get("fileSize").toString());
                        } catch (Exception e) {
                            return 0L;
                        }
                    }
                    return 0L;
                })
                .sum();

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

    /**
     * 查询任务详情
     * 
     * GET /api/v1/tasks/{taskId}
     *
     * @param taskId 任务ID
     * @return AjaxResult 封装的任务详情
     */
    @GetMapping("/{taskId}")
    public AjaxResult getTaskDetail(@PathVariable @NotNull(message = "任务ID不能为空") Long taskId) {
        try {
            SysMediaTask task = taskService.getTaskById(taskId);

            if (task == null) {
                return AjaxResult.error("又三郎 - 任务不存在");
            }

            return AjaxResult.success("夜行 - 查询成功", task);

        } catch (Exception e) {
            log.error("思想犯 - 任务查询异常,taskId: {}, 错误: {}", taskId, e.getMessage(), e);
            return AjaxResult.error("思想犯 - 查询失败");
        }
    }

    /**
     * 创建新任务 (真实落库)
     *
     * POST /api/v1/tasks
     *
     * 请求体示例:
     * {
     * "taskName": "又三郎 4K MV 抓取",
     * "targetUrl": "https://youtube.com/watch?v=xxx",
     * "metaInfo": {
     * "artist": "ヨルシカ",
     * "resolution": "4K"
     * }
     * }
     *
     * 业务规则: Service 层强制设置 status=PENDING 并调用 Mapper 写入 PostgreSQL
     *
     * @param task 任务实体 (前端或脚本传入)
     * @return AjaxResult 落库成功返回任务(含 taskId),失败返回春泥棒文案
     */
    @PostMapping
    public AjaxResult createTask(@RequestBody @Validated SysMediaTask task) {
        try {
            if (task.getTaskName() == null || task.getTaskName().trim().isEmpty()) {
                return AjaxResult.error("思想犯 - 任务名称不能为空");
            }
            if (task.getTargetUrl() == null || task.getTargetUrl().trim().isEmpty()) {
                return AjaxResult.error("思想犯 - 目标URL不能为空");
            }

            boolean success = taskService.createTask(task);

            if (success) {
                return AjaxResult.success("夜行 - 任务创建成功", task);
            } else {
                return AjaxResult.error("春泥棒 - 任务创建失败，未能落库");
            }
        } catch (ServiceException e) {
            log.error("思想犯 - 任务创建业务异常: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("思想犯 - 任务创建系统异常: {}", e.getMessage(), e);
            return AjaxResult.error("春泥棒 - 任务创建失败，未能落库");
        }
    }

    /**
     * 删除任务
     *
     * DELETE /api/v1/tasks/{taskId}
     *
     * @param taskId 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{taskId}")
    public AjaxResult deleteTask(@PathVariable @NotNull(message = "任务ID不能为空") Long taskId) {
        try {
            boolean success = taskService.deleteTask(taskId);
            if (success) {
                return AjaxResult.success("夜行 - 任务清理成功");
            } else {
                return AjaxResult.error("春泥棒 - 任务不存在或清理失败");
            }
        } catch (ServiceException e) {
            log.error("思想犯 - 任务删除业务异常: {}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("思想犯 - 任务删除系统异常: {}", e.getMessage(), e);
            return AjaxResult.error("思想犯 - 系统异常,请稍后重试");
        }
    }

    /**
     * 查询全部任务列表（供前端控制台展示）
     *
     * GET /api/v1/tasks/list?status=SUCCESS
     *
     * @param status 可选状态筛选 (PENDING/RUNNING/SUCCESS/FAILED)
     * @return 任务列表，按创建时间倒序
     */
    @GetMapping("/list")
    public AjaxResult listTasks(
            @RequestParam(name = "status", required = false) String status) {
        try {
            List<SysMediaTask> list = taskService.listTasks(status);
            return AjaxResult.success("夜行 - 任务列表查询成功", list);
        } catch (Exception e) {
            log.error("思想犯 - 任务列表查询异常: {}", e.getMessage(), e);
            return AjaxResult.error("思想犯 - 列表查询失败");
        }
    }

    /**
     * 健康检查接口 - Yorushika 主题
     * 
     * GET /api/v1/tasks/health
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public AjaxResult healthCheck() {
        return AjaxResult.success("夜行 - 调度中心运行正常")
                .put("service", "Amy Dispatch Center")
                .put("theme", "ヨルシカ (Yorushika)")
                .put("status", "RUNNING");
    }

    /**
     * 直链下载/在线流播接口
     * 
     * GET /api/v1/tasks/{taskId}/download
     *
     * @param taskId 任务ID
     * @return 文件流
     */
    @GetMapping("/{taskId}/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            @PathVariable @NotNull(message = "任务ID不能为空") Long taskId) {
        try {
            SysMediaTask task = taskService.getTaskById(taskId);
            if (task == null || !"SUCCESS".equals(task.getStatus())) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            // 从 metaInfo 获取 filePath
            java.util.Map<String, Object> metaInfo = task.getMetaInfo();
            if (metaInfo == null || !metaInfo.containsKey("filePath")) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            String filePath = (String) metaInfo.get("filePath");
            java.io.File file = new java.io.File(filePath);

            if (!file.exists()) {
                log.error("春泥棒 - 文件在磁盘上找不到: {}", filePath);
                return org.springframework.http.ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);

            // 推断 MIME 类型
            String contentType = "application/octet-stream";
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".mp3"))
                contentType = "audio/mpeg";
            else if (fileName.endsWith(".flac"))
                contentType = "audio/flac";
            else if (fileName.endsWith(".mp4"))
                contentType = "video/mp4";
            else if (fileName.endsWith(".webm"))
                contentType = "video/webm";

            // 附件名为原始文件名，处理中文编码
            String encodedFileName = java.net.URLEncoder.encode(file.getName(), java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + encodedFileName)
                    .header(org.springframework.http.HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (Exception e) {
            log.error("思想犯 - 文件流式传输异常: {}", e.getMessage(), e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
