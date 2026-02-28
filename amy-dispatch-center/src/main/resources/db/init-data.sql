-- ============================================================================
-- PostgreSQL 初始化测试数据
-- Yorushika (ヨルシカ) 主题 Mock 数据
-- ============================================================================

-- 清空现有测试数据 (可选)
-- TRUNCATE TABLE sys_media_task RESTART IDENTITY CASCADE;

-- ============================================================================
-- 插入 Yorushika 主题的测试任务
-- ============================================================================

INSERT INTO sys_media_task (task_name, target_url, status, meta_info) VALUES
-- 1. 又三郎 - 代表作之一
(
    '又三郎 4K MV 抓取',
    'https://youtube.com/watch?v=matasaburo_4k',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "又三郎",
        "album": "盗作",
        "resolution": "4K",
        "codec": "H.265",
        "year": 2019,
        "genre": "J-Rock"
    }'::jsonb
),

-- 2. 夜行 - 经典曲目
(
    '夜行 Live 版本备份',
    'https://youtube.com/watch?v=yakou_live',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "夜行",
        "type": "live",
        "venue": "武道館",
        "year": 2020,
        "resolution": "1080p",
        "bitrate": "320kbps"
    }'::jsonb
),

-- 3. 思想犯 - 热门单曲
(
    '思想犯 Official MV',
    'https://youtube.com/watch?v=shisouham_official',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "思想犯",
        "album": "盗作",
        "resolution": "1080p",
        "views": "10M+",
        "tags": ["rock", "anime"]
    }'::jsonb
),

-- 4. 春泥棒 - 春季代表作
(
    '春泥棒 Piano Ver 抓取',
    'https://youtube.com/watch?v=harunodorobou_piano',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "春泥棒",
        "version": "piano",
        "resolution": "720p",
        "mood": "melancholic"
    }'::jsonb
),

-- 5. 盗作 - 专辑同名曲
(
    '盗作 Full Album 备份',
    'https://youtube.com/watch?v=tousaku_full_album',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "album": "盗作",
        "type": "full_album",
        "tracks": 10,
        "duration": "42:30",
        "format": "FLAC"
    }'::jsonb
),

-- 6. 夜明けと蛍 (夜明与萤火虫)
(
    '夜明けと蛍 MV 高清版',
    'https://youtube.com/watch?v=yoake_to_hotaru',
    'RUNNING',
    '{
        "artist": "ヨルシカ",
        "song": "夜明けと蛍",
        "resolution": "1080p",
        "fps": 60,
        "description": "感动催泪系"
    }'::jsonb
),

-- 7. ただ君に晴れ (只愿你晴朗)
(
    'ただ君に晴れ Acoustic Ver',
    'https://youtube.com/watch?v=tadakimi_acoustic',
    'SUCCESS',
    '{
        "artist": "ヨルシカ",
        "song": "ただ君に晴れ",
        "version": "acoustic",
        "completed_at": "2024-02-20",
        "file_size": "156MB"
    }'::jsonb
),

-- 8. 花に亡霊 (花之亡灵)
(
    '花に亡霊 动画版 MV',
    'https://youtube.com/watch?v=hana_ni_bourei_anime',
    'FAILED',
    '{
        "artist": "ヨルシカ",
        "song": "花に亡霊",
        "anime": "Kaijuu no Kodomo",
        "resolution": "1080p"
    }'::jsonb
),

-- 9. 雨とカプチーノ (雨与卡布奇诺)
(
    '雨とカプチーノ 雨天循环版',
    'https://youtube.com/watch?v=ame_to_cappuccino_loop',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "雨とカプチーノ",
        "mood": "rainy_day",
        "loop": true,
        "duration": "3:45"
    }'::jsonb
),

-- 10. 言って (说出来吧)
(
    '言って Live Tour 2023',
    'https://youtube.com/watch?v=itte_live_2023',
    'PENDING',
    '{
        "artist": "ヨルシカ",
        "song": "言って",
        "type": "live",
        "tour": "2023 Tour",
        "resolution": "4K",
        "fps": 60
    }'::jsonb
);

-- ============================================================================
-- 模拟已分配任务 - 绑定到工作节点
-- ============================================================================

UPDATE sys_media_task 
SET worker_node = 'worker-node-alpha', 
    updated_at = CURRENT_TIMESTAMP
WHERE task_name = '夜明けと蛍 MV 高清版';

-- ============================================================================
-- 模拟失败任务 - 记录错误日志
-- ============================================================================

UPDATE sys_media_task
SET error_log = '春泥棒 - 网络超时导致下载失败,已重试3次仍无法连接源站',
    updated_at = CURRENT_TIMESTAMP
WHERE task_name = '花に亡霊 动画版 MV';

-- ============================================================================
-- 数据验证查询
-- ============================================================================

-- 1. 查看所有任务统计
SELECT 
    status,
    COUNT(*) AS task_count
FROM sys_media_task
GROUP BY status
ORDER BY 
    CASE status
        WHEN 'PENDING' THEN 1
        WHEN 'RUNNING' THEN 2
        WHEN 'SUCCESS' THEN 3
        WHEN 'FAILED' THEN 4
    END;

-- 2. 查看 PENDING 任务详情
SELECT 
    task_id,
    task_name,
    meta_info->>'song' AS song_name,
    meta_info->>'resolution' AS resolution,
    created_at
FROM sys_media_task
WHERE status = 'PENDING'
ORDER BY created_at ASC;

-- 3. 验证 JSONB 索引查询性能
EXPLAIN ANALYZE
SELECT * FROM sys_media_task
WHERE meta_info @> '{"artist":"ヨルシカ"}';

-- ============================================================================
-- 测试数据说明
-- ============================================================================

/*
初始化数据包含:
- 10条任务记录
- 状态分布: 
  * PENDING: 7条 (可用于拉取测试)
  * RUNNING: 1条 (模拟正在执行)
  * SUCCESS: 1条 (模拟成功完成)
  * FAILED: 1条 (模拟失败,带错误日志)

- JSONB 元数据包含:
  * 艺术家信息
  * 歌曲/专辑名称
  * 视频分辨率/编码
  * 自定义标签和描述

使用场景:
1. 并发拉取测试: 使用7条 PENDING 任务模拟多节点并发拉取
2. JSONB 查询测试: 基于 meta_info 进行条件查询
3. 状态流转测试: 验证 PENDING -> RUNNING -> SUCCESS/FAILED 流程
*/
