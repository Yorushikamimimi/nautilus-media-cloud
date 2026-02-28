@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: ==================== 配置区（端口与路径） ====================
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=5173"
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

:: ==================== 极客风 Banner ====================
echo.
echo  ═══════════════════════════════════════════════════
echo    Nautilus Media Cloud — 一键点火脚本
echo  ═══════════════════════════════════════════════════
echo.

:: ==================== 1. 进程探针与清理 (Kill Zombies) ====================
echo [1/6] 释放底层端口，扫描僵尸进程...

:: 根据端口揪出 PID 并强杀（后端端口）
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%BACKEND_PORT% " ^| findstr "LISTENING"') do (
    echo     ^> 发现后端端口 %BACKEND_PORT% 被占用，清理 PID: %%a
    taskkill /F /PID %%a 2>nul
)

:: 根据端口揪出 PID 并强杀（前端端口）
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":%FRONTEND_PORT% " ^| findstr "LISTENING"') do (
    echo     ^> 发现前端端口 %FRONTEND_PORT% 被占用，清理 PID: %%a
    taskkill /F /PID %%a 2>nul
)

:: 短暂冷却，确保端口完全释放
timeout /t 2 /nobreak >nul
echo     ^> 端口探针完成，底层已就绪。
echo.

:: ==================== 2. 异步启动后端 (Backend Ignition) ====================
echo [2/6] 异步挂载调度中心 (Spring Boot)...

set "BACKEND_DIR=%ROOT%\amy-dispatch-center"
set "JAR_PATH=%BACKEND_DIR%\target\amy-dispatch-center-1.0.0.jar"

if exist "%JAR_PATH%" (
    :: 已有 JAR，直接 java -jar 点火
    start "Media-Backend" /MIN cmd /k "cd /d "%BACKEND_DIR%" && echo [Media-Backend] 调度中心 JAR 启动中... && java -jar target\amy-dispatch-center-1.0.0.jar"
) else (
    :: 未打包则用 Maven 内联运行
    start "Media-Backend" /MIN cmd /k "cd /d "%BACKEND_DIR%" && echo [Media-Backend] Maven Spring Boot 启动中... && mvn spring-boot:run"
)

echo     ^> 后端已在最小化窗口中启动 (端口 %BACKEND_PORT%)。
echo.

:: ==================== 3. 阻塞心跳等待 (Health Check Wait) ====================
echo [3/6] 阻塞心跳等待，留给 Spring Boot 容器初始化...
timeout /t 5 /nobreak >nul
echo     ^> 5 秒冷却完成。
echo.

:: ==================== 4. 异步启动前端 (Frontend Ignition) ====================
echo [4/6] 异步挂载前端静态服务...

set "FRONTEND_DIR=%ROOT%\nautilus-frontend"

:: 当前前端为纯静态 (Vue CDN)，无 npm；用 Python 起一个轻量 HTTP 服务占位 5173
where python >nul 2>&1
if %errorlevel% equ 0 (
    start "Media-Frontend" /MIN cmd /k "cd /d "%FRONTEND_DIR%" && echo [Media-Frontend] Python HTTP 服务 0.0.0.0:%FRONTEND_PORT% ... && python -m http.server %FRONTEND_PORT%"
) else (
    :: 无 Python 则尝试 npx serve（需已安装 Node）
    where npx >nul 2>&1
    if !errorlevel! equ 0 (
        start "Media-Frontend" /MIN cmd /k "cd /d "%FRONTEND_DIR%" && echo [Media-Frontend] npx serve 启动中... && npx -y serve -s . -l %FRONTEND_PORT%"
    ) else (
        echo     ^> 警告: 未检测到 python 或 npx，请先安装 Python 或将 index.html 用其他方式托管在 %FRONTEND_PORT% 端口。
    )
)

echo     ^> 前端已在最小化窗口中启动 (端口 %FRONTEND_PORT%)。
echo.

:: ==================== 5. 短暂等待后唤醒浏览器 ====================
echo [5/6] 等待前端服务就绪...
timeout /t 3 /nobreak >nul

echo [6/6] 唤醒浏览器 (UAT Navigation)...
start "" "http://localhost:%FRONTEND_PORT%"

echo.
echo  ═══════════════════════════════════════════════════
echo    だから僕は音楽を辞めた —— Media Cloud Engine Ready!
echo  ═══════════════════════════════════════════════════
echo    后端 API: http://localhost:%BACKEND_PORT%/api/v1/tasks
echo    前端页面: http://localhost:%FRONTEND_PORT%
echo  ═══════════════════════════════════════════════════
echo.
pause
