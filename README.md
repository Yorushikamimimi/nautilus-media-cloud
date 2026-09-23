# Nautilus · 基于 PostgreSQL SKIP LOCKED 的 Pull 型异步任务调度项目

这是一个异步任务调度原型：**PostgreSQL 存任务行，Worker 主动拉取并执行，通过 HTTP 回写状态**。Java（Spring Boot）控制面负责数据库操作；Python Worker 通过 API 领取任务，示例负载为 URL + yt-dlp 下载；`nautilus-frontend` 是 Vue 3 CDN 静态控制台。仓库没有实现网盘、对象存储或通用文件分发。

## 项目解决的问题

- 不用消息中间件时，如何用**表行**表达队列，并让多 Worker **抢不同行**（`SKIP LOCKED`），避免应用层粗粒度锁。  
- 如何把任务状态持久化到数据库：`PENDING` → `RUNNING` → `SUCCESS`；执行失败或超时可回到 `PENDING` 等待重试，次数耗尽后进入 `FAILED`。
- **控制面 / 执行面分离**：Java 持久化与对外 API；Python 不直连数据库。

## 核心流程

1. 插入 `sys_media_task`，`PENDING`（可按 `next_retry_at` 排序）。  
2. Worker：`GET /api/v1/tasks/pending?workerNode=...` → `pullPendingTask` 在**同一事务**内：`selectOnePendingForUpdate`（`FOR UPDATE SKIP LOCKED`）→ `updateTaskToRunning`；无任务时返回 HTTP 200，JSON `code` 为 204。
3. Worker 执行（如 yt-dlp）→ `PUT /api/v1/tasks/{taskId}/status`；失败走 `handleFailureWithRetry`（`retry_count` / `max_retry` / `next_retry_at`）。  
4. `TaskMaintenanceScheduler` 按 `task.reclaim-interval-ms` 调用 `reclaimTimedOutRunningTasks`，配合 `selectTimedOutRunningForUpdate` 与 `task.running-timeout-seconds`。

## 关键工程点

| 主题 | 位置（对照代码） |
|------|------------------|
| Pull + `SKIP LOCKED` | `SysMediaTaskMapper.xml`：`selectOnePendingForUpdate`、`updateTaskToRunning`、超时 `selectTimedOutRunningForUpdate`；`SysMediaTaskServiceImpl.pullPendingTask`（`@Transactional`）。 |
| 状态与重试 | `reportTaskStatus`、`handleFailureWithRetry`；`scheduleTaskRetry` / `markTaskFinalFailed`；`application.yml` → `task.retry.*`。 |
| 超时回收 | `TaskMaintenanceScheduler`、`reclaimTimedOutRunningTasks`。 |
| JSONB | `SysMediaTask.metaInfo` + `JacksonTypeHandler`；`schema.sql` 中间索引。 |
| 任务变更推送 | `TaskUpdateEvent` → `SseServiceImpl` → `SseEmitter`。 |
| 跨语言 | 控制面编排落库；Worker `httpx` + `asyncio.to_thread`（`elma-stream-worker/main.py`）。 |

## 架构与目录

- **前端** `nautilus-frontend/index.html`：REST + `EventSource` → `/api/v1/tasks/stream`。  
- **控制面** `amy-dispatch-center`：JDBC、任务 API、统计/健康检查、本机文件下载（见边界）。  
- **Worker** `elma-stream-worker/main.py`：仅 HTTP；`NAUTILUS_AUTH_TOKEN` 对齐 `nautilus.auth.token`。  
- **公共** `ruoyi-common`：`AjaxResult`、`ServiceException` 等。  

关系与图示见 [架构文档](docs/ARCHITECTURE.md)。

```
nautilus-media-cloud/
├── docs/
│   ├── ARCHITECTURE.md
│   └── images/                    # 界面预览
├── ruoyi-common/
├── amy-dispatch-center/
├── elma-stream-worker/
├── nautilus-frontend/
├── start_media_cloud.bat / .ps1
├── API_TEST_GUIDE.md
└── test_concurrent_pull.ps1
```

## 项目边界与限制

