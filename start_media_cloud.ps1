$ErrorActionPreference = "Stop"

$BackendPort = 8081
$FrontendPort = 5174
$DbPort = 5433
$AuthToken = if ($env:NAUTILUS_AUTH_TOKEN) { $env:NAUTILUS_AUTH_TOKEN } else { "changeme" }
$env:NAUTILUS_AUTH_TOKEN = $AuthToken

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "amy-dispatch-center"
$FrontendDir = Join-Path $Root "nautilus-frontend"
$WorkerDir = Join-Path $Root "elma-stream-worker"
$SharedDownloadDir = Join-Path $BackendDir "downloads"
$env:NAUTILUS_DOWNLOAD_BASE_DIR = $SharedDownloadDir
$env:NAUTILUS_DOWNLOAD_DIR = $SharedDownloadDir
$env:NAUTILUS_API_BASE_URL = "http://localhost:$BackendPort/api/v1/tasks"
$JarPath = Join-Path $BackendDir "target\amy-dispatch-center-1.0.0.jar"

$status = @{
    Backend = "UNKNOWN"
    Frontend = "UNKNOWN"
    Worker = "UNKNOWN"
}

function Write-Step {
    param([string]$Text)
    Write-Host $Text -ForegroundColor Cyan
}

function Write-Info {
    param([string]$Text)
    Write-Host "  - $Text" -ForegroundColor Gray
}

function Write-Ok {
    param([string]$Text)
    Write-Host "  - $Text" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Text)
    Write-Host "  - $Text" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Text)
    Write-Host "  - $Text" -ForegroundColor Red
}

function Test-PortListening {
    param([int]$Port)
    $pattern = ":{0}\s+.*LISTENING" -f $Port
    return [bool](netstat -ano 2>$null | Select-String -Pattern $pattern)
}

function Assert-PortAvailable {
    param([int]$Port, [string]$Service)
    if (Test-PortListening $Port) {
        throw "$Service 端口 $Port 已被占用。请检查占用进程后自行处理；启动脚本不会结束现有进程。"
    }
}

function Ensure-Database {
    if (-not (Test-PortListening $DbPort)) {
        throw "独立开发 PostgreSQL 未监听端口 $DbPort。请按 docs/LOCAL_SETUP.md 手动准备本项目数据库；启动脚本不会启动或修改现有数据库服务。"
    }
    Write-Ok "PostgreSQL 端口 $DbPort 已监听"
}

function Start-Backend {
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$BackendDir`" && echo [Media-Backend] Maven Spring Boot 启动中... && mvn spring-boot:run" `
            -WindowStyle Minimized | Out-Null
        $status.Backend = "BOOTING_MAVEN"
        Write-Ok "后端已启动（Maven 模式），端口 $BackendPort"
        return
    }

    if (Test-Path $JarPath) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$BackendDir`" && echo [Media-Backend] JAR 启动中... && java -jar target\amy-dispatch-center-1.0.0.jar" `
            -WindowStyle Minimized | Out-Null
        $status.Backend = "BOOTING_JAR"
        Write-Ok "后端已启动（JAR 模式），端口 $BackendPort"
        return
    }

    throw "未检测到 mvn，且未找到可执行 JAR：$JarPath"
}

function Wait-BackendReady {
    Write-Info "检查后端健康状态..."
    $headers = @{ Authorization = "Bearer $AuthToken" }
    for ($i = 0; $i -lt 20; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/v1/tasks/health" -Headers $headers -Method GET -TimeoutSec 4
            if ($resp.code -eq 200 -and $resp.status -eq "RUNNING" -and $resp.database -eq "UP") {
                $status.Backend = "READY"
                Write-Ok "后端健康检查通过"
                return
            }
        } catch {
            # keep waiting
        }
        Start-Sleep -Seconds 1
    }
    $status.Backend = "TIMEOUT"
    throw "后端未在预期时间内就绪，请检查 Media-Backend 窗口日志。"
}

function Start-Frontend {
    if (Get-Command python -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$FrontendDir`" && echo [Media-Frontend] Python HTTP 服务 0.0.0.0:$FrontendPort ... && python -m http.server $FrontendPort" `
            -WindowStyle Minimized | Out-Null
        $status.Frontend = "BOOTING_PYTHON"
        Write-Ok "前端已启动（Python 模式），端口 $FrontendPort"
        return
    }

    if (Get-Command npx -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$FrontendDir`" && echo [Media-Frontend] npx serve 启动中... && npx -y serve -s . -l $FrontendPort" `
            -WindowStyle Minimized | Out-Null
        $status.Frontend = "BOOTING_NPX"
        Write-Ok "前端已启动（npx 模式），端口 $FrontendPort"
        return
    }

    throw "未检测到 python 或 npx，无法启动前端静态服务。"
}

