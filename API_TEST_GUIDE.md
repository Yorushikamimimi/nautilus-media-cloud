# API 接口测试脚本集合

## 🧪 使用 curl 测试所有接口

### 环境变量设置
```bash
export BASE_URL="http://localhost:8080/api/v1/tasks"
export WORKER_NODE="worker-node-test-01"
```

---

## 1️⃣ 健康检查

```bash
# 测试服务是否正常运行
curl -X GET "${BASE_URL}/health" | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "夜行 - 调度中心运行正常",
#   "service": "Amy Dispatch Center",
#   "theme": "ヨルシカ (Yorushika)",
#   "status": "RUNNING"
# }
```

---

## 2️⃣ 拉取待处理任务

### 测试 1: 正常拉取
```bash
curl -X GET "${BASE_URL}/pending?workerNode=${WORKER_NODE}" | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "夜行 - 任务拉取成功",
#   "data": {
#     "taskId": 1,
#     "taskName": "又三郎 4K MV 抓取",
#     "targetUrl": "https://youtube.com/watch?v=matasaburo_4k",
#     "status": "RUNNING",
#     "workerNode": "worker-node-test-01",
#     "metaInfo": { ... }
#   }
# }
```

### 测试 2: 无可用任务
```bash
# 拉取所有任务后再次请求
curl -X GET "${BASE_URL}/pending?workerNode=worker-no-task" | jq

# 预期响应:
# {
#   "code": 204,
#   "msg": "思想犯 - 当前无待处理任务",
#   "data": null
# }
```

### 测试 3: 参数校验
```bash
# 缺少 workerNode 参数
curl -X GET "${BASE_URL}/pending" | jq

# 预期响应: 400 参数校验失败
```

---

## 3️⃣ 查询任务详情

```bash
# 查询 ID 为 1 的任务
curl -X GET "${BASE_URL}/1" | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "夜行 - 查询成功",
#   "data": {
#     "taskId": 1,
#     "taskName": "又三郎 4K MV 抓取",
#     ...
#   }
# }
```

```bash
# 查询不存在的任务
curl -X GET "${BASE_URL}/99999" | jq

# 预期响应:
# {
#   "code": 500,
#   "msg": "又三郎 - 任务不存在"
# }
```

---

## 4️⃣ 创建新任务

### 测试 1: 创建基础任务
```bash
curl -X POST "${BASE_URL}" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "言って Live Tour 2024",
    "targetUrl": "https://youtube.com/watch?v=itte_live_2024",
    "metaInfo": {
      "artist": "ヨルシカ",
      "song": "言って",
      "type": "live",
      "resolution": "4K"
    }
  }' | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "夜行 - 任务创建成功",
#   "data": {
#     "taskId": 11,
#     "taskName": "言って Live Tour 2024",
#     "status": "PENDING",
#     ...
#   }
# }
```

### 测试 2: 创建复杂 JSONB 元数据任务
```bash
curl -X POST "${BASE_URL}" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "盗作专辑完整版",
    "targetUrl": "https://youtube.com/watch?v=tousaku_full",
    "metaInfo": {
      "artist": "ヨルシカ",
      "album": "盗作",
      "tracks": ["又三郎", "夜行", "思想犯", "春泥棒"],
      "metadata": {
        "year": 2019,
        "label": "Universal Music Japan",
        "duration": "42:30"
      }
    }
  }' | jq
```

### 测试 3: 参数校验失败
```bash
# 缺少 taskName
curl -X POST "${BASE_URL}" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUrl": "https://youtube.com/watch?v=test"
  }' | jq

# 预期响应:
# {
#   "code": 500,
#   "msg": "思想犯 - 任务名称不能为空"
# }
```

---

## 5️⃣ 回报任务状态

### 测试 1: 任务成功完成
```bash
curl -X PUT "${BASE_URL}/1/status" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "SUCCESS"
  }' | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "夜行 - 节点状态更新成功"
# }
```

### 测试 2: 任务执行失败
```bash
curl -X PUT "${BASE_URL}/2/status" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "FAILED",
    "errorLog": "春泥棒 - 网络超时导致下载失败,已重试3次"
  }' | jq

# 预期响应:
# {
#   "code": 200,
#   "msg": "春泥棒 - 任务失败状态已记录"
# }
```

### 测试 3: 无效状态值
```bash
curl -X PUT "${BASE_URL}/3/status" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "INVALID_STATUS"
  }' | jq

# 预期响应:
# {
#   "code": 500,
#   "msg": "思想犯 - 任务状态只能为 SUCCESS 或 FAILED"
# }
```

---

## 🔥 并发拉取测试脚本

### Bash 脚本: 模拟 10 个节点并发拉取

创建文件 `test_concurrent_pull.sh`:

```bash
#!/bin/bash

# 配置
BASE_URL="http://localhost:8080/api/v1/tasks"
CONCURRENT_NODES=10

echo "🎵 Yorushika 主题并发拉取测试"
echo "========================================"
echo "开始时间: $(date)"
echo "并发节点数: ${CONCURRENT_NODES}"
echo ""

# 并发拉取
for i in $(seq 1 $CONCURRENT_NODES); do
  WORKER_NODE=$(printf "worker-node-%02d" $i)
  echo "[$i] 节点 ${WORKER_NODE} 开始拉取..."
  
  curl -s -X GET "${BASE_URL}/pending?workerNode=${WORKER_NODE}" \
    | jq -c '{code: .code, msg: .msg, taskId: .data.taskId, taskName: .data.taskName}' &
done

# 等待所有请求完成
wait

echo ""
echo "========================================"
echo "结束时间: $(date)"
echo "✅ 并发测试完成"
```

