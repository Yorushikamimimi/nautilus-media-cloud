# Nautilus Media Cloud · 鹦鹉螺媒体云

流媒体调度中台：**控制面（Java Spring Boot）+ 数据面（Python Worker）+ 前端（Vue 3 静态）**，支持高并发任务拉取与状态管理。

**Powered by Yorushika (ヨルシカ)** 🌙

---

## ⚡ 快速开始（Windows 一键点火）

1. **准备数据库**：安装 PostgreSQL，创建库 `nautilus_dispatch`，执行 `amy-dispatch-center/src/main/resources/db/schema.sql` 与 `init-data.sql`。
2. **配置密码与 Token（仅首次）**：
   - 设置环境变量 `DB_PASSWORD` 为你的 PostgreSQL 密码，或直接修改 `amy-dispatch-center/src/main/resources/application.yml` 中的 `password: your_postgres_password`。
   - 若需自定义 API 鉴权，设置环境变量 `NAUTILUS_AUTH_TOKEN`（与前端登录/Worker 使用的 Token 一致）；默认开发值为 `change-me`。
3. **双击运行**：在项目根目录双击 `start_media_cloud.bat`，脚本会自动清理占用端口、启动后端与前端，并打开浏览器访问 `http://localhost:5173`。  
   **关闭**：先关网页，再在弹出的两个最小化命令行窗口里按 `Ctrl+C` 即可。

---

## 📦 模块结构

```
nautilus-media-cloud/
├── ruoyi-common/              # 公共模块 (Java)
│   ├── core/domain/           
│   │   └── AjaxResult.java    # 统一响应封装
│   └── exception/
│       └── ServiceException.java
│
├── amy-dispatch-center/       # 调度中心 (Java) ⭐ 控制面
│   ├── domain/entity/
│   │   └── SysMediaTask.java  # 任务实体
│   ├── mapper/
│   │   ├── SysMediaTaskMapper.java
│   │   └── SysMediaTaskMapper.xml  # 核心原子拉取 SQL
│   ├── service/
│   │   ├── ISysMediaTaskService.java
│   │   └── impl/SysMediaTaskServiceImpl.java
│   ├── controller/
│   │   └── MediaTaskController.java
│   └── resources/
│       ├── application.yml
│       └── db/
│           ├── schema.sql     # 建表脚本
│           └── init-data.sql  # 测试数据
│
├── nautilus-frontend/          # 前端 (Vue 3 + Tailwind CDN) 静态单页
│   └── index.html
│
├── start_media_cloud.bat      # Windows 一键启动脚本
└── elma-stream-worker/        # Python Worker 节点 (Python) ⭐ 数据面
    ├── main.py                # 核心异步工作节点
    ├── requirements.txt       # Python 依赖
    ├── config.template.py     # 配置模板
    ├── test_worker.py        # 自动化测试
    ├── start-worker.ps1      # Windows 启动脚本
    ├── start-worker.sh       # Linux/macOS 启动脚本
    ├── Dockerfile            # Docker 镜像
    ├── docker-compose.yml    # 多节点编排
    └── docs/                 # 完整文档
        ├── README.md
        ├── QUICKSTART.md
        ├── DEPLOYMENT.md
        └── STRUCTURE.md
```

## 🚀 核心特性

### 1. 高并发防重原子拉取
使用 PostgreSQL 的 `FOR UPDATE SKIP LOCKED` 特性:

```sql
UPDATE sys_media_task 
SET status = 'RUNNING', worker_node = #{workerNode}
WHERE task_id = (
    SELECT task_id FROM sys_media_task 
    WHERE status = 'PENDING' 
    ORDER BY created_at ASC 
    LIMIT 1 
    FOR UPDATE SKIP LOCKED
)
RETURNING *;
```

**技术优势:**
- ✅ 100% 防止并发争抢
- ✅ 无锁等待,零死锁风险
- ✅ 单条 SQL 原子性更新
- ✅ 支持百级并发节点同时拉取

### 2. JSONB 元数据映射
使用 MyBatis-Plus 的 `JacksonTypeHandler` 优雅处理 PostgreSQL JSONB:

```java
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> metaInfo;
```

支持复杂 JSON 结构存储与查询。

### 3. Yorushika 主题 Mock 数据
所有测试数据与异常提示均采用 Yorushika 元素:
- 🎵 又三郎 4K MV 抓取
- 🌙 夜行 - 任务拉取成功
- 🚨 思想犯 - 并发冲突警告

## 🛠️ 技术栈

### 控制面 (Java)

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 核心语言 |
| Spring Boot | 3.2.2 | 应用框架 |
| MyBatis-Plus | 3.5.5 | ORM 增强 |
| PostgreSQL | 15+ | 数据库 |
| Lombok | Latest | 代码简化 |

### 数据面 (Python)

| 技术 | 版本 | 说明 |
|------|------|------|
| Python | 3.10+ | 核心语言 |
| httpx | 0.27.0 | 异步 HTTP 客户端 |
| asyncio | 内置 | 事件循环 |
| yt-dlp | 2024.8.6 | 流媒体提取 |

