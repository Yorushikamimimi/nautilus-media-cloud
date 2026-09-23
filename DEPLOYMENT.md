# 部署说明

仓库当前提供的是本地任务调度原型，不附可直接用于公网生产环境的部署方案，也没有服务器部署验收记录。请按 [本地启动指南](docs/LOCAL_SETUP.md) 在隔离的开发数据库中运行。

当前默认值：

- Spring Boot API：`8081`，可用 `SERVER_PORT` 覆盖。
- PostgreSQL：`localhost:5433/nautilus_dispatch`，可用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖。
- Worker API 地址：`NAUTILUS_API_BASE_URL`，默认 `http://localhost:8081/api/v1/tasks`。
- API token：`NAUTILUS_AUTH_TOKEN`，默认 `changeme`，仅适合本地演示。
- 文件下载路径：Worker 的 `NAUTILUS_DOWNLOAD_DIR` 与后端的 `NAUTILUS_DOWNLOAD_BASE_DIR` 必须对应到同一存储位置。

公网部署前仍需单独设计鉴权、HTTPS、SSE token 传递、共享文件存储、数据库备份、日志与监控，并在目标环境完成验收。数据库当队列的实现和本地集成测试不证明这些部署能力。
