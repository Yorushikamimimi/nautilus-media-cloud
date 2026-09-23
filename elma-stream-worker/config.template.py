# Elma Stream Worker - 配置文件模板
# 复制此文件为 config.py 并修改配置

# ==================== 调度中心配置 ====================
# Java 调度中心的 REST API 基础 URL
BASE_URL = "http://localhost:8081/api/v1/tasks"  # NAUTILUS_API_BASE_URL 可覆盖

# 与后端 nautilus.auth.token 一致；main.py 默认读取环境变量 NAUTILUS_AUTH_TOKEN（未设置则为 changeme）
# export NAUTILUS_AUTH_TOKEN=your-secret

# ==================== Worker 配置 ====================
# 当前 Worker 节点的唯一标识符
# 建议格式: Elma-Node-{序号} 或 {主机名}-Worker-{序号}
WORKER_ID = "<hostname>-<pid>"  # 默认由 main.py 自动生成；用 NAUTILUS_WORKER_ID 固定覆盖

# 轮询间隔(秒)
# 建议范围: 1-10 秒
POLL_INTERVAL = 3

# HTTP 请求超时(秒)
REQUEST_TIMEOUT = 30.0

# ==================== yt-dlp 配置 ====================
# yt-dlp 命令路径 (如果不在 PATH 中,请指定完整路径)
YTDLP_PATH = "yt-dlp"

# yt-dlp 提取超时(秒)
YTDLP_TIMEOUT = 60.0

# 是否仅提取元数据(不下载视频文件)
YTDLP_METADATA_ONLY = True

# ==================== 日志配置 ====================
# 日志级别: DEBUG, INFO, WARNING, ERROR, CRITICAL
LOG_LEVEL = "INFO"

# 日志格式
LOG_FORMAT = "[%(asctime)s] [%(levelname)s] 🌙 %(message)s"

# 日志时间格式
LOG_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

# 是否启用彩色日志 (需要安装 colorlog)
ENABLE_COLOR_LOG = False

# ==================== 重试策略 ====================
# 任务执行失败后的最大重试次数
MAX_RETRY_COUNT = 3

# 重试间隔(秒)
RETRY_INTERVAL = 5

# ==================== 性能配置 ====================
# 并发任务数量限制
# 注意: Python Worker 通常为单线程,此参数用于未来扩展
MAX_CONCURRENT_TASKS = 1

# ==================== Yorushika 主题配置 ====================
# 是否启用 Yorushika 主题日志
ENABLE_YORUSHIKA_THEME = True

# Yorushika 主题消息模板
YORUSHIKA_MESSAGES = {
    "no_task": "思想犯 - 当前无任务,Elma 正在游荡...",
    "task_success": "夜行 - 任务拉取成功",
    "extract_start": "又三郎 - 开始提取媒体流",
    "extract_success": "又三郎 - 媒体流提取完毕,风载着数据归来",
    "extract_failed": "春泥棒 - 抓取链路断裂,花瓣散落",
    "status_reported": "夜行 - 节点状态更新成功",
    "http_error": "春泥棒 - HTTP 状态错误",
    "request_failed": "春泥棒 - 请求失败",
    "timeout": "春泥棒 - 提取超时,风停了",
    "shutdown": "夜行 - 收到中断信号,Elma 准备休眠...",
    "goodbye": "再见,Elma 陷入长眠..."
}
