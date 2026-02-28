package com.nautilus.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 媒体任务 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper 获得基础 CRUD 能力
 *
 * @author Nautilus Media Cloud
 */
@Mapper
public interface SysMediaTaskMapper extends BaseMapper<SysMediaTask> {

    /**
     * 选取一条待处理任务并加行锁 (FOR UPDATE SKIP LOCKED)
     * 与 updateTaskToRunning 在同一事务内调用,实现原子拉取
     *
     * @return 一条 PENDING 任务,无则返回 null
     */
    SysMediaTask selectOnePendingForUpdate();

    /**
     * 将指定任务更新为 RUNNING 并绑定工作节点
     *
     * @param taskId 任务ID
     * @param workerNode 工作节点标识
     * @return 影响行数
     */
    int updateTaskToRunning(@Param("taskId") Long taskId, @Param("workerNode") String workerNode);

    /**
     * 更新任务状态
     * 用于工作节点回报任务执行结果,成功时可回填 metaInfo
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @param errorLog 错误日志(可选)
     * @param metaInfo 元数据(可选,JSONB)
     * @return 影响行数
     */
    int updateTaskStatus(@Param("taskId") Long taskId,
                        @Param("status") String status,
                        @Param("errorLog") String errorLog,
                        @Param("metaInfo") Map<String, Object> metaInfo);
}
