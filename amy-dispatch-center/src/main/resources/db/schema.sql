-- ============================================================================
-- PostgreSQL 数据库建表脚本
-- 流媒体调度中台 - 媒体任务表
-- ============================================================================

-- 如果表已存在则先删除
DROP TABLE IF EXISTS sys_media_task;

-- 创建媒体任务表
CREATE TABLE sys_media_task (
    -- 任务ID - 主键自增
    task_id         BIGSERIAL PRIMARY KEY,
    
    -- 任务名称
    task_name       VARCHAR(200) NOT NULL,
    
    -- 目标URL
    target_url      TEXT NOT NULL,
    
    -- 任务状态: PENDING(待处理), RUNNING(执行中), SUCCESS(成功), FAILED(失败)
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- 工作节点标识 (拉取任务时绑定)
    worker_node     VARCHAR(100),
    
    -- 元数据信息 - JSONB 类型,支持复杂 JSON 结构存储
    -- 可存储如: 视频分辨率、编码格式、艺术家信息等
    meta_info       JSONB,
    
    -- 错误日志 (任务失败时记录)
    error_log       TEXT,
    
    -- 创建时间
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 更新时间
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 状态约束: 只允许四种合法状态
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'))
);

-- ============================================================================
-- 索引优化策略
-- ============================================================================

-- 1. 部分索引: 只索引 PENDING 状态的任务
-- 作用: 加速任务拉取查询,减少索引存储空间
-- 适用场景: 大部分任务完成后状态为 SUCCESS/FAILED,只有少量 PENDING 任务需要索引
CREATE INDEX idx_status_created ON sys_media_task(status, created_at) 
WHERE status = 'PENDING';

-- 2. 工作节点索引: 加速按节点查询任务历史
-- 作用: 支持运维人员快速查询某个节点处理的所有任务
CREATE INDEX idx_worker_node ON sys_media_task(worker_node) 
WHERE worker_node IS NOT NULL;

-- 3. JSONB GIN 索引: 支持 meta_info 字段的高级查询
-- 作用: 支持 JSON 字段的键值查询,如: WHERE meta_info @> '{"artist":"ヨルシカ"}'
CREATE INDEX idx_meta_info ON sys_media_task USING GIN(meta_info);

-- ============================================================================
-- 表注释
-- ============================================================================

COMMENT ON TABLE sys_media_task IS '流媒体任务调度表 - 支持高并发原子拉取';
COMMENT ON COLUMN sys_media_task.task_id IS '任务ID - 主键自增';
COMMENT ON COLUMN sys_media_task.task_name IS '任务名称';
COMMENT ON COLUMN sys_media_task.target_url IS '目标URL - 媒体资源地址';
COMMENT ON COLUMN sys_media_task.status IS '任务状态: PENDING/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN sys_media_task.worker_node IS '工作节点标识 - 拉取任务时绑定';
COMMENT ON COLUMN sys_media_task.meta_info IS '元数据 - JSONB 格式,存储任务相关配置';
COMMENT ON COLUMN sys_media_task.error_log IS '错误日志 - 记录任务失败原因';
COMMENT ON COLUMN sys_media_task.created_at IS '创建时间';
COMMENT ON COLUMN sys_media_task.updated_at IS '更新时间';

-- ============================================================================
-- 触发器: 自动更新 updated_at 字段
-- ============================================================================

-- 创建触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 绑定触发器到表
DROP TRIGGER IF EXISTS trigger_update_sys_media_task_updated_at ON sys_media_task;
CREATE TRIGGER trigger_update_sys_media_task_updated_at
    BEFORE UPDATE ON sys_media_task
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 表结构验证
-- ============================================================================

-- 验证表创建成功
SELECT 
    table_name, 
    column_name, 
    data_type, 
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_name = 'sys_media_task' 
ORDER BY ordinal_position;
