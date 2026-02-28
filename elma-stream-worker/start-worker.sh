#!/bin/bash
# Elma Stream Worker - 启动脚本 (Bash)
# 用于 Linux/macOS 环境快速启动 Python Worker 节点

set -e

# 颜色定义
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}============================================================${NC}"
echo -e "${CYAN}🌙 Elma Stream Worker 启动器${NC}"
echo -e "${CYAN}   Nautilus Media Cloud - Python Data Plane${NC}"
echo -e "${CYAN}============================================================${NC}"
echo ""

# 检查 Python 版本
echo -e "${YELLOW}[1/4] 检查 Python 环境...${NC}"
if command -v python3 &> /dev/null; then
    PYTHON_CMD="python3"
    PYTHON_VERSION=$(python3 --version)
    echo -e "${GREEN}   ✓ $PYTHON_VERSION${NC}"
elif command -v python &> /dev/null; then
    PYTHON_CMD="python"
    PYTHON_VERSION=$(python --version)
    echo -e "${GREEN}   ✓ $PYTHON_VERSION${NC}"
else
    echo -e "${RED}   ✗ Python 未安装或未添加到 PATH${NC}"
    echo -e "${RED}   请安装 Python 3.10+${NC}"
    exit 1
fi

# 检查 pip
if command -v pip3 &> /dev/null; then
    PIP_CMD="pip3"
elif command -v pip &> /dev/null; then
    PIP_CMD="pip"
else
    echo -e "${RED}   ✗ pip 未找到${NC}"
    exit 1
fi

# 检查依赖是否已安装
echo ""
echo -e "${YELLOW}[2/4] 检查依赖包...${NC}"
MISSING_PACKAGES=()

for pkg in httpx yt-dlp; do
    if $PIP_CMD show $pkg &> /dev/null; then
        echo -e "${GREEN}   ✓ $pkg 已安装${NC}"
    else
        echo -e "${RED}   ✗ $pkg 未安装${NC}"
        MISSING_PACKAGES+=($pkg)
    fi
done

# 如果有缺失的包,提示安装
if [ ${#MISSING_PACKAGES[@]} -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}发现缺失依赖包,是否自动安装? (Y/N)${NC}"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        echo ""
        echo -e "${YELLOW}[3/4] 安装依赖包...${NC}"
        $PIP_CMD install -r requirements.txt
        echo -e "${GREEN}   ✓ 依赖安装完成${NC}"
    else
        echo -e "${RED}   ✗ 用户取消安装,退出启动流程${NC}"
        exit 1
    fi
else
    echo ""
    echo -e "${GREEN}[3/4] 依赖检查完成,所有包已就绪${NC}"
fi

# 检查 Java 调度中心是否在线
echo ""
echo -e "${YELLOW}[4/4] 检查 Java 调度中心连接...${NC}"
if curl -s -f -m 3 "http://localhost:8080/api/v1/tasks/health" > /dev/null 2>&1; then
    echo -e "${GREEN}   ✓ Java 调度中心在线${NC}"
else
    echo -e "${YELLOW}   ⚠ Java 调度中心未响应 (http://localhost:8080)${NC}"
    echo -e "${YELLOW}   请确保 amy-dispatch-center 已启动${NC}"
    echo ""
    echo -e "${YELLOW}是否继续启动 Worker? (Y/N)${NC}"
    read -r response
    
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo -e "${RED}   ✗ 用户取消启动${NC}"
        exit 1
    fi
fi

# 启动 Worker
echo ""
echo -e "${CYAN}============================================================${NC}"
echo -e "${CYAN}🚀 启动 Elma Stream Worker...${NC}"
echo -e "${CYAN}============================================================${NC}"
echo ""
echo -e "${YELLOW}提示: 按 Ctrl+C 停止 Worker${NC}"
echo ""

# 运行主程序
$PYTHON_CMD main.py
