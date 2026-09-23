#!/usr/bin/env python3
"""只读 Worker/API 连通性检查；不会创建、领取或更新任务。"""

import asyncio
import os

import httpx

BASE_URL = os.environ.get(
    "NAUTILUS_API_BASE_URL", "http://localhost:8081/api/v1/tasks"
)
AUTH_TOKEN = os.environ.get("NAUTILUS_AUTH_TOKEN", "changeme")


async def main() -> int:
    headers = {"Authorization": f"Bearer {AUTH_TOKEN}"}
    try:
        async with httpx.AsyncClient(timeout=10.0, headers=headers) as client:
            response = await client.get(f"{BASE_URL}/health")
            response.raise_for_status()
            result = response.json()
            if (
                result.get("code") == 200
                and result.get("status") == "RUNNING"
                and result.get("database") == "UP"
            ):
                print("调度中心健康检查通过，数据库在线")
                return 0
            print("调度中心或数据库未就绪")
            return 1
    except httpx.HTTPError as exc:
        print(f"调度中心健康检查失败: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
