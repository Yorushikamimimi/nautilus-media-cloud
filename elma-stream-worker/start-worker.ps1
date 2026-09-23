# Elma Stream Worker - 启动脚本 (PowerShell)
# 用于 Windows 环境快速启动 Python Worker 节点

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "🌙 Elma Stream Worker 启动器" -ForegroundColor Cyan
Write-Host "   Nautilus - Python Worker（数据面）" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Python 版本
Write-Host "[1/4] 检查 Python 环境..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    Write-Host "   ✓ $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "   ✗ Python 未安装或未添加到 PATH" -ForegroundColor Red
    Write-Host "   请访问 https://www.python.org/ 下载安装 Python 3.10+" -ForegroundColor Red
    exit 1
}

# 检查依赖是否已安装
Write-Host ""
Write-Host "[2/4] 检查依赖包..." -ForegroundColor Yellow
$packages = @("httpx", "yt-dlp")
$missingPackages = @()

foreach ($pkg in $packages) {
    $installed = pip show $pkg 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        $missingPackages += $pkg
        Write-Host "   ✗ $pkg 未安装" -ForegroundColor Red
    } else {
        Write-Host "   ✓ $pkg 已安装" -ForegroundColor Green
    }
}

# 如果有缺失的包,提示安装
if ($missingPackages.Count -gt 0) {
    Write-Host ""
    Write-Host "发现缺失依赖包,是否自动安装? (Y/N)" -ForegroundColor Yellow
    $response = Read-Host
    
    if ($response -eq "Y" -or $response -eq "y") {
        Write-Host ""
        Write-Host "[3/4] 安装依赖包..." -ForegroundColor Yellow
        pip install -r requirements.txt
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "   ✗ 依赖安装失败" -ForegroundColor Red
            exit 1
        }
        Write-Host "   ✓ 依赖安装完成" -ForegroundColor Green
    } else {
        Write-Host "   ✗ 用户取消安装,退出启动流程" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host ""
    Write-Host "[3/4] 依赖检查完成,所有包已就绪" -ForegroundColor Green
}

# 检查 Java 调度中心是否在线
Write-Host ""
Write-Host "[4/4] 检查 Java 调度中心连接..." -ForegroundColor Yellow
$ApiBaseUrl = if ($env:NAUTILUS_API_BASE_URL) { $env:NAUTILUS_API_BASE_URL } else { "http://localhost:8081/api/v1/tasks" }
$AuthToken = if ($env:NAUTILUS_AUTH_TOKEN) { $env:NAUTILUS_AUTH_TOKEN } else { "changeme" }
$HealthHeaders = @{ Authorization = "Bearer $AuthToken" }
try {
    $response = Invoke-WebRequest -Uri "$ApiBaseUrl/health" -Headers $HealthHeaders -TimeoutSec 3 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "   ✓ Java 调度中心在线" -ForegroundColor Green
    }
} catch {
    Write-Host "   ⚠ Java 调度中心未响应 ($ApiBaseUrl)" -ForegroundColor Yellow
    Write-Host "   请确保 amy-dispatch-center 已启动" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "是否继续启动 Worker? (Y/N)" -ForegroundColor Yellow
    $response = Read-Host
    
    if ($response -ne "Y" -and $response -ne "y") {
        Write-Host "   ✗ 用户取消启动" -ForegroundColor Red
        exit 1
    }
}

# 启动 Worker
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "🚀 启动 Elma Stream Worker..." -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示: 按 Ctrl+C 停止 Worker" -ForegroundColor Yellow
Write-Host ""

# 运行主程序
python main.py
