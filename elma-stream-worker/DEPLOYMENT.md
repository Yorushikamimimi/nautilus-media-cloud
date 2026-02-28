# Elma Stream Worker - 部署指南

## 🚀 部署方式概览

Elma Stream Worker 支持以下三种部署方式:

1. **本地直接运行** - 适合开发测试
2. **Docker 容器部署** - 适合生产环境
3. **K8s/Docker Swarm** - 适合大规模集群

---

## 📦 方式一: 本地直接运行

### 环境要求

- Python 3.10 或更高版本
- pip 包管理器
- (可选) yt-dlp 命令行工具

### 部署步骤

#### 1. 克隆/下载项目

```bash
cd nautilus-media-cloud/elma-stream-worker
```

#### 2. 安装依赖

**Windows (PowerShell):**
```powershell
pip install -r requirements.txt
```

**Linux/macOS:**
```bash
pip3 install -r requirements.txt
```

#### 3. 配置参数

编辑 `main.py` 中的全局配置:

```python
BASE_URL = "http://your-java-center:8080/api/v1/tasks"  # 修改为实际地址
WORKER_ID = "Elma-Node-Prod-01"                          # 修改为生产环境标识
POLL_INTERVAL = 3                                        # 根据负载调整
```

或使用配置文件:

```bash
cp config.template.py config.py
# 编辑 config.py 修改配置
```

#### 4. 启动 Worker

**使用启动脚本 (推荐):**

Windows:
```powershell
.\start-worker.ps1
```

Linux/macOS:
```bash
chmod +x start-worker.sh
./start-worker.sh
```

**直接运行:**
```bash
python main.py
```

#### 5. 验证运行

查看日志输出,应看到类似信息:

```
============================================================
🌙 Elma Stream Worker 启动成功
   Worker ID: Elma-Node-Prod-01
   Java 调度中心: http://localhost:8080/api/v1/tasks
   轮询间隔: 3s
============================================================
```

---

## 🐳 方式二: Docker 容器部署

### 环境要求

- Docker 20.10+
- Docker Compose 1.29+ (可选)

### 单容器部署

#### 1. 构建镜像

```bash
cd elma-stream-worker
docker build -t elma-worker:latest .
```

#### 2. 运行容器

```bash
docker run -d \
  --name elma-worker-01 \
  --restart unless-stopped \
  -e BASE_URL=http://host.docker.internal:8080/api/v1/tasks \
  -e WORKER_ID=Elma-Docker-Node-01 \
  -e POLL_INTERVAL=3 \
  elma-worker:latest
```

**参数说明:**
- `--restart unless-stopped`: 自动重启
- `-e BASE_URL`: Java 调度中心地址
- `-e WORKER_ID`: Worker 节点标识
- `host.docker.internal`: Docker 内访问宿主机地址

#### 3. 查看日志

```bash
docker logs -f elma-worker-01
```

### 多容器部署 (Docker Compose)

#### 1. 启动集群

```bash
docker-compose up -d
```

默认配置会启动 3 个 Worker 节点:
- `elma-worker-01`
- `elma-worker-02`
- `elma-worker-03`

#### 2. 扩展节点数量

```bash
# 扩展到 5 个节点
docker-compose up -d --scale elma-worker-01=5
```

#### 3. 查看集群状态

```bash
docker-compose ps
```

#### 4. 停止集群

```bash
docker-compose down
```

---

## ☸️ 方式三: Kubernetes 部署

### 前置准备

- Kubernetes 集群 1.20+
- kubectl 命令行工具
- (可选) Helm 3.0+

### Deployment 配置示例

创建 `k8s-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: elma-worker
  namespace: nautilus-media
spec:
  replicas: 3  # Worker 节点数量
  selector:
    matchLabels:
      app: elma-worker
  template:
    metadata:
      labels:
        app: elma-worker
    spec:
      containers:
      - name: worker
        image: your-registry/elma-worker:latest
        env:
        - name: BASE_URL
          value: "http://amy-dispatch-center:8080/api/v1/tasks"
        - name: WORKER_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name  # 使用 Pod 名称作为 Worker ID
        - name: POLL_INTERVAL
          value: "3"
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          exec:
            command:
            - python
            - -c
            - "import sys; sys.exit(0)"
          initialDelaySeconds: 10
          periodSeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  name: elma-worker-headless
  namespace: nautilus-media
spec:
  clusterIP: None
  selector:
    app: elma-worker
  ports:
  - port: 80
    targetPort: 80
```

### 部署步骤

#### 1. 创建命名空间

```bash
kubectl create namespace nautilus-media
```

#### 2. 部署 Worker

```bash
kubectl apply -f k8s-deployment.yaml
```

#### 3. 查看 Pod 状态

```bash
kubectl get pods -n nautilus-media -l app=elma-worker
```

#### 4. 查看日志

```bash
# 查看特定 Pod
kubectl logs -f elma-worker-xxxxx-yyyyy -n nautilus-media

# 查看所有 Worker 日志
kubectl logs -l app=elma-worker -n nautilus-media --tail=50
```

