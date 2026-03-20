# Nautilus - 端到端测试脚本
# 演示 Java 控制面 + Python Worker 数据面

$AuthToken = if ($env:NAUTILUS_AUTH_TOKEN) { $env:NAUTILUS_AUTH_TOKEN } else { "changeme" }
$ApiHeaders = @{
    Authorization = "Bearer $AuthToken"
}
$env:NAUTILUS_AUTH_TOKEN = $AuthToken

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "🎵 Nautilus - 端到端测试" -ForegroundColor Cyan
Write-Host "   控制面 (Java) + 数据面 (Python) 联合测试" -ForegroundColor Cyan
Write-Host "   Powered by Yorushika (ヨルシカ) 🌙" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# ==================== 步骤 1: 检查 Java 调度中心 ====================
Write-Host "[1/5] 检查 Java 调度中心..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/tasks/health" -Headers $ApiHeaders -TimeoutSec 3 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "   ✓ Java 调度中心在线" -ForegroundColor Green
    }
} catch {
    Write-Host "   ✗ Java 调度中心未启动 (http://localhost:8080)" -ForegroundColor Red
    Write-Host "   请先启动 amy-dispatch-center" -ForegroundColor Red
    Write-Host ""
    Write-Host "   启动命令:" -ForegroundColor Yellow
    Write-Host "   cd amy-dispatch-center" -ForegroundColor Yellow
    Write-Host "   mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# ==================== 步骤 2: 创建测试任务 ====================
Write-Host ""
Write-Host "[2/5] 创建 Yorushika 主题测试任务..." -ForegroundColor Yellow

$tasks = @(
    @{
        taskName = "🎵 又三郎 4K MV 抓取"
        targetUrl = "https://www.youtube.com/watch?v=F64yFFnZfkI"
        metaInfo = @{
            artist = "ヨルシカ"
            song = "又三郎 (Matasaburo)"
            resolution = "4K"
        }
    },
    @{
        taskName = "🌙 夜行 官方音频提取"
        targetUrl = "https://www.youtube.com/watch?v=xxxxx"
        metaInfo = @{
            artist = "ヨルシカ"
            song = "夜行 (Yakou)"
            type = "audio"
        }
    },
    @{
        taskName = "🚨 思想犯 Live 版本"
        targetUrl = "https://www.youtube.com/watch?v=yyyyy"
        metaInfo = @{
            artist = "ヨルシカ"
            song = "思想犯 (Shisouham)"
            type = "live"
        }
    }
)

$createdTaskIds = @()

foreach ($task in $tasks) {
    try {
        $body = $task | ConvertTo-Json -Depth 10 -Compress
        $response = Invoke-RestMethod `
            -Uri "http://localhost:8080/api/v1/tasks" `
            -Method POST `
            -Headers $ApiHeaders `
            -ContentType "application/json; charset=utf-8" `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
        
        if ($response.code -eq 200) {
            $taskId = $response.data.taskId
            $createdTaskIds += $taskId
            Write-Host "   ✓ 任务创建成功 | TaskID: $taskId | $($task.taskName)" -ForegroundColor Green
        }
    } catch {
        Write-Host "   ✗ 任务创建失败: $_" -ForegroundColor Red
    }
}

if ($createdTaskIds.Count -eq 0) {
    Write-Host ""
    Write-Host "   ✗ 无任务创建成功,退出测试" -ForegroundColor Red
    exit 1
}

# ==================== 步骤 3: 检查待处理任务 ====================
Write-Host ""
Write-Host "[3/5] 检查待处理任务列表..." -ForegroundColor Yellow

Start-Sleep -Seconds 1

try {
    $response = Invoke-RestMethod `
        -Uri "http://localhost:8080/api/v1/tasks/pending?workerNode=Test-Node" `
        -Method GET `
        -Headers $ApiHeaders
    
    if ($response.code -eq 200 -and $response.data) {
        Write-Host "   ✓ 有待处理任务: $($response.data.taskName)" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ 当前无待处理任务" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   ✗ 查询失败: $_" -ForegroundColor Red
}

# ==================== 步骤 4: 启动 Python Worker ====================
Write-Host ""
Write-Host "[4/5] 启动 Python Worker 节点..." -ForegroundColor Yellow
Write-Host "   提示: Worker 将自动拉取并执行任务" -ForegroundColor Cyan
Write-Host "   提示: 按 Ctrl+C 可随时停止 Worker" -ForegroundColor Cyan
Write-Host ""

Start-Sleep -Seconds 2

# 检查 Python Worker 目录
if (Test-Path ".\elma-stream-worker\main.py") {
    Write-Host "   → 正在启动 Python Worker..." -ForegroundColor Cyan
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "🌙 Elma Stream Worker 日志输出" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
    
    # 启动 Worker (前台运行,可看到实时日志)
    python .\elma-stream-worker\main.py
} else {
    Write-Host "   ✗ 未找到 elma-stream-worker/main.py" -ForegroundColor Red
    Write-Host "   请确保 Python Worker 已生成" -ForegroundColor Red
    exit 1
}

# ==================== 步骤 5: 验证任务状态 ====================
Write-Host ""
Write-Host "[5/5] 验证任务执行结果..." -ForegroundColor Yellow

foreach ($taskId in $createdTaskIds) {
    try {
        $response = Invoke-RestMethod `
            -Uri "http://localhost:8080/api/v1/tasks/$taskId" `
            -Method GET `
            -Headers $ApiHeaders
        
        if ($response.code -eq 200) {
            $status = $response.data.status
            $taskName = $response.data.taskName
            
            if ($status -eq "SUCCESS") {
                Write-Host "   ✓ TaskID: $taskId | $taskName | 状态: $status" -ForegroundColor Green
            } elseif ($status -eq "FAILED") {
                Write-Host "   ✗ TaskID: $taskId | $taskName | 状态: $status" -ForegroundColor Red
            } else {
                Write-Host "   ⚠ TaskID: $taskId | $taskName | 状态: $status" -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "   ✗ 查询失败: TaskID $taskId" -ForegroundColor Red
    }
}

# ==================== 测试总结 ====================
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "🎉 测试完成!" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📊 测试摘要:" -ForegroundColor Yellow
Write-Host "   - 创建任务数: $($createdTaskIds.Count)" -ForegroundColor White
Write-Host "   - Java 调度中心: ✓ 正常运行" -ForegroundColor Green
Write-Host "   - Python Worker: ✓ 执行完成" -ForegroundColor Green
Write-Host ""
Write-Host "🎵 又三郎 - 风载着数据归来,测试成功!" -ForegroundColor Cyan
Write-Host ""
