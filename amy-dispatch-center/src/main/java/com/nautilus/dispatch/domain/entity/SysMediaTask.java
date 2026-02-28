package com.nautilus.dispatch.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 媒体任务实体类
 * 对应数据库表: sys_media_task
 *
 * @author Nautilus Media Cloud
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "sys_media_task", autoResultMap = true)
public class SysMediaTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID - 主键自增
     */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /**
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 目标URL
     */
    @TableField("target_url")
    private String targetUrl;

    /**
     * 任务状态: PENDING, RUNNING, SUCCESS, FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 工作节点标识
     */
    @TableField("worker_node")
    private String workerNode;

    /**
     * 元数据信息 - JSONB类型映射
     * 使用 JacksonTypeHandler 自动处理 Map <-> JSONB 序列化
     */
    @TableField(value = "meta_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaInfo;

    /**
     * 错误日志
     */
    @TableField("error_log")
    private String errorLog;

    /**
     * 实时进度 (瞬态字段，不入库，用于 SSE 广播)
     */
    @TableField(exist = false)
    private String progress;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 任务状态枚举
     */
    public static class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
    }
}