#### 5. 水平扩缩容

```bash
# 扩展到 10 个节点
kubectl scale deployment elma-worker --replicas=10 -n nautilus-media
```

---

## 🔧 生产环境配置建议

### 1. 日志管理

**本地部署:**
- 使用 `systemd` 服务管理,日志输出到 journald
- 配置日志轮转 (logrotate)

**Docker 部署:**
- 配置 Docker 日志驱动 (json-file + max-size)
- 或使用 ELK/EFK 日志收集

**K8s 部署:**
- 部署 Fluentd/Fluent Bit 收集器
- 集成 Elasticsearch + Kibana

### 2. 监控告警

**推荐方案:**
- Prometheus + Grafana
- 自定义 `/metrics` 端点导出指标

**关键指标:**
- 任务拉取成功率
- 任务执行耗时
- Worker 节点存活状态
- HTTP 请求失败次数

### 3. 高可用配置

**多节点部署:**
- 至少部署 3 个 Worker 节点
- 使用负载均衡确保任务分发均匀

**故障恢复:**
- 配置自动重启策略
- 任务超时后自动回滚为 PENDING 状态

### 4. 安全加固

**网络安全:**
- 使用 HTTPS 连接 Java 调度中心
- 配置防火墙规则限制访问

**认证鉴权:**
- 在 HTTP 请求中添加 API Token
- 使用 mTLS 双向认证

---

## 🧪 部署后验证

### 1. 运行测试脚本

```bash
python test_worker.py
```

预期输出:

```
🧪 Elma Stream Worker - 功能测试套件
   Powered by Yorushika (ヨルシカ) 🌙
============================================================
[测试] Java 调度中心连接测试
✓ 调度中心在线 (HTTP 200)

[测试] 任务拉取功能测试
✓ 任务拉取成功
   - TaskID: 1001
   - TaskName: 又三郎 4K MV 抓取
   - Status: RUNNING

📊 测试总结
✓ 全部通过: 5/5 (100%)

🎉 又三郎 - 测试风载着好消息归来!
```

### 2. 手动验证任务流程

**Step 1: 创建测试任务**
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "测试任务",
    "targetUrl": "https://www.youtube.com/watch?v=test",
    "metaInfo": {"test": true}
  }'
```

**Step 2: 观察 Worker 日志**

应看到 Worker 自动拉取并执行任务:

```
[2026-02-27 15:30:01] [INFO] 🌙 夜行 - 任务拉取成功 | TaskID: 1001
[2026-02-27 15:30:04] [INFO] 🌙 又三郎 - 媒体流提取完毕,风载着数据归来
[2026-02-27 15:30:04] [INFO] 🌙 夜行 - 节点状态更新成功 | Status: SUCCESS
```

**Step 3: 验证任务状态**
```bash
curl http://localhost:8080/api/v1/tasks/1001
```

响应中 `status` 应为 `SUCCESS`。

---

## 🛠️ 常见问题排查

### 问题 1: Worker 无法连接 Java 调度中心

**症状:**
```
春泥棒 - 请求失败: ConnectionRefused
```

**排查步骤:**
1. 检查 `BASE_URL` 配置是否正确
2. 确认 Java 调度中心已启动: `curl http://localhost:8080/api/v1/tasks/health`
3. 检查网络防火墙规则
4. Docker 环境检查 `host.docker.internal` 是否可达

### 问题 2: yt-dlp 执行失败

**症状:**
```
春泥棒 - yt-dlp 执行失败: ERROR: Unsupported URL
```

**排查步骤:**
1. 检查目标 URL 是否合法
2. 更新 yt-dlp 到最新版本: `pip install --upgrade yt-dlp`
3. 手动测试: `yt-dlp --skip-download <URL>`

### 问题 3: Worker 频繁重启

**症状:**
Docker 容器反复重启

**排查步骤:**
1. 查看容器日志: `docker logs elma-worker-01`
2. 检查内存/CPU 资源是否充足
3. 确认 Python 版本 >= 3.10
4. 检查依赖是否正确安装

---

## 📊 性能调优建议

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `POLL_INTERVAL` | 1-5 秒 | 任务量大时降低,任务少时提高 |
| `REQUEST_TIMEOUT` | 30-60 秒 | 根据网络延迟调整 |
| Worker 节点数量 | 3-10 个 | 根据任务并发量决定 |
| 容器内存限制 | 256-512 MB | 单任务场景下足够 |
| 容器 CPU 限制 | 0.5-1.0 核 | I/O 密集型任务 |

---

## 📄 附录: systemd 服务配置

创建 `/etc/systemd/system/elma-worker.service`:

```ini
[Unit]
Description=Elma Stream Worker
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/elma-stream-worker
ExecStart=/usr/bin/python3 /opt/elma-stream-worker/main.py
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启用服务:

```bash
sudo systemctl daemon-reload
sudo systemctl enable elma-worker
sudo systemctl start elma-worker
sudo systemctl status elma-worker
```

---

> **夜行** - 愿每个 Worker 节点都稳定运行,在黑夜中守护任务执行 🌙
