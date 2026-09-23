# Worker 部署边界

本仓库当前提供本地 Worker 启动和调度示例，没有完成服务器或容器部署验收。本文件不提供可直接执行的生产部署命令。

本地启动请按 [仓库本地启动指南](../docs/LOCAL_SETUP.md) 操作；Worker 环境变量和领取回调格式见 [Worker README](README.md)。

部署到其他机器前，至少需要配置可达的 `NAUTILUS_API_BASE_URL`、与后端一致的 `NAUTILUS_AUTH_TOKEN`，以及后端和 Worker 可共同访问的下载路径。当前鉴权默认值适用于本地演示。公网鉴权、TLS、共享存储、备份和监控方案仍需设计并在目标环境验证。
