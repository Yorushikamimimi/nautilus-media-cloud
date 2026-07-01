# 本地启动指南

## 端口分配（避免与 rag-nexus 冲突）

| 服务 | 端口 | 原项目端口 |
|------|------|------------|
| 后端 Spring Boot | **8081** | rag-nexus 用 8080 |
| 前端静态页 | **5174** | rag-nexus 用 5173 |
| PostgreSQL | **5433** | rag-nexus 已有容器 |

## 前置条件

- Docker（PostgreSQL 容器 `rag-nexus-pg`）
- Java 17+ / Maven
- Python 3.10+（仅启动 Worker 时需要）

## 启动步骤

### 1. 启动数据库

```bash
docker start rag-nexus-pg
```

首次使用需建库和初始化表（只需执行一次）：

```bash
docker exec rag-nexus-pg psql -U postgres -c "CREATE DATABASE nautilus_dispatch;"
docker exec -i rag-nexus-pg psql -U postgres -d nautilus_dispatch \
  < amy-dispatch-center/src/main/resources/db/schema.sql
docker exec -i rag-nexus-pg psql -U postgres -d nautilus_dispatch \
  < amy-dispatch-center/src/main/resources/db/init-data.sql
```

### 2. 编译公共模块

```bash
cd ruoyi-common && mvn clean install -q && cd ..
```

### 3. 启动后端（8081）

```bash
cd amy-dispatch-center && mvn spring-boot:run
```

启动成功标志：
```
🎵 Amy Dispatch Center 启动成功
API Base URL: http://localhost:8081/api/v1/tasks
```

### 4. 启动前端（5174）

```bash
cd nautilus-frontend && python3 -m http.server 5174
```

打开 http://localhost:5174，登录口令：`changeme`

### 5. 启动 Worker（可选）

```bash
cd elma-stream-worker
pip install -r requirements.txt
export NAUTILUS_AUTH_TOKEN=changeme
python main.py
```

## 验证

```bash
# 健康检查
curl -s -H "Authorization: Bearer changeme" http://localhost:8081/api/v1/tasks/health | python3 -m json.tool

# 任务列表
curl -s -H "Authorization: Bearer changeme" http://localhost:8081/api/v1/tasks/list | python3 -m json.tool
```

## 关闭

```bash
# 后端
pkill -f "spring-boot:run"
# 前端
pkill -f "http.server 5174"
# 数据库（如需）
docker stop rag-nexus-pg
```
