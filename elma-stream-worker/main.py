#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Nautilus - Elma Stream Worker（数据面）
从 Java 调度服务拉取任务并执行媒体提取（示例项目）。

技术栈:
- Python 3.10+
- httpx (异步 HTTP 客户端)
- asyncio (事件循环)
- yt-dlp (流媒体提取 - 原生 Python API)

Powered by Yorushika (ヨルシカ) 🌙
"""

import asyncio
import logging
import os
import sqlite3
import sys
import shutil
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Optional, Dict, Any, List, Callable

import httpx
import yt_dlp


# ==================== 全局配置 ====================
BASE_URL = "http://localhost:8080/api/v1/tasks"
WORKER_ID = "Elma-Node-01"
# 与 amy-dispatch-center 中 nautilus.auth.token / 环境变量 NAUTILUS_AUTH_TOKEN 一致
AUTH_TOKEN = os.environ.get("NAUTILUS_AUTH_TOKEN", "changeme")
POLL_INTERVAL = 3  # 轮询间隔(秒)
REQUEST_TIMEOUT = 30.0  # HTTP 请求超时(秒)

# 项目根目录 (main.py 所在目录)，用于创建 downloads
WORKER_ROOT = Path(__file__).resolve().parent
DOWNLOAD_DIR = WORKER_ROOT / "downloads"
LOG_DIR = WORKER_ROOT / "logs"
LOG_FILE = LOG_DIR / "worker.log"


# ==================== 日志配置 ====================
def setup_logging() -> logging.Logger:
    """
    配置优雅的 UTF-8 日志输出

    日志格式: [时间] [级别] [Yorushika 标识] 消息内容
    """
    logger = logging.getLogger("ElmaWorker")
    logger.setLevel(logging.INFO)
    logger.handlers.clear()
    logger.propagate = False

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)

    formatter = logging.Formatter(
        fmt="[%(asctime)s] [%(levelname)s] 🌙 %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    console_handler.setFormatter(formatter)

    # 日志落盘，便于排障追溯
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    file_handler = RotatingFileHandler(
        LOG_FILE,
        maxBytes=10 * 1024 * 1024,
        backupCount=7,
        encoding="utf-8"
    )
    file_handler.setLevel(logging.INFO)
    file_handler.setFormatter(formatter)

    logger.addHandler(console_handler)
    logger.addHandler(file_handler)
    return logger


logger = setup_logging()


# ==================== 下载目录初始化 ====================
def ensure_downloads_dir() -> Path:
    """在项目根目录自动创建 downloads 文件夹（若不存在）"""
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    return DOWNLOAD_DIR


# ==================== HTTP 客户端 ====================
async def create_http_client() -> httpx.AsyncClient:
    """创建异步 HTTP 客户端"""
    return httpx.AsyncClient(
        timeout=httpx.Timeout(REQUEST_TIMEOUT),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": f"ElmaWorker/{WORKER_ID}",
            "Authorization": f"Bearer {AUTH_TOKEN}",
        }
    )


# ==================== 任务拉取逻辑 ====================
async def poll_tasks(client: httpx.AsyncClient) -> Optional[Dict[str, Any]]:
    """
    从 Java 调度中心拉取待处理任务

    Args:
        client: 异步 HTTP 客户端

    Returns:
        任务数据字典,如果无任务则返回 None
    """
    url = f"{BASE_URL}/pending"
    params = {"workerNode": WORKER_ID}

    try:
        response = await client.get(url, params=params)
        response.raise_for_status()

        result = response.json()

        if result.get("code") != 200:
            logger.warning(f"思想犯 - 拉取响应异常: {result.get('msg')}")
            return None

        task_data = result.get("data")

        if task_data:
            logger.info(
                f"夜行 - 任务拉取成功 | "
                f"TaskID: {task_data.get('taskId')} | "
                f"TaskName: {task_data.get('taskName')}"
            )
            return task_data
        else:
            logger.debug("思想犯 - 当前无任务,Elma 正在游荡...")
            return None

    except httpx.HTTPStatusError as e:
        logger.error(f"春泥棒 - HTTP 状态错误: {e.response.status_code} | {e}")
        return None
    except httpx.RequestError as e:
        logger.error(f"春泥棒 - 请求失败: {e}")
        return None
    except Exception as e:
        logger.error(f"春泥棒 - 未知异常: {e}", exc_info=True)
        return None


# ==================== yt_dlp 配置与同步下载（在线程池中执行）====================
COOKIE_READ_MSG = (
    "思想犯 - 无法读取 cookies.txt，请检查凭证文件是否放在了根目录！"
)

# 静态 Cookie 文件路径（项目根目录，与 main.py 同级的 cookies.txt）
COOKIE_FILE = WORKER_ROOT / "cookies.txt"


def _build_ydl_opts(format_pref: str = "mp3", progress_hooks: Optional[List[Any]] = None) -> Dict[str, Any]:
    """
    根据前端传来的 format_pref，构建 yt_dlp 选项。
    支持: mp3, flac, video1080p, video4k
    使用 cookiefile 读取根目录 cookies.txt，避免 Chromium 内核文件锁。
    """
    outtmpl = str(DOWNLOAD_DIR / "%(title)s.%(ext)s")
    opts: Dict[str, Any] = {
        "outtmpl": outtmpl,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "cookiefile": str(COOKIE_FILE),  # 静态凭证：根目录 cookies.txt
    }
    
    if progress_hooks:
        opts["progress_hooks"] = progress_hooks
    
    if format_pref == "flac":
        opts["format"] = "bestaudio/best"
        if shutil.which("ffmpeg"):
            opts["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "flac"}]
    elif format_pref == "video1080p":
        opts["format"] = "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
        # 不需要单独的 Audio Extract Postprocessor
    elif format_pref == "video4k":
        opts["format"] = "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
    else:
        # Default MP3
        opts["format"] = "bestaudio/best"
        if shutil.which("ffmpeg"):
            opts["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3"}]

    return opts


def _download_with_ytdlp(url: str, format_pref: str, progress_hooks: Optional[List[Any]] = None) -> Dict[str, Any]:
    """
    使用 yt_dlp 原生 Python API（上下文管理器）下载并提取元数据。
    阻塞调用，需在 asyncio.to_thread 中执行。

    Returns:
        info_dict 核心字段组成的 metaInfo 字典。
    Raises:
        PermissionError / FileNotFoundError: 无法读取 cookies.txt 时
        yt_dlp.utils.DownloadError: 网络/下载错误
    """
    ydl_opts = _build_ydl_opts(format_pref, progress_hooks)
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        if info is None:
            raise yt_dlp.utils.DownloadError("extract_info 返回空")
        meta: Dict[str, Any] = {
            "title": info.get("title") or "",
            "duration": info.get("duration") if info.get("duration") is not None else 0,
            "uploader": info.get("uploader") or "",
            "thumbnail": info.get("thumbnail") or "",
            "fileSize": info.get("filesize") or info.get("filesize_approx") or 0,
            "format": format_pref
        }
        
        # 提取真实落盘文件路径
        # 对于单一文件，通常在 info 根级别，包含后缀
        # 经过 ffmpeg 处理的（如提取 mp3），可能会在 requested_downloads 里
        actual_filepath = info.get("filepath") or info.get("__files_to_move", {}).get(info.get("id"))
        if not actual_filepath and info.get("requested_downloads"):
            actual_filepath = info.get("requested_downloads")[0].get("filepath")
        
        # 如果还是没拿到，尝试用 info['ext'] 自行推断 (不一定完全准确，兜底用)
        if not actual_filepath:
             ext = info.get("ext", "mp3")
             if format_pref in ["mp3", "flac"] and "audio" in ydl_opts.get("format", ""):
                 ext = format_pref # 强转
             title = info.get("title", "unknown")
             # yt-dlp 会清理特殊字符，这里粗略拼接
             actual_filepath = str(DOWNLOAD_DIR / f"{title}.{ext}")

        meta["filePath"] = str(actual_filepath)
        return meta


# ==================== 流媒体提取逻辑（原生 yt_dlp API + 静态 Cookie）====================
async def extract_media(client: httpx.AsyncClient, task_data: Dict[str, Any]) -> tuple[bool, Optional[Dict[str, Any]], Optional[str]]:
    """
    使用 yt_dlp 原生 Python API（with YoutubeDL as ydl）+ 静态 cookies.txt 进行下载与元数据解析。
    异常兜底：无法读取凭证文件时友好提示，其余统一按 DownloadError 处理。
    """
    target_url = task_data.get("targetUrl", "")
    meta_info_input = task_data.get("metaInfo") or {}
    format_pref = meta_info_input.get("format", "mp3")
    task_id = int(task_data.get("taskId", 0))

    logger.info(f"又三郎 - 戴上 Edge 面具潜入深海，开始嗅探媒体流: {target_url} [Format: {format_pref}]")

    loop = asyncio.get_running_loop()
    last_report_time = 0

    def progress_hook(d):
        nonlocal last_report_time
        if d.get('status') == 'downloading':
            percent_str = d.get('_percent_str', '').strip()
            if percent_str:
                import re, time
                clean_percent = re.sub(r'\x1b\[[0-9;]*m', '', percent_str).strip()
                now = time.time()
                # 节流机制: 每 1.5 秒上报一次
                if now - last_report_time > 1.5:
                    last_report_time = now
                    # 从子线程将协程塞进主线程的事件循环中去执行 HTTP PUT
                    asyncio.run_coroutine_threadsafe(
                        report_status(client, task_id, "RUNNING", progress=clean_percent),
                        loop
                    )

    try:
        # 传递 format_pref 给 _download_with_ytdlp
        meta_info = await asyncio.to_thread(_download_with_ytdlp, target_url, format_pref, [progress_hook])
        logger.info("夜明けと蛍 - 原生 API 提取成功！文件落盘，元数据准备回调...")
        return True, meta_info, None

    except (PermissionError, FileNotFoundError, sqlite3.OperationalError) as e:
        logger.error(f"{COOKIE_READ_MSG} | 原始异常: {e}")
        return False, None, COOKIE_READ_MSG
    except yt_dlp.utils.DownloadError as e:
        error_msg = str(e)
        logger.error(f"春泥棒 - 抓取链路断裂，花瓣散落: {error_msg}")
        return False, None, error_msg
    except Exception as e:
        error_msg = str(e)
        logger.error(f"春泥棒 - 抓取链路断裂，花瓣散落: {error_msg}", exc_info=True)
        return False, None, error_msg


# ==================== 状态回调逻辑 ====================
async def report_status(
    client: httpx.AsyncClient,
    task_id: int,
    status: str,
    error_log: Optional[str] = None,
    meta_info: Optional[Dict[str, Any]] = None,
    progress: Optional[str] = None,
) -> bool:
    """
    向 Java 调度中心回报任务状态，成功时可携带 metaInfo。

    Args:
        client: 异步 HTTP 客户端
        task_id: 任务 ID
        status: 任务状态 (SUCCESS/FAILED/RUNNING)
        error_log: 错误日志 (可选)
        meta_info: 成功时从 info_dict 提取的元数据 (title/duration/uploader/thumbnail)
        progress: 运行时的进度百分比 (可选)

    Returns:
        True 表示回报成功, False 表示失败
    """
    url = f"{BASE_URL}/{task_id}/status"
    payload: Dict[str, Any] = {
        "status": status,
        "errorLog": error_log,
    }
    if meta_info is not None:
        payload["metaInfo"] = meta_info
    
    # 将 progress 也放入请求体，如果调用方没传它会直接是 None，不影响原来逻辑
    if "progress" in locals() and progress is not None:
        payload["progress"] = progress

    try:
        response = await client.put(url, json=payload)
        response.raise_for_status()

        result = response.json()

        if result.get("code") == 200:
            logger.info(f"夜行 - 节点状态更新成功 | TaskID: {task_id} | Status: {status}")
            return True
        else:
            logger.warning(f"思想犯 - 状态回报异常: {result.get('msg')}")
            return False

    except httpx.HTTPStatusError as e:
        logger.error(f"春泥棒 - 状态回报 HTTP 错误: {e.response.status_code}")
        return False
    except httpx.RequestError as e:
        logger.error(f"春泥棒 - 状态回报请求失败: {e}")
        return False
    except Exception as e:
        logger.error(f"春泥棒 - 状态回报未知异常: {e}", exc_info=True)
        return False


# ==================== 任务执行器 ====================
async def execute_task(client: httpx.AsyncClient, task_data: Dict[str, Any]) -> None:
    """
    执行单个任务的完整流程：真实下载 -> 元数据解析 -> PUT 回调（含 metaInfo/FAILED 兜底）
    """
    task_id = int(task_data.get("taskId", 0))

    success, meta_info, error_log = await extract_media(client, task_data)

    if success and meta_info is not None:
        await report_status(client, task_id, "SUCCESS", meta_info=meta_info)
    else:
        await report_status(
            client,
            task_id,
            "FAILED",
            error_log=error_log or "春泥棒 - 媒体流提取失败，具体见 Worker 日志",
        )


# ==================== 主循环 ====================
async def worker_loop() -> None:
    """
    Worker 节点主循环

    持续轮询 Java 调度中心，拉取并执行任务（真实 yt_dlp 下载 + 元数据回调）
    """
    ensure_downloads_dir()
    logger.info("=" * 60)
    logger.info("🌙 Elma Stream Worker 启动成功")
    logger.info(f"   Worker ID: {WORKER_ID}")
    logger.info(f"   Java 调度中心: {BASE_URL}")
    logger.info(f"   轮询间隔: {POLL_INTERVAL}s")
    logger.info(f"   下载目录: {DOWNLOAD_DIR}")
    logger.info("=" * 60)

    async with await create_http_client() as client:
        while True:
            try:
                task_data = await poll_tasks(client)

                if task_data:
                    await execute_task(client, task_data)
                else:
                    await asyncio.sleep(POLL_INTERVAL)

            except KeyboardInterrupt:
                logger.info("🌙 夜行 - 收到中断信号,Elma 准备休眠...")
                break
            except Exception as e:
                logger.error(f"春泥棒 - 主循环异常: {e}", exc_info=True)
                await asyncio.sleep(POLL_INTERVAL)


# ==================== 程序入口 ====================
def main() -> None:
    """程序主入口"""
    try:
        asyncio.run(worker_loop())
    except KeyboardInterrupt:
        logger.info("🌙 再见,Elma 陷入长眠...")
        sys.exit(0)


if __name__ == "__main__":
    main()
