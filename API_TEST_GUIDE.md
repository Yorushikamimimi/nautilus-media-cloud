# API 手工检查

本页仅提供当前 API 的请求格式。默认本地地址为 `http://localhost:8081/api/v1/tasks`。本页列出的任务 API 请求需使用 `Authorization: Bearer <NAUTILUS_AUTH_TOKEN>`，本地默认 token 是 `changeme`。SSE `/stream` 支持用 `?token=` 传入 token；下载路由不经过该拦截器。本页请求会读写实际配置的数据库；请使用独立开发库。

```bash
export BASE_URL="http://localhost:8081/api/v1/tasks"
export NAUTILUS_AUTH_TOKEN="${NAUTILUS_AUTH_TOKEN:-changeme}"
export WORKER_NODE="$(hostname)-manual-check"
```

## 健康检查与查询

```bash
curl -H "Authorization: Bearer $NAUTILUS_AUTH_TOKEN" "$BASE_URL/health"
curl -H "Authorization: Bearer $NAUTILUS_AUTH_TOKEN" "$BASE_URL/list"
```

## 创建任务并领取

创建任务会写入数据库；领取任务会把一条 `PENDING` 任务改为 `RUNNING`。只在专用开发库中操作：

```bash
curl -X POST "$BASE_URL" \
  -H "Authorization: Bearer $NAUTILUS_AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"taskName":"本地测试任务","targetUrl":"https://example.com/media","metaInfo":{}}'

curl -H "Authorization: Bearer $NAUTILUS_AUTH_TOKEN" \
  "$BASE_URL/pending?workerNode=$WORKER_NODE"
```

领取响应中的 `workerNode` 和 `claimVersion` 必须原样用于后续回调。无任务时，HTTP 仍为 200，JSON 业务 `code` 为 204。

## 回报状态

状态回调会改数据库状态。将 `<taskId>`、`<workerNode>`、`<claimVersion>` 替换为同一次领取响应里的值：

```bash
curl -X PUT "$BASE_URL/<taskId>/status" \
  -H "Authorization: Bearer $NAUTILUS_AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"SUCCESS","workerNode":"<workerNode>","claimVersion":<claimVersion>}'
```

`FAILED` 回调使用相同的两个领取字段并附带 `errorLog`。旧领取轮次的回调会被拒绝。不要只填 task ID，也不要用已经重试或回收过的旧领取信息。

并发领取、失败退避、超时回收及旧回调行为由后端 PostgreSQL 集成测试覆盖；本页请求示例不构成运行验收，也不应在共享或生产数据库上执行。
