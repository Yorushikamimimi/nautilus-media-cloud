-- ============================================================================
-- PostgreSQL schema for Nautilus Media Cloud task table
-- ============================================================================

DROP TABLE IF EXISTS sys_media_task;

CREATE TABLE sys_media_task (
    task_id         BIGSERIAL PRIMARY KEY,
    task_name       VARCHAR(200) NOT NULL,
    target_url      TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    worker_node     VARCHAR(100),
    claim_version   BIGINT NOT NULL DEFAULT 0,
    meta_info       JSONB,
    error_log       TEXT,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    max_retry       INTEGER NOT NULL DEFAULT 3,
    next_retry_at   TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'))
);

-- Pending task dispatch index.
CREATE INDEX idx_status_created ON sys_media_task(status, created_at)
WHERE status = 'PENDING';

-- Pending retry schedule index.
CREATE INDEX idx_pending_next_retry ON sys_media_task(next_retry_at)
WHERE status = 'PENDING';

-- Running timeout recovery index.
CREATE INDEX idx_running_updated_at ON sys_media_task(updated_at)
WHERE status = 'RUNNING';

-- Worker lookup index.
CREATE INDEX idx_worker_node ON sys_media_task(worker_node)
WHERE worker_node IS NOT NULL;

-- JSONB query index.
CREATE INDEX idx_meta_info ON sys_media_task USING GIN(meta_info);

COMMENT ON TABLE sys_media_task IS 'Media task dispatch table';
COMMENT ON COLUMN sys_media_task.retry_count IS 'Current retry count';
COMMENT ON COLUMN sys_media_task.max_retry IS 'Maximum retries allowed';
COMMENT ON COLUMN sys_media_task.next_retry_at IS 'Earliest allowed retry time';
COMMENT ON COLUMN sys_media_task.claim_version IS 'Monotonic fencing token incremented for each worker claim';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_sys_media_task_updated_at ON sys_media_task;
CREATE TRIGGER trigger_update_sys_media_task_updated_at
    BEFORE UPDATE ON sys_media_task
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
