# 🚀 快速部署指南

## 📋 前置要求

- ✅ JDK 17 或更高版本
- ✅ Maven 3.6+
- ✅ PostgreSQL 15+
- ✅ IDE: IntelliJ IDEA / Eclipse (推荐 IDEA)

---

## 🔧 第一步: 数据库准备

### 1.1 创建数据库
```bash
# 使用 psql 命令行
psql -U postgres

# 或使用 createdb 命令
createdb -U postgres nautilus_dispatch
```

### 1.2 执行建表脚本
```bash
cd d:\Workspace\cursor_projects\nautilus-media-cloud
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/schema.sql
```

**预期输出:**
```
CREATE TABLE
CREATE INDEX
CREATE INDEX
CREATE INDEX
...
```

### 1.3 初始化测试数据
```bash
psql -U postgres -d nautilus_dispatch -f amy-dispatch-center/src/main/resources/db/init-data.sql
```

**验证数据:**
```sql
-- 登录数据库
psql -U postgres -d nautilus_dispatch

-- 查询任务统计
SELECT status, COUNT(*) FROM sys_media_task GROUP BY status;
```

**预期结果:**
```
  status  | count 
----------+-------
 PENDING  |     7
 RUNNING  |     1
 SUCCESS  |     1
 FAILED   |     1
```

---

## 📦 第二步: 构建项目

### 2.1 安装公共模块
```bash
cd ruoyi-common
mvn clean install
```

**预期输出:**
```
[INFO] BUILD SUCCESS
[INFO] Installing ruoyi-common-1.0.0.jar to local repository
```

### 2.2 构建调度中心
```bash
cd ../amy-dispatch-center
mvn clean package
```

**预期输出:**
```
[INFO] BUILD SUCCESS
[INFO] Building jar: target/amy-dispatch-center-1.0.0.jar
```

---

## ⚙️ 第三步: 修改配置

编辑 `amy-dispatch-center/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/nautilus_dispatch
    username: postgres
    password: 修改为你的密码
```

**其他可选配置:**
```yaml
server:
  port: 8080  # 可修改端口

logging:
  level:
    com.nautilus.dispatch: DEBUG  # 生产环境改为 INFO
```

---

## 🏃 第四步: 启动服务

### 方式 1: Maven 启动 (开发环境)
```bash
cd amy-dispatch-center
mvn spring-boot:run
```

### 方式 2: JAR 包启动 (生产环境)
```bash
java -jar amy-dispatch-center/target/amy-dispatch-center-1.0.0.jar
```

### 方式 3: IDE 启动
- 打开 `DispatchApplication.java`
- 右键 Run 'DispatchApplication.main()'

**启动成功标志:**
```
╔═══════════════════════════════════════════════════════════╗
║                                                           ║
║     🎵 Amy Dispatch Center 启动成功                       ║
║                                                           ║
║     夜行 - 任务调度控制面（示例）                          ║
║     Powered by Yorushika (ヨルシカ)                       ║
║                                                           ║
║     API Base URL: http://localhost:8080/api/v1/tasks     ║
║                                                           ║
╚═══════════════════════════════════════════════════════════╝
```

---

## 🧪 第五步: 接口测试

### 5.1 健康检查
```bash
curl http://localhost:8080/api/v1/tasks/health
```

**预期响应:**
```json
{
  "code": 200,
  "msg": "夜行 - 调度中心运行正常",
  "service": "Amy Dispatch Center",
  "theme": "ヨルシカ (Yorushika)",
  "status": "RUNNING"
}
```

### 5.2 拉取任务
```bash
curl "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-node-01"
```

**预期响应:**
```json
{
  "code": 200,
  "msg": "夜行 - 任务拉取成功",
  "data": {
    "taskId": 1,
    "taskName": "又三郎 4K MV 抓取",
    "targetUrl": "https://youtube.com/watch?v=matasaburo_4k",
    "status": "RUNNING",
    "workerNode": "worker-node-01",
    "metaInfo": {
      "artist": "ヨルシカ",
      "song": "又三郎",
      "resolution": "4K"
    }
  }
}
```

### 5.3 查询任务详情
```bash
curl http://localhost:8080/api/v1/tasks/1
```