## 📡 API 接口

### 1. 拉取待处理任务
```http
GET /api/v1/tasks/pending?workerNode=worker-node-01
```

**响应示例:**
```json
{
  "code": 200,
  "msg": "夜行 - 任务拉取成功",
  "data": {
    "taskId": 1001,
    "taskName": "又三郎 4K MV 抓取",
    "targetUrl": "https://youtube.com/watch?v=...",
    "status": "RUNNING",
    "workerNode": "worker-node-01",
    "metaInfo": {
      "artist": "ヨルシカ",
      "resolution": "4K"
    }
  }
}
```

### 2. 回报任务状态
```http
PUT /api/v1/tasks/{taskId}/status
Content-Type: application/json

{
  "status": "SUCCESS",
  "errorLog": "春泥棒 - 网络超时"
}
```

**响应:**
```json
{
  "code": 200,
  "msg": "夜行 - 节点状态更新成功"
}
```

### 3. 创建新任务
```http
POST /api/v1/tasks
Content-Type: application/json

{
  "taskName": "又三郎 4K MV 抓取",
  "targetUrl": "https://youtube.com/watch?v=xxx",
  "metaInfo": {
    "artist": "ヨルシカ",
    "resolution": "4K"
  }
}
```

### 4. 健康检查
```http
GET /api/v1/tasks/health
```

## 🗄️ 数据库部署

### 1. 创建数据库
```bash
createdb nautilus_dispatch
```

### 2. 执行建表脚本
```bash
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/schema.sql
```

### 3. 初始化测试数据
```bash
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/init-data.sql
```

## 🔧 配置说明与隐私

- **数据库密码**：请设置环境变量 `DB_PASSWORD`，或在 `amy-dispatch-center/src/main/resources/application.yml` 中修改 `password: your_postgres_password`，**勿将真实密码提交到仓库**。
- **API 鉴权 Token**：后端通过 `nautilus.auth.token`（或环境变量 `NAUTILUS_AUTH_TOKEN`）校验请求；Worker 使用环境变量 `NAUTILUS_AUTH_TOKEN` 或 `config.py` 中的 `AUTH_TOKEN`；前端在登录页填写并保存到本地。三处需保持一致，默认开发值为 `change-me`。
- 更多部署步骤见 [DEPLOYMENT.md](./DEPLOYMENT.md)。

## 🏃 运行项目

### 方式一: 启动 Java 调度中心 (控制面)

#### 1. 安装依赖
```bash
# 先安装 ruoyi-common
cd ruoyi-common
mvn clean install

# 再构建调度中心
cd ../amy-dispatch-center
mvn clean package
```

#### 2. 启动服务
```bash
mvn spring-boot:run
```

或直接运行:
```bash
java -jar target/amy-dispatch-center-1.0.0.jar
```

启动成功后访问: `http://localhost:8080/api/v1/tasks/health`

---

### 方式二: 启动 Python Worker 节点 (数据面)

#### 快速启动 (3 分钟)

**Windows:**
```powershell
cd elma-stream-worker
pip install -r requirements.txt
.\start-worker.ps1
```

**Linux/macOS:**
```bash
cd elma-stream-worker
pip3 install -r requirements.txt
chmod +x start-worker.sh
./start-worker.sh
```

#### 或直接运行:
```bash
python main.py
```

查看详细文档: [elma-stream-worker/QUICKSTART.md](./elma-stream-worker/QUICKSTART.md)

---

### 完整运行流程

1. ✅ 启动 PostgreSQL 数据库
2. ✅ 启动 Java 调度中心 (端口 8080)
3. ✅ 启动 Python Worker 节点 (可多节点)
4. ✅ 创建测试任务并观察自动执行

## 🧪 并发测试

使用 curl 模拟多节点并发拉取:

```bash
# 节点1拉取
curl "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-01"

# 节点2拉取
curl "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-02"

# 节点3拉取
curl "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-03"
```

每个节点将获取不同的任务,无重复分配。

## 📊 数据库索引策略

| 索引名 | 类型 | 说明 |
|--------|------|------|
| `idx_status_created` | Partial Index | 只索引 PENDING 任务,优化拉取性能 |
| `idx_worker_node` | B-Tree | 加速按节点查询历史任务 |
| `idx_meta_info` | GIN | 支持 JSONB 字段高级查询 |

## 🎯 状态流转

```
PENDING (待处理)
    ↓
  [节点拉取]
    ↓
RUNNING (执行中)
    ↓
  [执行结果]
    ↓
SUCCESS / FAILED (成功/失败)
```

## 📝 日志规范

所有日志使用 Yorushika 歌曲名作为前缀:

- ✅ **夜行** - 操作成功
- ⚠️ **思想犯** - 业务警告/并发冲突
- ❌ **春泥棒** - 任务失败
- 🎵 **又三郎** - 数据不存在

## 🤝 贡献者

Nautilus Media Cloud Team

## 📄 许可证

MIT License

---

> **夜行** - 愿每个任务都能在黑夜中找到归途 🌙