function Wait-FrontendReady {
    Write-Info "检查前端端口监听..."
    for ($i = 0; $i -lt 10; $i++) {
        if (Test-PortListening $FrontendPort) {
            $status.Frontend = "READY"
            Write-Ok "前端端口监听正常"
            return
        }
        Start-Sleep -Seconds 1
    }
    $status.Frontend = "TIMEOUT"
    throw "前端端口 $FrontendPort 未监听，请检查 Media-Frontend 窗口日志。"
}

function Start-Worker {
    $workerMain = Join-Path $WorkerDir "main.py"
    if (-not (Test-Path $workerMain)) {
        $status.Worker = "MISSING_MAIN"
        Write-Warn "未找到 Worker 主程序，跳过 Worker 启动"
        return
    }

    if (Get-Command python -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$WorkerDir`" && echo [Media-Worker] Worker 启动中... && python main.py" `
            -WindowStyle Minimized | Out-Null
        $status.Worker = "BOOTING"
        Write-Ok "Worker 已启动（python main.py）"
        return
    }

    if (Get-Command py -ErrorAction SilentlyContinue) {
        Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/k", "cd /d `"$WorkerDir`" && echo [Media-Worker] Worker 启动中... && py -3 main.py" `
            -WindowStyle Minimized | Out-Null
        $status.Worker = "BOOTING"
        Write-Ok "Worker 已启动（py -3 main.py）"
        return
    }

    $status.Worker = "NO_PYTHON"
    Write-Warn "未检测到 python/py，跳过 Worker 启动"
}

function Wait-WorkerReady {
    if ($status.Worker -in @("MISSING_MAIN", "NO_PYTHON")) {
        return
    }

    Write-Info "检查 Worker 在线状态..."
    $headers = @{ Authorization = "Bearer $AuthToken" }
    for ($i = 0; $i -lt 15; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://127.0.0.1:$BackendPort/api/v1/tasks/stats" -Headers $headers -Method GET -TimeoutSec 4
            if ($resp.code -eq 200 -and $resp.data.onlineWorkers -ge 1) {
                $status.Worker = "READY"
                Write-Ok "Worker 已在线（onlineWorkers >= 1）"
                return
            }
        } catch {
            # keep waiting
        }
        Start-Sleep -Seconds 1
    }

    if ($status.Worker -eq "BOOTING") {
        $status.Worker = "OFFLINE"
    }
    Write-Warn "Worker 未在线，任务会停留在 PENDING。请查看 Media-Worker 窗口日志。"
}

try {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  Nautilus · 任务调度示例 - 一键启动" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""

    Write-Step "[1/8] 检查端口..."
    Assert-PortAvailable $BackendPort "后端"
    Assert-PortAvailable $FrontendPort "前端"
    Write-Host ""

    Write-Step "[2/8] 检查 PostgreSQL..."
    Ensure-Database
    Write-Host ""

    Write-Step "[3/8] 启动后端..."
    Start-Backend
    Wait-BackendReady
    Write-Host ""

    Write-Step "[4/8] 等待后端初始化..."
    Start-Sleep -Seconds 5
    Write-Host ""

    Write-Step "[5/8] 启动前端静态服务..."
    Start-Frontend
    Wait-FrontendReady
    Write-Host ""

    Write-Step "[6/8] 启动 Worker..."
    Start-Worker
    Wait-WorkerReady
    Write-Host ""

    Write-Step "[7/8] 等待服务就绪..."
    Start-Sleep -Seconds 2
    Write-Host ""

    Write-Step "[8/8] 打开浏览器..."
    try {
        Start-Process "http://localhost:$FrontendPort" | Out-Null
    } catch {
        Write-Warn "浏览器自动打开失败，请手动访问: http://localhost:$FrontendPort"
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  启动完成" -ForegroundColor Green
    Write-Host "  后端: http://localhost:$BackendPort/api/v1/tasks" -ForegroundColor Green
    Write-Host "  前端: http://localhost:$FrontendPort" -ForegroundColor Green
    Write-Host ("  就绪状态: Backend={0} Frontend={1} Worker={2}" -f $status.Backend, $status.Frontend, $status.Worker) -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    exit 0
} catch {
    Write-Host ""
    Write-Err ("启动失败: {0}" -f $_.Exception.Message)
    Write-Err ("当前状态: Backend={0} Frontend={1} Worker={2}" -f $status.Backend, $status.Frontend, $status.Worker)
    Write-Host ""
    exit 1
}