- **鉴权**：`AuthInterceptor` 校验 Bearer token（默认 `changeme`）；浏览器 SSE 的 `/stream` 使用 `?token=` 作为回退。`/*/download` 不经过该拦截器。默认口令与 URL 参数方式只适合受控本地演示。
- **文件下载**：Worker 将路径写入 `meta_info.filePath`，控制面从自己的本地磁盘读取，且要求路径落在 `nautilus.download.base-dir` 下。仓库约定目录启动时两者默认共享 `amy-dispatch-center/downloads`；跨机器时需把共享存储挂载到两端并配置 `NAUTILUS_DOWNLOAD_DIR` 与 `NAUTILUS_DOWNLOAD_BASE_DIR`。
- **统计**：`GET /stats` 用 SQL 聚合任务状态；`/list` 无分页，`/health` 会读取全部失败任务后再统计，不适合直接用于大规模任务表。
- **回调一致性**：终态、失败重试与心跳回写均校验 `worker_node` 和递增的 `claim_version`；超时重领后，旧 Worker 的迟到回调会被拒绝。`progress` 只用于事件推送，不作为任务列持久化。
- **语义**：接近「至少执行一次」；幂等由业务定义。  
- **在线 Worker**：`WorkerRegistry` 为**进程内**心跳，重启即丢；Worker ID 默认由主机名和进程号组成；可通过 `NAUTILUS_WORKER_ID` 固定覆盖。
- **验证范围**：本轮在 Docker 临时 PostgreSQL 上运行了集成测试：`cd amy-dispatch-center && mvn -q -Dtest=SysMediaTaskPostgresIntegrationTest test`（4 项通过）。测试覆盖并发领取、失败退避、超时回收和旧回调拒绝；这不代表浏览器端到端、干净机器首次启动或部署验收。

## 快速启动

**库**：默认连接 `localhost:5433/nautilus_dispatch`。在新建的测试库中执行 `amy-dispatch-center/src/main/resources/db/schema.sql`；它会先删除已有 `sys_media_task` 表。需要示例数据时再执行 `init-data.sql`。

**环境**：`SPRING_DATASOURCE_PASSWORD`（及按需 URL/用户）；`NAUTILUS_AUTH_TOKEN`（默认 `changeme`，对齐前端登录与 Worker）。

**端口**：后端默认 **8081**、数据库默认 **5433**；静态页示例使用 **5174**。Worker 默认生成可区分的 Worker ID，并与后端共享本机下载目录。

```bash
cd ruoyi-common && mvn clean install && cd ../amy-dispatch-center && mvn spring-boot:run
```

另开终端运行静态页：

```bash
cd nautilus-frontend && python3 -m http.server 5174
```

需要执行示例下载任务时，再开一个终端运行 Worker：

```bash
cd elma-stream-worker && pip install -r requirements.txt
# Windows: set NAUTILUS_AUTH_TOKEN=changeme  |  Linux/macOS: export NAUTILUS_AUTH_TOKEN=changeme
python main.py
```

Windows 一键启动脚本会检查默认端口并设置本机共享下载目录；数据库仍需先准备独立开发实例并初始化。详细步骤见 [本地启动指南](docs/LOCAL_SETUP.md)。

## 界面预览

本地跑通控制台后的操作路径示意（资源用 `docs/images/`）。

### 1. 控制台首页

![控制台首页](./docs/images/dashboard-overview.png)

在线 Worker、任务统计与列表。

### 2. 创建任务

![创建任务](./docs/images/task-create-modal.png)

任务名与目标 URL 提交至控制面。

### 3. 参数配置

![参数配置](./docs/images/task-format-options.png)

格式等字段走 `meta_info`，供 Worker 读取（透传演示）。

### 4. 执行结果

![执行结果](./docs/images/task-success-detail.png)

状态、URL、节点与下载入口（依赖本机路径与边界说明）。

## 设计取舍

- `SKIP LOCKED` 与「先 SELECT 再 UPDATE」非同事务的差别。  
- 认领为何必须 **`selectOnePendingForUpdate` + `updateTaskToRunning` 同事务**。  
- `next_retry_at` 与 `task.retry.base-delay-seconds` / `max-delay-seconds` 的退避含义。  
- RUNNING 超时与 Worker 失联的兜底。  
- JSONB + 索引在 `schema.sql` 中的取舍。  
- 数据库当队列：锁、索引、统计实现 vs 引入 MQ 的边界。  
- 为何 Worker 禁止直连 DB。  

---

仓库目前未附 `LICENSE` 文件。
