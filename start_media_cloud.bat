@echo off
chcp 65001 >nul
setlocal

rem 无论从双击还是其他目录调用，先切换到脚本所在目录
pushd "%~dp0"
if errorlevel 1 (
    echo [ERROR] 无法进入目录: %~dp0
    pause
    exit /b 1
)

set "PS_SCRIPT=%~dp0start_media_cloud.ps1"
if not exist "%PS_SCRIPT%" (
    echo [ERROR] 启动脚本缺失: %PS_SCRIPT%
    popd
    if "%NO_PAUSE%"=="1" exit /b 1
    pause
    exit /b 1
)

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到 powershell.exe，请确认已安装 Windows PowerShell。
    popd
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"
set "EXIT_CODE=%errorlevel%"

popd

if "%NO_PAUSE%"=="1" exit /b %EXIT_CODE%
if %EXIT_CODE% neq 0 (
    echo.
    echo 启动失败（exit %EXIT_CODE%），请根据上方日志修复后重试。
    pause
)
exit /b %EXIT_CODE%
