# Worker 快速启动

从仓库根目录按 [本地启动指南](../docs/LOCAL_SETUP.md) 准备 PostgreSQL、启动后端后，再运行：

```bash
cd elma-stream-worker
pip install -r requirements.txt
python main.py
```

默认调度中心地址是 `http://localhost:8081/api/v1/tasks`。Worker 会生成由主机名和进程号组成的默认 ID。环境变量和下载目录配置见 [Worker 说明](README.md)。

状态回调必须带回领取响应中的 `claimVersion` 和 `workerNode`。只读连通性检查见 `test_worker.py`；它校验 API 和数据库均处于运行状态。
