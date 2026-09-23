package com.nautilus.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysMediaTaskMapper extends BaseMapper<SysMediaTask> {

    SysMediaTask selectOnePendingForUpdate();

    List<SysMediaTask> selectTimedOutRunningForUpdate(@Param("timeoutSeconds") int timeoutSeconds);

    int updateTaskToRunning(@Param("taskId") Long taskId, @Param("workerNode") String workerNode);

    int updateTaskStatus(@Param("taskId") Long taskId,
            @Param("status") String status,
            @Param("errorLog") String errorLog,
            @Param("metaInfo") Map<String, Object> metaInfo,
            @Param("workerNode") String workerNode,
            @Param("claimVersion") Long claimVersion);

    int touchTaskHeartbeat(@Param("taskId") Long taskId, @Param("workerNode") String workerNode,
            @Param("claimVersion") Long claimVersion);

    int scheduleTaskRetry(@Param("taskId") Long taskId,
            @Param("retryCount") Integer retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("errorLog") String errorLog,
            @Param("workerNode") String workerNode,
            @Param("claimVersion") Long claimVersion);

    int markTaskFinalFailed(@Param("taskId") Long taskId,
            @Param("errorLog") String errorLog,
            @Param("workerNode") String workerNode,
            @Param("claimVersion") Long claimVersion);
}
