# Elma Stream Worker - Dockerfile
# 基于 Python 3.11 Alpine 镜像构建轻量级 Worker 容器

FROM python:3.11-alpine

# 设置工作目录
WORKDIR /app

# 安装系统依赖 (yt-dlp 需要 ffmpeg)
RUN apk add --no-cache \
    ffmpeg \
    curl \
    && rm -rf /var/cache/apk/*

# 复制依赖文件
COPY requirements.txt .

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY main.py .
COPY config.template.py ./config.py

# 设置环境变量
ENV PYTHONUNBUFFERED=1
ENV WORKER_ID=Elma-Docker-Node-01

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import sys; sys.exit(0)"

# 启动命令
CMD ["python", "main.py"]