运行:
```bash
chmod +x test_concurrent_pull.sh
./test_concurrent_pull.sh
```

### PowerShell 脚本 (Windows)

创建文件 `test_concurrent_pull.ps1`:

```powershell
# 配置
$BaseUrl = "http://localhost:8080/api/v1/tasks"
$ConcurrentNodes = 10

Write-Host "🎵 Yorushika 主题并发拉取测试" -ForegroundColor Cyan
Write-Host "========================================"
Write-Host "开始时间: $(Get-Date)"
Write-Host "并发节点数: $ConcurrentNodes"
Write-Host ""

# 并发拉取
$jobs = @()
for ($i = 1; $i -le $ConcurrentNodes; $i++) {
    $workerNode = "worker-node-{0:D2}" -f $i
    Write-Host "[$i] 节点 $workerNode 开始拉取..."
    
    $job = Start-Job -ScriptBlock {
        param($url, $worker)
        $response = Invoke-RestMethod -Uri "$url/pending?workerNode=$worker" -Method Get
        return @{
            code = $response.code
            msg = $response.msg
            taskId = $response.data.taskId
            taskName = $response.data.taskName
        }
    } -ArgumentList $BaseUrl, $workerNode
    
    $jobs += $job
}

# 等待所有任务完成
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

# 显示结果
$results | Format-Table -AutoSize

Write-Host ""
Write-Host "========================================"
Write-Host "结束时间: $(Get-Date)"
Write-Host "✅ 并发测试完成" -ForegroundColor Green
```

运行:
```powershell
.\test_concurrent_pull.ps1
```

---

## 🧪 完整流程测试

### 测试场景: 任务完整生命周期

```bash
#!/bin/bash

BASE_URL="http://localhost:8080/api/v1/tasks"

echo "🎵 测试任务完整生命周期"
echo "========================================"

# 1. 创建任务
echo "1️⃣ 创建任务..."
TASK_ID=$(curl -s -X POST "${BASE_URL}" \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "夜明けと蛍 Full MV",
    "targetUrl": "https://youtube.com/watch?v=yoake_hotaru",
    "metaInfo": {"artist": "ヨルシカ", "resolution": "1080p"}
  }' | jq -r '.data.taskId')

echo "✅ 任务创建成功, TaskID: ${TASK_ID}"
sleep 1

# 2. 拉取任务
echo ""
echo "2️⃣ 节点拉取任务..."
PULLED_TASK=$(curl -s -X GET "${BASE_URL}/pending?workerNode=test-worker")
echo "$PULLED_TASK" | jq '.data | {taskId, taskName, status}'
sleep 1

# 3. 查询任务详情
echo ""
echo "3️⃣ 查询任务详情..."
curl -s -X GET "${BASE_URL}/${TASK_ID}" | jq '.data | {taskId, taskName, status, workerNode}'
sleep 1

# 4. 回报成功
echo ""
echo "4️⃣ 回报任务成功..."
curl -s -X PUT "${BASE_URL}/${TASK_ID}/status" \
  -H "Content-Type: application/json" \
  -d '{"status":"SUCCESS"}' | jq '{code, msg}'

# 5. 验证最终状态
echo ""
echo "5️⃣ 验证最终状态..."
curl -s -X GET "${BASE_URL}/${TASK_ID}" | jq '.data | {taskId, taskName, status}'

echo ""
echo "========================================"
echo "✅ 完整流程测试完成"
```

---

## 📊 性能测试 (使用 Apache Bench)

### 测试 1: 健康检查接口
```bash
ab -n 1000 -c 10 http://localhost:8080/api/v1/tasks/health
```

### 测试 2: 并发拉取任务
```bash
ab -n 100 -c 10 "http://localhost:8080/api/v1/tasks/pending?workerNode=ab-test"
```

---

## 🔍 数据验证 SQL

### 查看任务统计
```sql
SELECT 
    status,
    COUNT(*) AS count,
    COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() AS percentage
FROM sys_media_task
GROUP BY status
ORDER BY count DESC;
```

### 查看工作节点任务分布
```sql
SELECT 
    worker_node,
    COUNT(*) AS task_count,
    STRING_AGG(task_name, ', ') AS tasks
FROM sys_media_task
WHERE worker_node IS NOT NULL
GROUP BY worker_node
ORDER BY task_count DESC;
```

### 验证无重复分配
```sql
-- 检查是否有任务被多个节点同时拉取 (应该返回 0 行)
SELECT task_id, status, COUNT(DISTINCT worker_node) AS node_count
FROM sys_media_task
WHERE status = 'RUNNING'
GROUP BY task_id, status
HAVING COUNT(DISTINCT worker_node) > 1;
```

---

## 📝 测试检查清单

- [ ] 健康检查接口返回 200
- [ ] 单节点可正常拉取任务
- [ ] 并发拉取无重复分配
- [ ] 无可用任务返回 204
- [ ] 任务状态可正常更新
- [ ] 错误日志正确记录
- [ ] JSONB 字段正确序列化
- [ ] 参数校验正常工作
- [ ] 异常响应包含 Yorushika 元素

---

> **夜行 - 测试脚本准备完毕,祝测试顺利!** 🎵
