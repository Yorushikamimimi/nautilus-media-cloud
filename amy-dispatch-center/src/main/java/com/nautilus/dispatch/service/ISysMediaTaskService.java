package com.nautilus.dispatch.service;

import com.nautilus.dispatch.domain.entity.SysMediaTask;

import java.util.List;
import java.util.Map;

/**
 * 媒体任务服务接口
 *
 * @author Nautilus Media Cloud
 */
public interface ISysMediaTaskService {

    /**
     * 拉取待处理任务
     * 使用原子性 SQL 防止并发争抢
     * 
     * 业务规则:
     * 1. 按 created_at 升序拉取最早的 PENDING 任务
     * 2. 原子性更新为 RUNNING 状态并绑定工作节点
     * 3. 使用 FOR UPDATE SKIP LOCKED 避免并发冲突
     *
     * @param workerNode 工作节点标识
     * @return 拉取到的任务,无可用任务时返回 null
     */
    SysMediaTask pullPendingTask(String workerNode);

    /**
     * 回报任务状态
     * 工作节点完成任务后调用此接口更新状态
     *
     * 状态流转:
     * - RUNNING -> SUCCESS: 任务成功完成,可携带 metaInfo
     * - RUNNING -> FAILED: 任务执行失败,需记录错误日志
     *
     * @param taskId   任务ID
     * @param status   新状态 (SUCCESS 或 FAILED)
     * @param errorLog 错误日志 (可选,仅 FAILED 状态时需要)
     * @param metaInfo 元数据 (可选,SUCCESS 时由 Worker 回填
     *                 title/duration/uploader/thumbnail)
     * @param progress 即时进度百分比 (可选,仅 RUNNING 状态有效)
     * @return 是否更新成功
     */
    boolean reportTaskStatus(Long taskId, String status, String errorLog, Map<String, Object> metaInfo,
            String progress);

    /**
     * 查询任务列表（供前端控制台使用）
     *
     * @param status 可选状态筛选，为 null 时查全部
     * @return 按创建时间倒序的任务列表
     */
    List<SysMediaTask> listTasks(String status);

    /**
     * 根据ID查询任务详情
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    SysMediaTask getTaskById(Long taskId);

    /**
     * 创建新任务
     *
     * @param task 任务实体
     * @return 是否创建成功
     */
    boolean createTask(SysMediaTask task);

    /**
     * 删除任务（物理删除）
     *
     * @param taskId 任务ID
     * @return 是否删除成功
     */
    boolean deleteTask(Long taskId);
}
