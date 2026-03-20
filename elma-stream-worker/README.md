# Elma Stream Worker - Python 数据面节点

## 🌙 项目简介

Nautilus 任务调度示例的 Python Worker：从 Java 控制面拉取任务并执行媒体提取（yt-dlp）。

**Powered by Yorushika (ヨルシカ) & asyncio** 🎵

## 📦 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Python | 3.10+ | 核心语言 |
| httpx | 0.27.0 | 异步 HTTP 客户端 |
| asyncio | 内置 | 事件循环 |
| yt-dlp | 2024.8.6 | 流媒体提取 |
| colorlog | 6.8.2 | 彩色日志输出 |

## 🚀 快速开始

### 1. 安装依赖

```bash
# 使用 pip
pip install -r requirements.txt

# 或使用 pip3
pip3 install -r requirements.txt
```

### 2. 配置参数

编辑 `main.py` 中的全局配置:

```python
BASE_URL = "http://localhost:8080/api/v1/tasks"  # Java 调度中心地址
WORKER_ID = "Elma-Node-01"                        # 当前 Worker 标识
POLL_INTERVAL = 3                                 # 轮询间隔(秒)
```

### 3. 启动节点

```bash
# 直接运行
python main.py

# 或使用 Python 3.10+
python3 main.py
```

## 📡 核心功能

### 1. 异步轮询 (Pull)

每隔 3 秒向 Java 调度中心发起 GET 请求:

```http
GET http://localhost:8080/api/v1/tasks/pending?workerNode=Elma-Node-01
```

### 2. 任务执行 (Execute)

- **真实模式**: 调用本地 `yt-dlp` 提取流媒体元数据
- **模拟模式**: 如果未安装 yt-dlp,自动切换为 `asyncio.sleep(3)` 模拟高 I/O 操作

### 3. 状态回调 (Callback)

执行完成后向 Java 端回报状态:

```http
PUT http://localhost:8080/api/v1/tasks/{taskId}/status
Content-Type: application/json

{
  "status": "SUCCESS",
  "errorLog": null
}
```

## 🎵 Yorushika 主题日志

所有日志输出都融合了 Yorushika 元素:

| 场景 | 日志前缀 | 示例 |
|------|---------|------|
| 任务拉取成功 | 🌙 夜行 | `夜行 - 任务拉取成功 \| TaskID: 1001` |
| 无任务可拉取 | 🚨 思想犯 | `思想犯 - 当前无任务,Elma 正在游荡...` |
| 媒体流提取成功 | 🎵 又三郎 | `又三郎 - 媒体流提取完毕,风载着数据归来` |
| 异常或失败 | ❌ 春泥棒 | `春泥棒 - 抓取链路断裂,花瓣散落` |
| 状态回报成功 | 🌙 夜行 | `夜行 - 节点状态更新成功 \| Status: SUCCESS` |

## 🔧 高级配置

### 安装 yt-dlp (可选)

如需真实提取流媒体,请安装 yt-dlp:

```bash
# Windows (使用 pip)
pip install yt-dlp

# macOS/Linux
pip3 install yt-dlp

# 或使用系统包管理器
brew install yt-dlp  # macOS
```

### 调整请求超时

在 `main.py` 中修改:

```python
REQUEST_TIMEOUT = 30.0  # HTTP 请求超时(秒)
```

### 并发启动多节点

修改 `WORKER_ID` 后启动多个实例:

```bash
# 终端 1
# 修改 WORKER_ID = "Elma-Node-01"
python main.py

# 终端 2
# 修改 WORKER_ID = "Elma-Node-02"
python main.py

# 终端 3
# 修改 WORKER_ID = "Elma-Node-03"
python main.py
```

## 📊 日志示例

正常运行时的日志输出:

```
============================================================
🌙 Elma Stream Worker 启动成功
   Worker ID: Elma-Node-01
   Java 调度中心: http://localhost:8080/api/v1/tasks
   轮询间隔: 3s
============================================================
[2026-02-27 15:30:01] [INFO] 🌙 夜行 - 任务拉取成功 | TaskID: 1001 | TaskName: 又三郎 4K MV 抓取
[2026-02-27 15:30:01] [INFO] 🌙 又三郎 - 开始提取媒体流 | TaskID: 1001 | URL: https://youtube.com/...
[2026-02-27 15:30:04] [INFO] 🌙 又三郎 - 媒体流提取完毕,风载着数据归来 (模拟模式)
[2026-02-27 15:30:04] [INFO] 🌙 夜行 - 节点状态更新成功 | TaskID: 1001 | Status: SUCCESS
[2026-02-27 15:30:07] [DEBUG] 🌙 思想犯 - 当前无任务,Elma 正在游荡...
```

## 🛡️ 异常处理

所有网络请求都包含完整的异常捕获:

- `httpx.HTTPStatusError`: HTTP 状态码错误
- `httpx.RequestError`: 网络连接失败
- `asyncio.TimeoutError`: 操作超时
- `Exception`: 通用异常兜底

## 🧪 测试指南

### 1. 启动 Java 调度中心

```bash
cd amy-dispatch-center
mvn spring-boot:run
```

### 2. 初始化测试数据

```bash
psql -U postgres -d nautilus_dispatch -f src/main/resources/db/init-data.sql
```

### 3. 启动 Python Worker

```bash
cd elma-stream-worker
python main.py
```

### 4. 观察日志输出

Worker 将自动拉取任务并执行,观察控制台日志验证功能。

## 📝 代码规范

- ✅ 严格使用 Python 3.10+ 语法
- ✅ 所有函数包含完整的类型注解
- ✅ 使用 `async def` 和 `await` 异步编程
- ✅ 异常处理使用 `try-except` 包裹网络请求
- ✅ 日志输出支持 UTF-8 编码

## 🐛 常见问题

### Q1: yt-dlp 未安装怎么办?

A: Worker 会自动检测并切换到模拟模式,不影响功能测试。

### Q2: 连接 Java 端失败?

A: 检查 `BASE_URL` 配置是否正确,确保 Java 调度中心已启动。

### Q3: 如何停止 Worker?

A: 按 `Ctrl+C` 发送中断信号,Worker 会优雅退出。

## 📄 文件结构

```
elma-stream-worker/
├── requirements.txt       # 依赖清单
├── main.py               # 主程序
└── README.md             # 本文档
```

## 🤝 与 Java 端集成

Worker 节点与 Java 调度中心的交互流程:

```
┌─────────────────┐       GET /pending        ┌─────────────────┐
│                 │ ───────────────────────> │                 │
│  Python Worker  │                           │  Java 调度中心  │
│  (Elma Node)    │ <─────────────────────── │  (Spring Boot)  │
│                 │   返回任务 JSON          │                 │
└─────────────────┘                           └─────────────────┘
        │                                              ▲
        │ yt-dlp 提取媒体流                           │
        │                                              │
        └────────────────> PUT /status ───────────────┘
                          回报执行结果
```

## 📚 参考资料

- [httpx 官方文档](https://www.python-httpx.org/)
- [asyncio 官方文档](https://docs.python.org/3/library/asyncio.html)
- [yt-dlp GitHub](https://github.com/yt-dlp/yt-dlp)
- [Yorushika 官网](https://yorushika.com/)

## 📄 许可证

MIT License

---

> **又三郎** - 风载着数据归来,夜行中的每个任务都将完成 🌙
