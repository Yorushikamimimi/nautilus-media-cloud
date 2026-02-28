package com.nautilus.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nautilus.common.exception.ServiceException;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import com.nautilus.dispatch.mapper.SysMediaTaskMapper;
import com.nautilus.dispatch.service.ISysMediaTaskService;
import com.nautilus.dispatch.domain.event.TaskUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 媒体任务服务实现类
 *
 * @author Nautilus Media Cloud
 */
@Slf4j
@Service
public class SysMediaTaskServiceImpl extends ServiceImpl<SysMediaTaskMapper, SysMediaTask>
        implements ISysMediaTaskService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 拉取待处理任务 - 带事务控制
     * 
     * 异常兜底策略:
     * 1. 无可用任务时返回 null,不抛出异常
     * 2. 记录 Yorushika 主题的警告日志
     * 3. 由 Controller 层统一封装为 204 响应
     *
     * @param workerNode 工作节点标识
     * @return 拉取到的任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMediaTask pullPendingTask(String workerNode) {
        if (workerNode == null || workerNode.trim().isEmpty()) {
            log.error("思想犯 - 工作节点标识不能为空");
            throw new ServiceException("思想犯 - 工作节点标识不能为空");
        }

        try {
            // 同一事务内: 先锁定一条 PENDING 任务,再更新为 RUNNING,避免 UPDATE RETURNING 在 JDBC/MyBatis
            // 下的兼容问题
            SysMediaTask task = baseMapper.selectOnePendingForUpdate();
            if (task == null) {
                log.warn("思想犯 - 节点 [{}] 未拉取到可用任务", workerNode);
                return null;
            }
            baseMapper.updateTaskToRunning(task.getTaskId(), workerNode);
            task.setStatus(SysMediaTask.TaskStatus.RUNNING);
            task.setWorkerNode(workerNode);
            task.setUpdatedAt(LocalDateTime.now());

            log.info("夜行 - 节点 [{}] 成功拉取任务 [taskId={}, taskName={}]",
                    workerNode, task.getTaskId(), task.getTaskName());

            // 发布 SSE 更新事件
            eventPublisher.publishEvent(new TaskUpdateEvent(this, task));

            return task;

        } catch (Exception e) {
            log.error("思想犯 - 任务拉取失败,节点: {}, 错误: {}", workerNode, e.getMessage(), e);
            throw new ServiceException("思想犯 - 任务拉取失败: " + e.getMessage());
        }
    }

    /**
     * 回报任务状态 - 带事务控制和异常处理,支持回填 metaInfo
     *
     * @param taskId   任务ID
     * @param status   新状态
     * @param errorLog 错误日志
     * @param metaInfo 元数据 (可选)
     * @param metaInfo 元数据 (可选)
     * @param progress 即时进度百分比 (可选)
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reportTaskStatus(Long taskId, String status, String errorLog, Map<String, Object> metaInfo,
            String progress) {
        if (taskId == null) {
            log.error("思想犯 - 任务ID不能为空");
            throw new ServiceException("思想犯 - 任务ID不能为空");
        }

        // 验证状态值合法性
        if (!SysMediaTask.TaskStatus.SUCCESS.equals(status)
                && !SysMediaTask.TaskStatus.FAILED.equals(status)
                && !SysMediaTask.TaskStatus.RUNNING.equals(status)) {
            log.error("思想犯 - 无效的任务状态: {}", status);
            throw new ServiceException("思想犯 - 任务状态只能为 RUNNING, SUCCESS 或 FAILED");
        }

        try {
            // 先查询任务是否存在
            SysMediaTask existingTask = baseMapper.selectById(taskId);
            if (existingTask == null) {
                log.error("又三郎 - 任务不存在, taskId: {}", taskId);
                throw new ServiceException("又三郎 - 任务不存在");
            }

            // 验证任务状态流转合法性
            if (!SysMediaTask.TaskStatus.RUNNING.equals(existingTask.getStatus())) {
                log.warn("盗作 - 任务状态异常,当前状态: {}, 期望状态: RUNNING, taskId: {}",
                        existingTask.getStatus(), taskId);
            }

            // 如果是 RUNNING 状态的进度更新，不落库，直接发送 SSE 广播后返回
            if (SysMediaTask.TaskStatus.RUNNING.equals(status)) {
                existingTask.setProgress(progress);
                eventPublisher.publishEvent(new TaskUpdateEvent(this, existingTask));
                return true;
            }

            int affectedRows = baseMapper.updateTaskStatus(taskId, status, errorLog, metaInfo);

            if (affectedRows > 0) {
                if (SysMediaTask.TaskStatus.SUCCESS.equals(status)) {
                    log.info("夜行 - 任务执行成功, taskId: {}, taskName: {}",
                            taskId, existingTask.getTaskName());
                } else {
                    log.warn("春泥棒 - 任务执行失败, taskId: {}, 错误: {}", taskId, errorLog);
                }

                // 更细实体的状态用于事件广播
                existingTask.setStatus(status);
                existingTask.setErrorLog(errorLog);
                existingTask.setUpdatedAt(LocalDateTime.now());
                // 发布 SSE 更新事件
                eventPublisher.publishEvent(new TaskUpdateEvent(this, existingTask));

                return true;
            } else {
                log.error("思想犯 - 状态更新失败, taskId: {}", taskId);
                return false;
            }

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("思想犯 - 状态回报异常, taskId: {}, 错误: {}", taskId, e.getMessage(), e);
            throw new ServiceException("思想犯 - 状态回报失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务列表（供前端控制台使用），按创建时间倒序
     */
    @Override
    public List<SysMediaTask> listTasks(String status) {
        LambdaQueryWrapper<SysMediaTask> wrapper = new LambdaQueryWrapper<SysMediaTask>()
                .orderByDesc(SysMediaTask::getCreatedAt);
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(SysMediaTask::getStatus, status.trim().toUpperCase());
        }
        return this.list(wrapper);
    }

    /**
     * 根据ID查询任务详情
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    @Override
    public SysMediaTask getTaskById(Long taskId) {
        if (taskId == null) {
            log.error("思想犯 - 任务ID不能为空");
            throw new ServiceException("思想犯 - 任务ID不能为空");
        }

        SysMediaTask task = baseMapper.selectById(taskId);
        if (task == null) {
            log.warn("又三郎 - 任务不存在, taskId: {}", taskId);
        }
        return task;
    }

    /**
     * 创建新任务并落库
     *
     * 业务约束:
     * 1. 强制设置初始状态为 PENDING
     * 2. 清空 taskId 确保走 insert 而非 update
     * 3. 设置创建/更新时间后调用 Mapper 真正写入 PostgreSQL
     *
     * @param task 任务实体 (前端或脚本传入)
     * @return 是否落库成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createTask(SysMediaTask task) {
        if (task == null) {
            log.error("思想犯 - 任务对象不能为空");
            throw new ServiceException("思想犯 - 任务对象不能为空");
        }

        // 1. 强制校验并设置初始状态为 PENDING (防止前端传入 RUNNING/SUCCESS 等)
        task.setStatus(SysMediaTask.TaskStatus.PENDING);
        // 2. 确保为新增: 清空主键,由数据库自增生成
        task.setTaskId(null);
        // 3. 新任务不绑定节点、无错误日志
        task.setWorkerNode(null);
        task.setErrorLog(null);
        // 4. 设置创建/更新时间 (不依赖 MetaObjectHandler,保证落库必有值)
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        try {
            // 5. 调用 MyBatis-Plus save -> 底层 insert 真正写入数据库
            boolean saved = this.save(task);
            if (saved) {
                log.info("夜行 - 任务创建成功已落库, taskId: {}, taskName: {}",
                        task.getTaskId(), task.getTaskName());

                // 发布 SSE 更新事件
                eventPublisher.publishEvent(new TaskUpdateEvent(this, task));

                return true;
            } else {
                log.error("春泥棒 - 任务创建失败，未能落库 (save 返回 false)");
                return false;
            }
        } catch (Exception e) {
            log.error("春泥棒 - 任务创建失败，未能落库: {}", e.getMessage(), e);
            throw new ServiceException("春泥棒 - 任务创建失败，未能落库");
        }
    }

    /**
     * 删除任务（物理删除）
     *
     * @param taskId 任务ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long taskId) {
        if (taskId == null) {
            log.error("思想犯 - 任务ID不能为空");
            throw new ServiceException("思想犯 - 任务ID不能为空");
        }
        try {
            boolean removed = this.removeById(taskId);
            if (removed) {
                log.info("夜行 - 任务删除成功, taskId: {}", taskId);
                return true;
            } else {
                log.warn("又三郎 - 任务删除失败或不存在, taskId: {}", taskId);
                return false;
            }
        } catch (Exception e) {
            log.error("思想犯 - 任务删除系统异常, taskId: {}, 错误: {}", taskId, e.getMessage(), e);
            throw new ServiceException("思想犯 - 任务删除失败: " + e.getMessage());
        }
    }
}
