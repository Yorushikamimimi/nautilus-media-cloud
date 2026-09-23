package com.nautilus.dispatch.service;

import com.nautilus.dispatch.DispatchApplication;
import com.nautilus.dispatch.domain.entity.SysMediaTask;
import com.nautilus.dispatch.mapper.SysMediaTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = DispatchApplication.class,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/task-dispatch-legacy-schema.sql",
                "task.retry.default-max=3",
                "task.retry.base-delay-seconds=60",
                "task.retry.max-delay-seconds=60",
                "task.running-timeout-seconds=2",
                "task.reclaim-interval-ms=3600000",
                "logging.file.name=target/test-logs/dispatch-test.log",
                "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        })
class SysMediaTaskPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("dispatch_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ISysMediaTaskService taskService;

    @Autowired
    private SysMediaTaskMapper taskMapper;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearTasks() {
        jdbcTemplate.execute("TRUNCATE TABLE sys_media_task RESTART IDENTITY");
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void concurrentWorkersClaimEachTaskOnlyOnceUsingSkipLocked() throws Exception {
        int count = 24;
        for (int i = 0; i < count; i++) {
            insertTask("concurrent-" + i, 3);
        }

        ExecutorService workers = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SysMediaTask>> claims = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String workerNode = "worker-" + i;
                claims.add(workers.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return taskService.pullPendingTask(workerNode);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<SysMediaTask> claimed = new ArrayList<>();
            for (Future<SysMediaTask> result : claims) {
                claimed.add(result.get(20, TimeUnit.SECONDS));
            }

            assertThat(claimed).hasSize(count).doesNotContainNull();
            Set<Long> uniqueIds = new HashSet<>();
            claimed.forEach(task -> uniqueIds.add(task.getTaskId()));
            assertThat(uniqueIds).hasSize(count);
            assertThat(claimed).allSatisfy(task -> assertThat(task.getClaimVersion()).isEqualTo(1L));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_media_task WHERE status = 'RUNNING'", Integer.class))
                    .isEqualTo(count);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void failedTaskHonorsBackoffAndCanBeClaimedAgainWithANewVersion() {
        long taskId = insertTask("retry-backoff", 2);
        SysMediaTask firstClaim = taskService.pullPendingTask("worker-first");

        assertThat(firstClaim).isNotNull();
        assertThat(firstClaim.getTaskId()).isEqualTo(taskId);
        assertThat(taskService.reportTaskStatus(taskId, SysMediaTask.TaskStatus.FAILED,
                "transient failure", null, null, "worker-first", firstClaim.getClaimVersion())).isTrue();

        Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM sys_media_task WHERE task_id = ?", Integer.class, taskId);
        LocalDateTime nextRetryAt = jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM sys_media_task WHERE task_id = ?", LocalDateTime.class, taskId);
        assertThat(retryCount).isEqualTo(1);
        assertThat(nextRetryAt).isAfter(LocalDateTime.now());
        assertThat(taskService.pullPendingTask("worker-too-early")).isNull();

        jdbcTemplate.update("UPDATE sys_media_task SET next_retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                "WHERE task_id = ?", taskId);
        SysMediaTask secondClaim = taskService.pullPendingTask("worker-second");
        assertThat(secondClaim).isNotNull();
        assertThat(secondClaim.getTaskId()).isEqualTo(taskId);
        assertThat(secondClaim.getClaimVersion()).isEqualTo(firstClaim.getClaimVersion() + 1L);
    }

    @Test
    void staleClaimCannotHeartbeatSucceedOrFailAfterTimeoutAndReclaim() {
        long taskId = insertTask("stale-claim", 1);
        SysMediaTask oldClaim = taskService.pullPendingTask("worker-old");
        assertThat(oldClaim).isNotNull();

        jdbcTemplate.update("UPDATE sys_media_task SET updated_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' " +
                "WHERE task_id = ?", taskId);
        assertThat(taskService.reclaimTimedOutRunningTasks()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT retry_count FROM sys_media_task WHERE task_id = ?", Integer.class, taskId)).isEqualTo(1);

        jdbcTemplate.update("UPDATE sys_media_task SET next_retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                "WHERE task_id = ?", taskId);
        SysMediaTask newClaim = taskService.pullPendingTask("worker-old");
        assertThat(newClaim).isNotNull();
        assertThat(newClaim.getClaimVersion()).isEqualTo(oldClaim.getClaimVersion() + 1L);

        assertThat(taskService.reportTaskStatus(taskId, SysMediaTask.TaskStatus.RUNNING,
                null, null, "99%", "worker-old", oldClaim.getClaimVersion())).isFalse();
        assertThat(taskService.reportTaskStatus(taskId, SysMediaTask.TaskStatus.SUCCESS,
                null, null, null, "worker-old", oldClaim.getClaimVersion())).isFalse();
        assertThat(taskService.reportTaskStatus(taskId, SysMediaTask.TaskStatus.FAILED,
                "late failure", null, null, "worker-old", oldClaim.getClaimVersion())).isFalse();

        assertThat(taskMapper.scheduleTaskRetry(taskId, 2, LocalDateTime.now().plusMinutes(1),
                "stale mapped retry", "worker-old", oldClaim.getClaimVersion())).isZero();
        assertThat(taskMapper.markTaskFinalFailed(taskId, "stale mapped final failure",
                "worker-old", oldClaim.getClaimVersion())).isZero();

        SysMediaTask current = taskService.getTaskById(taskId);
        assertThat(current.getStatus()).isEqualTo(SysMediaTask.TaskStatus.RUNNING);
        assertThat(current.getWorkerNode()).isEqualTo("worker-old");
        assertThat(current.getClaimVersion()).isEqualTo(newClaim.getClaimVersion());
        assertThat(current.getRetryCount()).isEqualTo(1);

        assertThat(taskService.reportTaskStatus(taskId, SysMediaTask.TaskStatus.FAILED,
                "current failure", null, null, "worker-old", newClaim.getClaimVersion())).isTrue();
        assertThat(taskService.getTaskById(taskId).getStatus()).isEqualTo(SysMediaTask.TaskStatus.FAILED);
    }

    @Test
    void timeoutReclaimerRequeuesTaskAndPreservesFiniteRetryLimit() {
        long taskId = insertTask("timeout-retry-limit", 1);
        SysMediaTask claim = taskService.pullPendingTask("worker-timeout");
        assertThat(claim).isNotNull();
        jdbcTemplate.update("UPDATE sys_media_task SET updated_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' " +
                "WHERE task_id = ?", taskId);

        assertThat(taskService.reclaimTimedOutRunningTasks()).isEqualTo(1);
        SysMediaTask requeued = taskService.getTaskById(taskId);
        assertThat(requeued.getStatus()).isEqualTo(SysMediaTask.TaskStatus.PENDING);
        assertThat(requeued.getRetryCount()).isEqualTo(1);
        assertThat(requeued.getNextRetryAt()).isNotNull();

        jdbcTemplate.update("UPDATE sys_media_task SET next_retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                "WHERE task_id = ?", taskId);
        SysMediaTask retryClaim = taskService.pullPendingTask("worker-timeout-retry");
        assertThat(retryClaim.getClaimVersion()).isEqualTo(claim.getClaimVersion() + 1L);
        jdbcTemplate.update("UPDATE sys_media_task SET updated_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' " +
                "WHERE task_id = ?", taskId);

        assertThat(taskService.reclaimTimedOutRunningTasks()).isEqualTo(1);
        SysMediaTask terminal = taskService.getTaskById(taskId);
        assertThat(terminal.getStatus()).isEqualTo(SysMediaTask.TaskStatus.FAILED);
        assertThat(terminal.getRetryCount()).isEqualTo(1);
        assertThat(terminal.getNextRetryAt()).isNull();
    }

    private long insertTask(String name, int maxRetry) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO sys_media_task(task_name, target_url, max_retry)
                VALUES (?, ?, ?)
                RETURNING task_id
                """, Long.class, name + "-" + UUID.randomUUID(),
                "https://example.invalid/media", maxRetry);
    }
}
