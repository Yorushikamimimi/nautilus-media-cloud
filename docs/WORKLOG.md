# WORKLOG

## 2026-07-01 — 全项目审查与修复

审查范围：`ruoyi-common` / `amy-dispatch-center` / `elma-stream-worker` / `nautilus-frontend` / 配置与 SQL

---

### 🔴 高优修复

#### 1. 路径穿越漏洞 — downloadFile 无路径校验

**问题**：Worker 写回的 `meta_info.filePath` 未校验。恶意方可写入 `../../etc/passwd` 等路径，控制面直接读取返回任意文件。

**修复**：
- `application.yml`：新增 `nautilus.download.base-dir` 配置项
- `MediaTaskController.downloadFile`：文件存在后，`toRealPath()` 规范化路径并校验 `startsWith(baseDir)`，不在允许目录内返回 404

**改动文件**：
- `amy-dispatch-center/src/main/resources/application.yml`
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/controller/MediaTaskController.java`

---

#### 2. SSE stream 端点无鉴权

**问题**：浏览器 `EventSource` 不支持自定义请求头，前端只能 URL 传 token。`AuthInterceptor` 只读 `Authorization` header，且 `WebMvcConfig` 排除了 `/stream` 端点——导致 SSE 实际无鉴权。

**修复**：
- `AuthInterceptor.preHandle`：Header 无 token 时 fallback 读 `request.getParameter("token")`
- `WebMvcConfig`：移除 `/api/v1/tasks/stream` 排除项，SSE 端点纳入拦截

**改动文件**：
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/config/AuthInterceptor.java`
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/config/WebMvcConfig.java`

---

#### 3. 心跳无所有权校验 — Worker A 能给 Worker B 的任务续命

**问题**：`touchTaskHeartbeat` SQL 只校验 `taskId + status=RUNNING`，未校验 `worker_node`。挂掉的 Worker 的任务可被其他 Worker 续命、永不回收。

**修复**：
- `SysMediaTaskMapper.xml`：`touchTaskHeartbeat` SQL 加 `AND worker_node = #{workerNode}`
- 全链路透传 `workerNode`：Controller 从 request body 提取 → Service → Mapper
- Python Worker `main.py`：`report_status` 请求体新增 `workerNode` 字段

**改动文件**：
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/controller/MediaTaskController.java`
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/service/ISysMediaTaskService.java`
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/service/impl/SysMediaTaskServiceImpl.java`
- `amy-dispatch-center/src/main/java/com/nautilus/dispatch/mapper/SysMediaTaskMapper.java`
- `amy-dispatch-center/src/main/resources/mapper/SysMediaTaskMapper.xml`
- `elma-stream-worker/main.py`

---

### 🟡 中优修复

#### 4. getStats() 全量内存聚合 → SQL 聚合

**问题**：`listTasks(null)` 全量拉到 Java 内存 stream 统计。任务万级时 OOM。

**修复**：改用 `COUNT(*) FILTER (WHERE status = ...)` SQL 聚合 + `SUM((meta_info->>'fileSize')::bigint)` 计算今日流量。

**改动文件**：`MediaTaskController.java`

---

#### 5. Python Worker 错误分类不精确

**问题**：`sqlite3.OperationalError` 与 `PermissionError/FileNotFoundError` 混在一起判为 cookie 问题。yt-dlp 内部也用 sqlite，会误判。`"progress" in locals()` 冗余检查。

**修复**：
- `sqlite3.OperationalError` 单独 catch，按通用下载错误处理
- 移除 `locals()` 冗余检查

**改动文件**：`elma-stream-worker/main.py`

---

#### 6. 前端 API_BASE 硬编码

**问题**：`const API_BASE = 'http://localhost:8080/api/v1/tasks'` 硬编码，部署需改代码。

**修复**：改为 `window.NAUTILUS_API_BASE || 'http://localhost:8080/api/v1/tasks'`，部署时可在 HTML 前设全局变量覆盖。

**改动文件**：`nautilus-frontend/index.html`

---

### 📋 未修复/已知限制（记录备案）

| 项 | 说明 |
|----|------|
| SSE URL 传 token | 浏览器 `EventSource` 不支持自定义 header，token 仍在 URL 中。生产建议用 cookie 或临时 ticket 握手 |
| `TaskSchemaBootstrap` 启动跑 DDL | 每次启动 `ALTER TABLE ADD COLUMN IF NOT EXISTS`，演示可接受；生产用 Flyway/Liquibase |
| `CorsConfig` 过于宽松 | `allowedOriginPattern("*")`，演示可接受 |
| `progress` 字段未持久化 | `@TableField(exist = false)`，重启丢失 |
| 零单元测试 | 核心逻辑无测试覆盖 |
