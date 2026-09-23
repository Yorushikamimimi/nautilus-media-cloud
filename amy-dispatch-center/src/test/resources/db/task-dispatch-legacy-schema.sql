CREATE TABLE sys_media_task (
    task_id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    target_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    worker_node VARCHAR(100),
    meta_info JSONB,
    error_log TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'))
);
