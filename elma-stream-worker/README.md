# Elma Stream Worker

Python Worker 从 Spring Boot 调度中心领取任务、执行 yt-dlp 处理，并回报进度和结果。Worker 只通过 HTTP API 访问控制面，不直接连接 PostgreSQL。

## 启动

```bash
pip install -r requirements.txt
python main.py
```

默认调度中心地址为 `http://localhost:8081/api/v1/tasks`。在本地多实例时，每个进程默认生成由主机名和进程号组成的 Worker ID。需要固定名称时可设置 `NAUTILUS_WORKER_ID`。

## 可配置项

| 环境变量 | 默认值 | 用途 |
|----------|--------|------|
| `NAUTILUS_API_BASE_URL` | `http://localhost:8081/api/v1/tasks` | 调度中心 API 根地址 |
| `NAUTILUS_AUTH_TOKEN` | `changeme` | 与后端一致的 Bearer token |
| `NAUTILUS_WORKER_ID` | 主机名和进程号 | 固定 Worker 标识 |
| `NAUTILUS_DOWNLOAD_DIR` | `../amy-dispatch-center/downloads`（相对仓库结构解析为绝对路径） | Worker 写入目录 |

后端 `NAUTILUS_DOWNLOAD_BASE_DIR` 必须指向 Worker 写入的同一目录，才能通过后端下载接口读取文件。仓库根目录的 `start_media_cloud.ps1` 会为本机流程设置共享路径；跨机器时请挂载共享存储并在两端配置相同路径。

## 任务回调

Worker 领取任务后，调度中心返回 `workerNode` 和递增的 `claimVersion`。状态回调需要带回领取时的这两个值，后端据此拒绝已超时并重新领取后的旧回调：

```json
{
  "status": "SUCCESS",
  "workerNode": "worker-host-1234",
  "claimVersion": 1,
  "errorLog": null
}
```

启动辅助脚本：macOS/Linux 使用 `./start-worker.sh`，Windows 使用 `./start-worker.ps1`。两者的健康检查会使用与 Worker 相同的 Bearer token。
