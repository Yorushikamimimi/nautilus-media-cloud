# 项目结构与当前实现

```text
nautilus-media-cloud/
├── ruoyi-common/                         # AjaxResult、ServiceException
├── amy-dispatch-center/                  # Spring Boot 控制面，只由后端连接 PostgreSQL
│   └── src/main/
│       ├── java/com/nautilus/dispatch/   # API、任务服务、调度与实体
│       └── resources/                    # application.yml、MyBatis SQL、数据库脚本
├── elma-stream-worker/                   # Python Worker，通过 HTTP 拉取任务和回写状态
├── nautilus-frontend/index.html          # Vue 3 CDN 静态控制台
├── docs/                                 # 架构、本地启动和界面材料
└── start_media_cloud.ps1                 # Windows 本地启动辅助脚本
```

## 任务领取与回调

后端在事务中使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 领取任务。每次领取会递增 `claim_version`；终态、失败重试和心跳更新都校验 Worker 身份及领取版本，过期领取者的回调不会改写新一轮状态。

任务流为 `PENDING` → `RUNNING` → `SUCCESS`，失败或超时后按退避策略回到 `PENDING`，次数耗尽后为 `FAILED`。Worker 不直接连接数据库。任务 heartbeat 回调会更新时间记录的数据库 `updated_at`；用于统计在线节点的 `WorkerRegistry` 则只保存在后端进程内存，服务重启后会清空。

## 数据库脚本边界

`amy-dispatch-center/src/main/resources/db/schema.sql` 会删除并重建 `sys_media_task`，只能用于专用空开发库。后端启动时会检查并补充所需的重试和领取版本字段。集成测试使用独立 PostgreSQL 测试实例，不依赖其他项目数据库。

更完整的本地运行步骤见 [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)，项目能力和验证边界见 [README.md](README.md)。
