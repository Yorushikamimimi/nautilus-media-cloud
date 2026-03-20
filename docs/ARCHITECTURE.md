# 架构说明（Nautilus 任务调度示例）

## 项目一句话定位

个人演示项目：**用 PostgreSQL 存任务行、由多个 Worker 主动拉取（Pull）并执行**，Java 进程提供 HTTP API 与持久化，Python 进程负责执行具体下载并回写状态；静态单页仅作操作与展示入口。

---

## 模块对照表

| 目录 / 资产 | 职责 |
|-------------|------|
| `ruoyi-common/` | Java 公共依赖：`AjaxResult` 统一 API 形态、`ServiceException` 业务异常；被控制面引用。 |
| `amy-dispatch-center/` | **控制面**：Spring Boot 应用。任务 CRUD、Worker 拉取 `GET /pending`、状态回报 `PUT /status`、列表/统计/健康检查、部分场景下的本机文件下载、SSE 推送；定时回收长时间停留在 `RUNNING` 的任务；内存中记录 Worker 心跳用于「在线数」展示。 |
| `elma-stream-worker/` | **Worker（数据面）**：独立进程。轮询控制面拉任务，用 **yt-dlp**（及可选 ffmpeg）执行URL 下载，把进度/结果通过 HTTP 写回；文件落在 Worker 本机目录。 |
| `nautilus-frontend/` | **控制台**：单文件 `index.html`（Vue 3 + Tailwind CDN）。登录后调 REST、订阅 SSE 刷新列表；不单独承载业务规则。 |
| `amy-dispatch-center/src/main/resources/db/` | 表结构 `schema.sql`、样例数据 `init-data.sql`；任务真相来源。 |
| `start_media_cloud.bat` / `start_media_cloud.ps1` | 本地一键拉起后端、静态页、（可选）Worker 的编排脚本。 |
| `test_*.ps1`、`API_TEST_GUIDE.md` 等 | 接口与链路验证的辅助材料，非运行时核心。 |

目录名 **`amy-dispatch-center`**、**`elma-stream-worker`** 为历史命名，与 GitHub 仓库名 / 产品昵称不必一致；以本表职责为准。

---

## 控制面 / Worker / PostgreSQL / 前端 的关系

```
                    ┌─────────────────────┐
                    │  nautilus-frontend  │
                    │  （浏览器静态页）    │
                    └──────────┬──────────┘
                               │ HTTPS/HTTP：REST + EventSource(SSE)
                               ▼
                    ┌─────────────────────┐
                    │ amy-dispatch-center │
                    │ （Java 控制面）      │
                    └──────────┬──────────┘
                               │ JDBC
                               ▼
                    ┌─────────────────────┐
                    │     PostgreSQL      │
                    │ 表：sys_media_task   │
                    └──────────▲──────────┘
                               │
         ┌─────────────────────┴─────────────────────┐
         │ HTTP：GET pending / PUT status / …          │
         ▼                                               │
┌─────────────────────┐                                  │
│ elma-stream-worker  │──────────────────────────────────┘
│ （Python，可多实例） │   读写作业行，不直接连库
└─────────────────────┘
```

- **PostgreSQL**：仅存任务及状态字段（含 JSONB `meta_info`）；抢占语义依赖 **`SELECT … FOR UPDATE SKIP LOCKED`** 与随后同事务内的 `UPDATE`（见 `SysMediaTaskMapper.xml` + `SysMediaTaskServiceImpl`）。  
- **控制面**：唯一连库写业务表的一方；对外统一暴露 REST。  
- **Worker**：不访问数据库；通过 API 认领任务并回报，符合典型「Pull Worker」边界。  
- **前端**：只与控制面交互，用于人机操作与实时列表刷新。

---

## 当前边界与限制

以下均为**现有实现下的客观约束**，便于面试时主动说明，避免被误认为完整「云产品」。

| 类别 | 说明 |
|------|------|
| 安全 | API 使用固定 **Bearer Token** 演示鉴权；部分路径在配置中排除拦截器（如 SSE、文件下载）。不适合公网零信任部署。 |
| 存储 | 成功文件路径多写在 `meta_info` 中，指向 **Worker 本机路径**；控制面提供的下载接口读本地文件。多机 Worker 时，「下载」与路径语义需重新设计。 |
| 伸缩 | 统计等能力可能依赖「全量或大范围列表」再在应用内聚合，数据量增大后需改为 SQL 聚合或分页。 |
| Worker 在线 | `WorkerRegistry` 为 **进程内内存表**；重启控制面或进程即丢失；「在线数」为短时心跳推断，非集群协调服务。 |
| 执行语义 | 队列语义接近「至少投递一次」；业务是否幂等、重复执行同一 URL 是否可接受，由调用方与任务含义决定。 |

---

## 为什么是「Pull 型任务调度」而不是 Media Cloud

| 维度 | 本项目实际形态 |
|------|----------------|
| 核心对象 | **任务行**（URL、状态、重试字段、元数据 JSONB），不是面向租户的**媒体资产库**或**对象存储桶**。 |
| 数据流 | Worker **主动请求**「给我下一条待处理任务」，而不是云存储常见的 **推送上传事件 + 全局元数据服务**。 |
| 技术主线条 | 并发抢占、状态机、超时回收、失败重试——属于 **Job Queue / 轻量调度** 范畴；未实现 CDN、多区域副本、存储分层等「云」能力。 |
| 命名 | 仓库与 UI 可能仍带「媒体」字样，源于演示场景使用 **流媒体 URL + yt-dlp**；换成任意「待处理 URL + 本地脚本」仍可复用同一套控制面与表结构。 |

因此对外介绍时，用 **「基于 PostgreSQL 的 Pull 型任务调度示例」** 比 **「Media Cloud」** 更贴近代码真实边界，也更有利于面试官把问题聚焦在事务、状态机与多进程协作上。

---

*文档与仓库实现同步；若改动 API 或表结构，请一并更新本节。*
