#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Elma Stream Worker - 功能测试脚本
用于验证 Worker 节点的核心功能

测试覆盖:
1. HTTP 客户端连接测试
2. 任务拉取功能测试
3. 状态回调功能测试
4. 异常处理测试
5. yt-dlp 可用性测试
"""

import asyncio
import sys
from typing import Dict, Any

import httpx


# 测试配置
BASE_URL = "http://localhost:8080/api/v1/tasks"
TEST_WORKER_ID = "Test-Worker-01"
TIMEOUT = 10.0


class Colors:
    """终端颜色代码"""
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BLUE = '\033[94m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'


def print_test_header(test_name: str) -> None:
    """打印测试标题"""
    print(f"\n{Colors.BOLD}{Colors.BLUE}[测试] {test_name}{Colors.ENDC}")
    print("-" * 60)


def print_success(message: str) -> None:
    """打印成功消息"""
    print(f"{Colors.GREEN}✓ {message}{Colors.ENDC}")


def print_warning(message: str) -> None:
    """打印警告消息"""
    print(f"{Colors.YELLOW}⚠ {message}{Colors.ENDC}")


def print_error(message: str) -> None:
    """打印错误消息"""
    print(f"{Colors.RED}✗ {message}{Colors.ENDC}")


async def test_java_center_health() -> bool:
    """测试 Java 调度中心健康检查"""
    print_test_header("Java 调度中心连接测试")
    
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            response = await client.get(f"{BASE_URL}/health")
            
            if response.status_code == 200:
                print_success(f"调度中心在线 (HTTP {response.status_code})")
                return True
            else:
                print_warning(f"调度中心响应异常 (HTTP {response.status_code})")
                return False
    except httpx.RequestError as e:
        print_error(f"连接失败: {e}")
        return False


async def test_pull_task() -> Dict[str, Any] | None:
    """测试任务拉取功能"""
    print_test_header("任务拉取功能测试")
    
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            url = f"{BASE_URL}/pending"
            params = {"workerNode": TEST_WORKER_ID}
            
            response = await client.get(url, params=params)
            response.raise_for_status()
            
            result = response.json()
            
            if result.get("code") == 200:
                task_data = result.get("data")
                
                if task_data:
                    print_success(f"任务拉取成功")
                    print(f"   - TaskID: {task_data.get('taskId')}")
                    print(f"   - TaskName: {task_data.get('taskName')}")
                    print(f"   - Status: {task_data.get('status')}")
                    return task_data
                else:
                    print_warning("当前无待处理任务")
                    return None
            else:
                print_error(f"拉取失败: {result.get('msg')}")
                return None
                
    except httpx.RequestError as e:
        print_error(f"请求异常: {e}")
        return None


async def test_report_status(task_id: int) -> bool:
    """测试状态回调功能"""
    print_test_header("状态回调功能测试")
    
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            url = f"{BASE_URL}/{task_id}/status"
            payload = {
                "status": "SUCCESS",
                "errorLog": None
            }
            
            response = await client.put(url, json=payload)
            response.raise_for_status()
            
            result = response.json()
            
            if result.get("code") == 200:
                print_success(f"状态回报成功 (TaskID: {task_id})")
                return True
            else:
                print_error(f"状态回报失败: {result.get('msg')}")
                return False
                
    except httpx.RequestError as e:
        print_error(f"请求异常: {e}")
        return False


async def test_ytdlp_availability() -> bool:
    """测试 yt-dlp 可用性"""
    print_test_header("yt-dlp 可用性测试")
    
    try:
        import subprocess
        
        result = subprocess.run(
            ["yt-dlp", "--version"],
            capture_output=True,
            text=True,
            timeout=5,
            check=True
        )
        
        version = result.stdout.strip()
        print_success(f"yt-dlp 已安装 (版本: {version})")
        return True
        
    except FileNotFoundError:
        print_warning("yt-dlp 未安装 (将使用模拟模式)")
        return False
    except Exception as e:
        print_error(f"检测异常: {e}")
        return False


async def test_create_task() -> Dict[str, Any] | None:
    """测试创建任务功能 (用于确保有任务可拉取)"""
    print_test_header("创建测试任务")
    
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            url = BASE_URL
            payload = {
                "taskName": "🧪 自动化测试任务 - 又三郎",
                "targetUrl": "https://www.youtube.com/watch?v=test",
                "metaInfo": {
                    "artist": "ヨルシカ",
                    "test_mode": True
                }
            }
            
            response = await client.post(url, json=payload)
            response.raise_for_status()
            
            result = response.json()
            
            if result.get("code") == 200:
                task_data = result.get("data")
                print_success(f"测试任务创建成功")
                print(f"   - TaskID: {task_data.get('taskId')}")
                return task_data
            else:
                print_error(f"创建失败: {result.get('msg')}")
                return None
                
    except httpx.RequestError as e:
        print_error(f"请求异常: {e}")
        return None


async def run_all_tests() -> None:
    """运行所有测试"""
    print(f"{Colors.BOLD}{Colors.BLUE}")
    print("=" * 60)
    print("🧪 Elma Stream Worker - 功能测试套件")
    print("   Powered by Yorushika (ヨルシカ) 🌙")
    print("=" * 60)
    print(f"{Colors.ENDC}")
    
    # 测试统计
    total_tests = 0
    passed_tests = 0
    
    # 1. 测试 Java 调度中心连接
    total_tests += 1
    if await test_java_center_health():
        passed_tests += 1
    else:
        print_error("\n❌ Java 调度中心未启动,后续测试将跳过")
        print_summary(passed_tests, total_tests)
        return
    
    # 2. 测试 yt-dlp 可用性
    total_tests += 1
    if await test_ytdlp_availability():
        passed_tests += 1
    
    # 3. 创建测试任务
    total_tests += 1
    task_data = await test_create_task()
    if task_data:
        passed_tests += 1
    
    # 4. 测试任务拉取
    total_tests += 1
    pulled_task = await test_pull_task()
    if pulled_task:
        passed_tests += 1
        
        # 5. 测试状态回调
        total_tests += 1
        if await test_report_status(pulled_task.get("taskId")):
            passed_tests += 1
    else:
        print_warning("无任务可拉取,跳过状态回调测试")
    
    # 打印测试总结
    print_summary(passed_tests, total_tests)


def print_summary(passed: int, total: int) -> None:
    """打印测试总结"""
    print(f"\n{Colors.BOLD}{Colors.BLUE}")
    print("=" * 60)
    print("📊 测试总结")
    print("=" * 60)
    print(f"{Colors.ENDC}")
    
    success_rate = (passed / total * 100) if total > 0 else 0
    
    if passed == total:
        print_success(f"全部通过: {passed}/{total} ({success_rate:.0f}%)")
        print(f"\n{Colors.GREEN}🎉 又三郎 - 测试风载着好消息归来!{Colors.ENDC}")
    else:
        print_warning(f"部分通过: {passed}/{total} ({success_rate:.0f}%)")
        print(f"\n{Colors.YELLOW}🌙 思想犯 - 仍有 {total - passed} 个测试需要修复{Colors.ENDC}")


def main() -> None:
    """主入口"""
    try:
        asyncio.run(run_all_tests())
        sys.exit(0)
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}测试被用户中断{Colors.ENDC}")
        sys.exit(1)
    except Exception as e:
        print_error(f"测试异常: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
