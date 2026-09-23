# 本地启动指南

## 默认端口

| 服务 | 默认端口 | 覆盖方式 |
|------|----------|----------|
| Spring Boot 调度中心 | 8081 | `SERVER_PORT` |
| Vue 静态页 | 5174（下方示例） | 启动静态服务器时指定 |
| PostgreSQL | 5433 | `SPRING_DATASOURCE_URL` |

这些默认值与 `rag-nexus` 分开。数据库需要是本项目自己的 PostgreSQL 数据库；不要直接复用其他项目的容器或数据库。

## 前置条件

- Java 17+、Maven
- PostgreSQL 16，可使用独立 Docker 容器
- Python 3.10+（只在运行 Worker 时需要）

## 1. 准备独立开发数据库

下面创建独立容器 `nautilus-dispatch-pg`，宿主机端口为 5433。若同名容器已存在，先检查确认归属，不要直接删除或覆盖。

```bash
docker run --name nautilus-dispatch-pg \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=nautilus_dispatch \
  -p 5433:5432 \
  -d postgres:16
```

首次初始化只对这个新建的开发数据库执行：

```bash
psql "postgresql://postgres:postgres@localhost:5433/nautilus_dispatch" \
  -f amy-dispatch-center/src/main/resources/db/schema.sql
psql "postgresql://postgres:postgres@localhost:5433/nautilus_dispatch" \
  -f amy-dispatch-center/src/main/resources/db/init-data.sql
```

`schema.sql` 会删除并重建 `sys_media_task`。只能对空的、专用的开发数据库执行，不能对已有数据的数据库执行。

## 2. 编译并启动后端

```bash
cd ruoyi-common && mvn clean install && cd ../amy-dispatch-center && mvn spring-boot:run
```

后端默认连接 `localhost:5433/nautilus_dispatch`，监听 8081。若用其他数据库连接，请设置 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`。

## 3. 启动前端

```bash
cd nautilus-frontend && python3 -m http.server 5174
```

打开 <http://localhost:5174>。默认本地演示口令为 `changeme`，可通过 `NAUTILUS_AUTH_TOKEN` 覆盖后端和 Worker 的 API token；前端登录 token 需要与之匹配。

## 4. 启动 Worker（可选）

```bash
cd elma-stream-worker
pip install -r requirements.txt
python main.py
```

默认 API 地址为 `http://localhost:8081/api/v1/tasks`。可用环境变量覆盖：

- `NAUTILUS_API_BASE_URL`：调度中心 API 根地址。
- `NAUTILUS_AUTH_TOKEN`：Bearer token，需与后端一致。
- `NAUTILUS_WORKER_ID`：固定 Worker 标识；未设置时使用“主机名-进程号”，同机并行实例可区分。
- `NAUTILUS_DOWNLOAD_DIR`：Worker 下载目录。
- `NAUTILUS_DOWNLOAD_BASE_DIR`：后端允许读取的下载目录。

按以上仓库目录启动时，Worker 与后端默认共享 `amy-dispatch-center/downloads`。跨机器运行时需把同一共享存储挂载到两端，并分别将 Worker 的 `NAUTILUS_DOWNLOAD_DIR` 与后端的 `NAUTILUS_DOWNLOAD_BASE_DIR` 指向该共享路径。

Windows 可用仓库根目录的 `start_media_cloud.ps1` 快速启动；它会设置共享下载目录，并默认端口已被占用时会退出并提示，不会结束已有进程。该脚本不会创建或初始化数据库。

## 验证接口

```bash
curl -s -H "Authorization: Bearer changeme" \
  http://localhost:8081/api/v1/tasks/health | python3 -m json.tool
curl -s -H "Authorization: Bearer changeme" \
  http://localhost:8081/api/v1/tasks/list | python3 -m json.tool
```

## 清理本指南创建的开发容器

```bash
docker stop nautilus-dispatch-pg
docker rm nautilus-dispatch-pg
```

只对本指南新建并确认属于本项目的容器执行清理。
