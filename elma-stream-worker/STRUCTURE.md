# Worker 目录说明

当前 Worker 入口为 `main.py`，通过 HTTP 与 Spring Boot 调度中心交互，不直接连接 PostgreSQL。

```text
elma-stream-worker/
├── main.py                 # 轮询、媒体处理与状态回调
├── requirements.txt        # Python 依赖
├── config.template.py      # 配置参考；运行配置以环境变量为准
├── test_worker.py          # 只读健康检查，不改任务数据
├── start-worker.ps1        # Windows 启动与健康检查
├── start-worker.sh         # macOS/Linux 启动与健康检查
└── README.md               # Worker 当前配置和回调说明
```

Worker 使用 `NAUTILUS_API_BASE_URL`、`NAUTILUS_AUTH_TOKEN`、`NAUTILUS_WORKER_ID` 和 `NAUTILUS_DOWNLOAD_DIR` 配置地址、鉴权、节点标识与下载目录。每次领取响应中的 `workerNode` 和 `claimVersion` 必须随状态回调提交；超时重领后旧领取者的回调会被拒绝。

- Worker 使用方式：[Worker README](README.md)
- 本地服务与独立开发数据库：[本地启动指南](../docs/LOCAL_SETUP.md)
- 项目架构和验证边界：[仓库 README](../README.md)

Docker、多节点编排及 Kubernetes 部署流程没有在本轮验证，不作为当前启动指引。