### 5.4 回报任务状态
```bash
# 成功完成
curl -X PUT http://localhost:8080/api/v1/tasks/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SUCCESS"}'

# 任务失败
curl -X PUT http://localhost:8080/api/v1/tasks/2/status \
  -H "Content-Type: application/json" \
  -d '{"status":"FAILED","errorLog":"春泥棒 - 网络超时导致下载失败"}'
```

### 5.5 创建新任务
```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "言って Live Tour 2024",
    "targetUrl": "https://youtube.com/watch?v=itte_live_2024",
    "metaInfo": {
      "artist": "ヨルシカ",
      "song": "言って",
      "type": "live",
      "resolution": "4K"
    }
  }'
```

---

## 🔥 第六步: 并发拉取测试

### 6.1 使用 Bash 脚本模拟 10 个节点并发拉取

创建文件 `test_concurrent.sh`:
```bash
#!/bin/bash
for i in {1..10}
do
  curl -s "http://localhost:8080/api/v1/tasks/pending?workerNode=worker-node-$(printf %02d $i)" &
done
wait
echo "并发测试完成"
```

运行:
```bash
bash test_concurrent.sh
```

### 6.2 验证无重复分配
每个节点应获取不同的任务 ID,无重复。

---

## 📊 常见问题排查

### 问题 1: 数据库连接失败
**错误信息:**
```
org.postgresql.util.PSQLException: Connection refused
```

**解决方案:**
1. 检查 PostgreSQL 服务是否启动
2. 验证 `application.yml` 中的连接信息
3. 检查防火墙设置

### 问题 2: MyBatis Mapper 找不到
**错误信息:**
```
Invalid bound statement (not found): com.nautilus.dispatch.mapper.SysMediaTaskMapper.pullPendingTask
```

**解决方案:**
1. 检查 `application.yml` 中的 `mapper-locations` 配置
2. 确认 XML 文件路径: `resources/mapper/SysMediaTaskMapper.xml`
3. 验证 namespace 与 Mapper 接口完全一致

### 问题 3: JSONB 字段映射失败
**错误信息:**
```
TypeException: Error setting non null for parameter #1 with JdbcType null
```

**解决方案:**
1. 确认 Entity 中 `@TableField(typeHandler = JacksonTypeHandler.class)` 注解
2. 检查 `application.yml` 中 `type-handlers-package` 配置
3. 确认 XML 中 resultMap 的 typeHandler 配置

### 问题 4: 端口占用
**错误信息:**
```
Port 8080 was already in use
```

**解决方案:**
修改 `application.yml` 中的端口:
```yaml
server:
  port: 8081
```

---

## 🎯 性能优化建议

### 1. 数据库连接池调优
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 根据并发量调整
      minimum-idle: 10
```

### 2. 关闭开发日志 (生产环境)
```yaml
logging:
  level:
    com.nautilus.dispatch: INFO
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

### 3. 添加 Redis 缓存
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## 📝 监控与日志

### 查看应用日志
```bash
tail -f logs/spring.log
```

### 数据库慢查询监控
```sql
-- 查看活跃连接
SELECT * FROM pg_stat_activity WHERE datname = 'nautilus_dispatch';

-- 查看索引使用情况
SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';
```

---

## ✅ 部署检查清单

- [ ] PostgreSQL 服务已启动
- [ ] 数据库 `nautilus_dispatch` 已创建
- [ ] 表结构已执行 (`schema.sql`)
- [ ] 测试数据已初始化 (`init-data.sql`)
- [ ] `application.yml` 配置已修改
- [ ] ruoyi-common 已 `mvn install`
- [ ] amy-dispatch-center 已构建成功
- [ ] 服务启动正常,端口无冲突
- [ ] 健康检查接口返回 200
- [ ] 任务拉取接口可正常工作

---

## 🎉 部署成功

恭喜！Java 任务调度控制面（示例）已成功部署。

**下一步:**
1. 开发工作节点 (elma-stream-worker) 调用拉取接口
2. 集成 Redis 实现分布式锁
3. 接入监控系统 (Prometheus + Grafana)
4. 配置日志收集 (ELK Stack)

---

> **夜行 - 愿每个任务都能顺利完成** 🌙
