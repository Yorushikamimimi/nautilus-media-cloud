# 并发拉取测试脚本 - Yorushika 主题
# 用法: .\test_concurrent_pull.ps1

# 控制台输出 UTF-8，避免中文乱码
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 配置（与后端 NAUTILUS_AUTH_TOKEN / 默认 changeme 一致）
$BaseUrl = "http://localhost:8080/api/v1/tasks"
$ConcurrentNodes = 10
$AuthToken = if ($env:NAUTILUS_AUTH_TOKEN) { $env:NAUTILUS_AUTH_TOKEN } else { "changeme" }

Write-Host "Yorushika 主题并发拉取测试" -ForegroundColor Cyan
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
        param($url, $worker, $token)
        try {
            $headers = @{ Authorization = "Bearer $token" }
            $response = Invoke-RestMethod -Uri "$url/pending?workerNode=$worker" -Method Get -Headers $headers -ErrorAction Stop
            return [PSCustomObject]@{
                worker   = $worker
                code     = $response.code
                msg      = $response.msg
                taskId   = if ($response.data) { $response.data.taskId } else { $null }
                taskName = if ($response.data) { $response.data.taskName } else { $null }
                ok       = $true
                error    = $null
            }
        } catch {
            $statusCode = $null
            if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
            return [PSCustomObject]@{
                worker   = $worker
                code     = $statusCode
                msg      = $_.Exception.Message
                taskId   = $null
                taskName = $null
                ok       = $false
                error    = $_.Exception.Message
            }
        }
    } -ArgumentList $BaseUrl, $workerNode, $AuthToken

    $jobs += $job
}

# 等待所有任务完成
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

# 显示结果
Write-Host ""
Write-Host "======== 拉取结果 ========" -ForegroundColor Yellow
$results | Format-Table -AutoSize -Property worker, code, msg, taskId, taskName

$successCount = ($results | Where-Object { $_.ok -and $_.taskId }).Count
$failCount = ($results | Where-Object { -not $_.ok }).Count
$noTaskCount = ($results | Where-Object { $_.ok -and -not $_.taskId }).Count

Write-Host "统计: 成功拉取=$successCount, 无任务=$noTaskCount, 请求失败(500等)=$failCount" -ForegroundColor Cyan
Write-Host "========================================"
Write-Host "结束时间: $(Get-Date)"
Write-Host "并发测试完成" -ForegroundColor Green
