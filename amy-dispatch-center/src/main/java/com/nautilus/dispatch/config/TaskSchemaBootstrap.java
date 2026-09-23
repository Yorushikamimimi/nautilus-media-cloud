package com.nautilus.dispatch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSchemaBootstrap {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTaskReliabilityColumns() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE sys_media_task
                    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE sys_media_task
                    ADD COLUMN IF NOT EXISTS max_retry INTEGER NOT NULL DEFAULT 3
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE sys_media_task
                    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP NULL
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE sys_media_task
                    ADD COLUMN IF NOT EXISTS claim_version BIGINT NOT NULL DEFAULT 0
                    """);
            jdbcTemplate.execute("""
                    UPDATE sys_media_task
                    SET claim_version = 0
                    WHERE claim_version IS NULL
                    """);

            jdbcTemplate.execute("""
                    UPDATE sys_media_task
                    SET retry_count = 0
                    WHERE retry_count IS NULL
                    """);
            jdbcTemplate.execute("""
                    UPDATE sys_media_task
                    SET max_retry = 3
                    WHERE max_retry IS NULL OR max_retry <= 0
                    """);

            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sys_media_task_pending_next_retry
                    ON sys_media_task(next_retry_at)
                    WHERE status = 'PENDING'
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sys_media_task_running_updated_at
                    ON sys_media_task(updated_at)
                    WHERE status = 'RUNNING'
                    """);

            log.info("Task schema bootstrap checked: retry fields and claim_version");
        } catch (Exception e) {
            log.warn("Task schema bootstrap skipped: {}", e.getMessage());
        }
    }
}
