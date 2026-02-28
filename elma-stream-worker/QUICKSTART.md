# 🚀 Elma Stream Worker - 快速启动指南

> 3 分钟快速部署 Python Worker 节点

---

## ⚡ 前置条件

- ✅ Python 3.10+ 已安装
- ✅ Java 调度中心 (amy-dispatch-center) 已启动
- ✅ PostgreSQL 数据库已初始化

---

## 📦 步骤 1: 安装依赖 (30 秒)

### Windows (PowerShell)

```powershell
cd elma-stream-worker
pip install -r requirements.txt
```

### Linux/macOS

```bash
cd elma-stream-worker
pip3 install -r requirements.txt
```

---

## ⚙️ 步骤 2: 配置参数 (可选,30 秒)

默认配置直接可用,如需自定义:

```python
# 编辑 main.py 顶部配置
BASE_URL = "http://localhost:8080/api/v1/tasks"  # Java 调度中心地址
WORKER_ID = "Elma-Node-01"                        # Worker 节点标识
POLL_INTERVAL = 3                                 # 轮询间隔(秒)
```

---

## 🎯 步骤 3: 启动 Worker (10 秒)

### 使用启动脚本 (推荐)

**Windows:**
```powershell
.\start-worker.ps1
```

**Linux/macOS:**
```bash
chmod +x start-worker.sh
./start-worker.sh
```

### 直接运行

```bash
python main.py
```

---

## ✅ 步骤 4: 验证运行 (30 秒)

### 1. 查看日志输出

应看到类似信息:

```
============================================================
🌙 Elma Stream Worker 启动成功
   Worker ID: Elma-Node-01
   Java 调度中心: http://localhost:8080/api/v1/tasks
   轮询间隔: 3s
============================================================
[2026-02-27 15:30:01] [INFO] 🌙 思想犯 - 当前无任务,Elma 正在游荡...
```

### 2. 创建测试任务

打开新终端,执行:

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "又三郎 4K MV 抓取",
    "targetUrl": "https://www.youtube.com/watch?v=test",
    "metaInfo": {"artist": "ヨルシカ"}
  }'
```

### 3. 观察 Worker 自动执行

Worker 日志应输出:

```
[2026-02-27 15:30:04] [INFO] 🌙 夜行 - 任务拉取成功 | TaskID: 1001 | TaskName: 又三郎 4K MV 抓取
[2026-02-27 15:30:04] [INFO] 🌙 又三郎 - 开始提取媒体流 | TaskID: 1001 | URL: https://...
[2026-02-27 15:30:07] [INFO] 🌙 又三郎 - 媒体流提取完毕,风载着数据归来 (模拟模式)
[2026-02-27 15:30:07] [INFO] 🌙 夜行 - 节点状态更新成功 | TaskID: 1001 | Status: SUCCESS
```

---

## 🐳 (可选) Docker 快速启动

如果您更喜欢容器化部署:

```bash
# 构建镜像
docker build -t elma-worker:latest .

# 启动单节点
docker run -d \
  --name elma-worker-01 \
  -e BASE_URL=http://host.docker.internal:8080/api/v1/tasks \
  -e WORKER_ID=Elma-Docker-Node-01 \
  elma-worker:latest

# 查看日志
docker logs -f elma-worker-01
```

或使用 Docker Compose 启动多节点:

```bash
docker-compose up -d
```

---

## 🧪 运行自动化测试

验证所有功能是否正常:

```bash
python test_worker.py
```

预期输出:

```
🧪 Elma Stream Worker - 功能测试套件
============================================================
✓ 调度中心在线 (HTTP 200)
✓ 任务拉取成功
✓ 状态回报成功
📊 测试总结
✓ 全部通过: 5/5 (100%)

🎉 又三郎 - 测试风载着好消息归来!
```

---

## 🛑 停止 Worker

### 本地运行

按 `Ctrl+C` 即可优雅退出:

```
[2026-02-27 15:35:00] [INFO] 🌙 夜行 - 收到中断信号,Elma 准备休眠...
🌙 再见,Elma 陷入长眠...
```

### Docker 运行

```bash
# 停止单容器
docker stop elma-worker-01

# 停止 Docker Compose 集群
docker-compose down
```

---

## 🚨 常见问题

### Q1: 提示 "模块未找到"

**解决方案:**
```bash
pip install -r requirements.txt --upgrade
```

### Q2: 无法连接 Java 调度中心

**检查清单:**
- ✅ Java 服务是否启动: `curl http://localhost:8080/api/v1/tasks/health`
- ✅ 端口是否被占用: `netstat -an | findstr 8080`
- ✅ 防火墙是否拦截

### Q3: yt-dlp 未安装警告

**说明:** Worker 会自动切换到模拟模式,不影响功能测试。

**如需真实提取:**
```bash
pip install yt-dlp
```

---

## 📚 更多文档

- 📖 [完整说明 - README.md](./README.md)
- 🚀 [部署指南 - DEPLOYMENT.md](./DEPLOYMENT.md)
- 🏗️ [架构说明 - STRUCTURE.md](./STRUCTURE.md)

---

## 🎵 Yorushika 主题日志说明

| 前缀 | 含义 | 场景 |
|------|------|------|
| 🌙 **夜行** | 操作成功 | 任务拉取成功、状态回报成功 |
| 🚨 **思想犯** | 正常警告 | 无任务可拉取 |
| 🎵 **又三郎** | 数据操作 | 媒体流提取、数据传输 |
| ❌ **春泥棒** | 异常失败 | 网络错误、提取失败 |

---

## 🎉 恭喜!

您已成功部署 Elma Stream Worker!

Worker 将持续轮询 Java 调度中心,自动执行流媒体提取任务。

如有问题,请参阅 [完整文档](./README.md) 或提交 Issue。

---

> **又三郎** - 风载着任务归来,让我们开始工作吧! 🌙
