# Nautilus 任务调度示例 - 项目结构总览

## 📁 完整文件清单

### 1️⃣ ruoyi-common (公共模块)
```
ruoyi-common/
├── pom.xml
└── src/main/java/com/nautilus/common/
    ├── core/domain/
    │   └── AjaxResult.java              ✅ RuoYi 标准响应封装
    └── exception/
        └── ServiceException.java         ✅ 业务异常类
```

**关键特性:**
- ✅ 统一 API 响应格式 (code, msg, data)
- ✅ Yorushika 主题默认消息
- ✅ 支持链式调用

---

### 2️⃣ amy-dispatch-center (调度中心)
```
amy-dispatch-center/
├── pom.xml                              ✅ Maven 依赖配置
├── src/main/java/com/nautilus/dispatch/
│   ├── DispatchApplication.java         ✅ Spring Boot 启动类
│   ├── domain/entity/
│   │   └── SysMediaTask.java            ✅ 任务实体 (Lombok + JSONB)
│   ├── mapper/
│   │   └── SysMediaTaskMapper.java      ✅ MyBatis-Plus Mapper
│   ├── service/
│   │   ├── ISysMediaTaskService.java    ✅ 服务接口
│   │   └── impl/
│   │       └── SysMediaTaskServiceImpl.java  ✅ 服务实现 (事务+日志)
│   └── controller/
│       └── MediaTaskController.java     ✅ REST API 控制器
│
└── src/main/resources/
    ├── application.yml                  ✅ 配置文件
    ├── mapper/
    │   └── SysMediaTaskMapper.xml       ✅ SQL Mapper (核心原子拉取)
    └── db/
        ├── schema.sql                   ✅ 建表脚本
        └── init-data.sql                ✅ Yorushika 测试数据
```

---

## 🎯 核心代码亮点

### 1. 并发防重 SQL (SysMediaTaskMapper.xml)
```sql
UPDATE sys_media_task
SET status = 'RUNNING', worker_node = #{workerNode}
WHERE task_id = (
    SELECT task_id FROM sys_media_task 
    WHERE status = 'PENDING' 
    ORDER BY created_at ASC 
    LIMIT 1 
    FOR UPDATE SKIP LOCKED  -- 🔥 核心技术点
)
RETURNING *;
```

### 2. JSONB 映射 (SysMediaTask.java)
```java
@TableField(typeHandler = JacksonTypeHandler.class)
private Map<String, Object> metaInfo;
```

### 3. 事务控制 (SysMediaTaskServiceImpl.java)
```java
@Transactional(rollbackFor = Exception.class)
public SysMediaTask pullPendingTask(String workerNode) {
    // 原子拉取 + Yorushika 日志
}
```

### 4. 统一响应 (MediaTaskController.java)
```java
@GetMapping("/pending")
public AjaxResult pullTask(@RequestParam String workerNode) {
    // 无任务：AjaxResult.noContent("No pending task")
    // 有任务：AjaxResult.success("Task pulled", task)
}
```

---

## 🗄️ 数据库设计

### 表: sys_media_task

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | BIGSERIAL | 主键自增 |
| task_name | VARCHAR(200) | 任务名称 |
| target_url | TEXT | 目标URL |
| status | VARCHAR(20) | PENDING/RUNNING/SUCCESS/FAILED |
| worker_node | VARCHAR(100) | 工作节点标识 |
| meta_info | JSONB | 元数据 (JacksonTypeHandler) |
| error_log | TEXT | 错误日志 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### 核心索引
- `idx_status_created`: 部分索引 (只索引 PENDING)
- `idx_worker_node`: 节点查询加速
- `idx_meta_info`: GIN 索引支持 JSONB 查询

---

## 🚀 快速启动

### Step 1: 数据库初始化
```bash
createdb nautilus_dispatch
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/schema.sql
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/init-data.sql
```

### Step 2: 修改配置
编辑 `amy-dispatch-center/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nautilus_dispatch
    username: postgres
    password: your_password
```

### Step 3: 构建运行
```bash
# 安装公共模块
cd ruoyi-common && mvn clean install

# 启动调度中心
cd ../amy-dispatch-center && mvn spring-boot:run
```

### Step 4: 测试 API
```bash
# 健康检查
curl http://localhost:8080/api/v1/tasks/health

# 拉取任务
curl "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-01"

# 回报状态
curl -X PUT http://localhost:8080/api/v1/tasks/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUCCESS"}'
```

---

## 📊 测试数据说明

`init-data.sql` 包含 10 条 Yorushika 主题任务:

| 任务名 | 状态 | 说明 |
|--------|------|------|
| 又三郎 4K MV 抓取 | PENDING | 可拉取 |
| 夜行 Live 版本备份 | PENDING | 可拉取 |
| 思想犯 Official MV | PENDING | 可拉取 |
| 夜明けと蛍 MV | RUNNING | 正在执行 |
| ただ君に晴れ | SUCCESS | 已完成 |
| 花に亡霊 动画版 | FAILED | 带错误日志 |

---

## 🎵 Yorushika 日志规范

| 前缀 | 场景 | 示例 |
|------|------|------|
| 夜行 | 成功操作 | "夜行 - 任务拉取成功" |
| 思想犯 | 业务警告 | "思想犯 - 当前无待处理任务" |
| 春泥棒 | 任务失败 | "春泥棒 - 网络超时" |
| 又三郎 | 数据不存在 | "又三郎 - 任务不存在" |
| 盗作 | 状态异常 | "盗作 - 任务状态异常" |

---

## ✅ 技术规范遵循情况

- ✅ Java 17+ 语法
- ✅ Spring Boot 3.x (jakarta.* 包)
- ✅ Lombok 注解 (@Data, @Builder, @Slf4j)
- ✅ MyBatis-Plus BaseMapper + XML
- ✅ PostgreSQL 15+ JSONB + FOR UPDATE SKIP LOCKED
- ✅ 统一 Result<T> 响应封装 (AjaxResult)
- ✅ 强制 Type Hinting (JacksonTypeHandler)
- ✅ 全部 Mock 数据使用 Yorushika 元素

---

## 📦 依赖版本

| 依赖 | 版本 |
|------|------|
| Spring Boot | 3.2.2 |
| MyBatis-Plus | 3.5.5 |
| PostgreSQL JDBC | 42.7.1 |
| Lombok | Latest (via Spring Boot) |

---

## 🎯 后续扩展建议

1. **Redis 分布式锁**: 在 Service 层增加 Redisson 锁,进一步提升并发安全性
2. **任务重试机制**: FAILED 任务自动重置为 PENDING,带重试计数
3. **节点心跳检测**: RUNNING 任务超时自动释放
4. **Metrics 监控**: 集成 Micrometer 暴露任务拉取 TPS/成功率指标
5. **WebSocket 推送**: 实时推送任务状态变更给前端

---

> **夜行 - 愿任务队列与状态机逻辑清晰可维护** 🌙
