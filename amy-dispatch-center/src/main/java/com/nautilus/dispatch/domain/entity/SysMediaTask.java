package com.nautilus.dispatch.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * Media task entity mapping for table sys_media_task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@TableName(value = "sys_media_task", autoResultMap = true)
public class SysMediaTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    @TableField("task_name")
    private String taskName;

    @TableField("target_url")
    private String targetUrl;

    @TableField("status")
    private String status;

    @TableField("worker_node")
    private String workerNode;

    @TableField(value = "meta_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metaInfo;

    @TableField("error_log")
    private String errorLog;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField(exist = false)
    private String progress;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        private TaskStatus() {
        }
    }
}
