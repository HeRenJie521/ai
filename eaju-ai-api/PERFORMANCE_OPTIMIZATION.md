# 服务端性能优化指南

## 优化概览

本次优化涵盖以下方面：
1. 数据库连接池（HikariCP）优化
2. Redis 连接池优化
3. JPA/Hibernate 性能优化
4. 多线程池优化
5. REST 客户端连接池优化
6. Tomcat 服务器性能调优
7. Jackson 序列化优化

## 详细优化项

### 1. 数据库连接池（HikariCP）

#### 开发环境配置
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # 最大连接数
      minimum-idle: 5                # 最小空闲连接
      connection-timeout: 30000      # 连接超时 30 秒
      idle-timeout: 600000           # 空闲连接存活 10 分钟
      max-lifetime: 1800000          # 连接最大存活 30 分钟
      connection-test-query: SELECT 1
      initialization-fail-timeout: -1
```

#### 生产环境配置
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:50}    # 可通过环境变量调整
      minimum-idle: ${HIKARI_MIN_IDLE:10}
      leak-detection-threshold: 60000  # 连接泄漏检测 60 秒
```

**调优建议：**
- `maximum-pool-size`：根据数据库服务器性能和并发需求调整，一般 20-100
- `minimum-idle`：建议为 maximum-pool-size 的 1/5 到 1/2
- 生产环境启用 `leak-detection-threshold` 检测连接泄漏

### 2. Redis 连接池（Lettuce）

#### 开发环境
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20      # 最大连接数
        max-idle: 10        # 最大空闲连接
        min-idle: 5         # 最小空闲连接
        max-wait: 3000ms    # 获取连接最大等待时间
```

#### 生产环境
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: ${REDIS_MAX_ACTIVE:50}
        max-idle: ${REDIS_MAX_IDLE:20}
        min-idle: ${REDIS_MIN_IDLE:10}
        max-wait: 3000ms
```

**调优建议：**
- 高并发场景可以增加 `max-active` 到 50-100
- `max-wait` 不宜设置过大，避免请求堆积

### 3. JPA/Hibernate 性能优化

```yaml
spring:
  jpa:
    open-in-view: false    # 禁用 OSIV，避免长连接占用数据库连接
    properties:
      hibernate:
        order_inserts: true      # 批量插入优化
        order_updates: true      # 批量更新优化
        jdbc:
          batch_size: 50         # 批量操作大小
        generate_statistics: false  # 关闭统计信息，提升性能
```

**优化说明：**
- `order_inserts/updates`：Hibernate 会排序 SQL 语句以提升批量操作效率
- `batch_size: 50`：每 50 条记录批量提交，减少数据库交互
- `generate_statistics: false`：生产环境关闭性能统计

### 4. 多线程池优化

创建多个专用线程池，替代单一固定线程池：

#### 通用异步任务线程池
```java
@Bean(name = "taskExecutor")
public Executor taskExecutor() {
    // core-size: 8, max-size: 32, queue: 1000
}
```

#### IO 密集型线程池
```java
@Bean(name = "ioExecutor")
public Executor ioExecutor() {
    // CPU 核心数 * 2 ~ * 4，适合数据库查询、外部 API 调用
}
```

#### ChatStream 线程池
```java
@Bean
public ExecutorService chatStreamExecutor() {
    // 根据 CPU 核心数动态调整，最少 16 个线程
    int poolSize = Math.max(16, cpuCores * 2);
}
```

#### 缓存管理线程池
```java
@Bean(name = "cacheExecutor")
public Executor cacheExecutor() {
    // 4-8 个线程，专用于 Redis 缓存操作
}
```

**环境变量配置：**
```bash
THREAD_POOL_CORE=8        # 核心线程数
THREAD_POOL_MAX=32        # 最大线程数
THREAD_POOL_QUEUE=1000    # 队列容量
```

### 5. REST 客户端连接池（Apache HttpClient）

```yaml
app:
  http-client:
    connect-timeout-ms: 10000              # 连接超时 10 秒
    read-timeout-ms: 120000                # 读取超时 120 秒
    connection-request-timeout-ms: 5000    # 获取连接超时 5 秒
    max-total: 100                         # 总连接数
    max-per-route: 20                      # 单个路由最大连接数
    validate-after-inactivity-ms: 1000     # 空闲后验证连接
    time-to-live-minutes: 10               # 连接存活时间
```

**优化说明：**
- 使用 Apache HttpClient 连接池替代简单的 RestTemplate
- 自动清理空闲和过期连接
- 连接复用，减少 TCP 握手开销

**环境变量配置：**
```bash
HTTP_MAX_TOTAL=100          # 总连接池大小
HTTP_MAX_PER_ROUTE=20       # 单个目标服务器最大连接
```

### 6. Tomcat 服务器性能调优

```yaml
server:
  tomcat:
    max-threads: ${SERVER_MAX_THREADS:200}          # 最大工作线程
    min-spare-threads: ${SERVER_MIN_SPARE_THREADS:20}  # 最小空闲线程
    max-connections: ${SERVER_MAX_CONNECTIONS:10000}   # 最大连接数
    accept-count: ${SERVER_ACCEPT_COUNT:100}           # 等待队列长度
    connection-timeout: 20000                          # 连接超时 20 秒
    compression: "on"                                  # 启用压缩
    compressable-mime-types: "application/json,..."    # 压缩类型
    compression-min-size: 1024                         # 最小压缩大小
```

**调优建议：**
- `max-threads`：根据 CPU 核心数和业务类型调整，建议 200-500
- `max-connections`：Tomcat 能处理的最大并发连接数
- `accept-count`：超过 max-connections 后的等待队列，建议 100-200
- 启用压缩可以减少 60-80% 的响应体积

**环境变量配置：**
```bash
SERVER_MAX_THREADS=200
SERVER_MIN_SPARE_THREADS=20
SERVER_MAX_CONNECTIONS=10000
SERVER_ACCEPT_COUNT=100
```

### 7. Jackson 序列化优化

```yaml
spring:
  jackson:
    default-property-inclusion: non_null    # 忽略 null 字段
    serialization:
      write-dates-as-timestamps: false      # 日期不转时间戳
      fail-on-empty-beans: false            # 空对象不报错
    deserialization:
      fail-on-unknown-properties: false     # 忽略未知属性
```

## 生产环境部署建议

### 环境变量清单

部署时需要配置以下环境变量：

```bash
# 数据库
SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/eaju_ai
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password
HIKARI_MAX_POOL_SIZE=50
HIKARI_MIN_IDLE=10

# Redis
REDIS_HOST=redis-host
REDIS_PASSWORD=your_redis_password
REDIS_MAX_ACTIVE=50
REDIS_MAX_IDLE=20
REDIS_MIN_IDLE=10

# 服务器
SERVER_PORT=8080
SERVER_MAX_THREADS=200
SERVER_MIN_SPARE_THREADS=20

# HTTP 客户端
HTTP_MAX_TOTAL=100
HTTP_MAX_PER_ROUTE=20

# 线程池
THREAD_POOL_CORE=8
THREAD_POOL_MAX=32
THREAD_POOL_QUEUE=1000

# JWT（生产务必使用强密钥）
APP_JWT_SECRET=your-strong-secret-key-at-least-32-bytes
```

### JVM 启动参数建议

```bash
java -jar eaju-ai-api-1.0.0-SNAPSHOT.jar \
  -Xms512m \                          # 初始堆内存
  -Xmx2g \                            # 最大堆内存
  -XX:+UseG1GC \                      # 使用 G1 垃圾回收器
  -XX:MaxGCPauseMillis=200 \          # 最大 GC 暂停时间
  -XX:+HeapDumpOnOutOfMemoryError \   # OOM 时导出堆转储
  -XX:HeapDumpPath=/var/log/heapdump.hprof
```

## 性能监控建议

1. **启用 Spring Boot Actuator**（可选）
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   ```

2. **监控指标**
   - 数据库连接池使用率（HikariPool 指标）
   - Redis 连接池使用率
   - 线程池活跃线程数
   - HTTP 连接池使用情况
   - 响应时间分布

3. **日志级别调整**
   ```yaml
   logging:
     level:
       com.zaxxer.hikari: INFO  # 连接池日志
       org.hibernate.SQL: WARN  # 生产关闭 SQL 日志
       org.hibernate.type: WARN # 生产关闭参数日志
   ```

## 性能测试建议

使用工具进行压力测试：
- **Apache JMeter**：模拟并发用户
- **wrk**：HTTP 基准测试
- **Gatling**：高性能负载测试

测试场景：
1. 正常负载（50-100 并发）
2. 峰值负载（200-500 并发）
3. 长时间稳定性测试（24 小时+）

## 优化效果预期

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 数据库连接池 | 默认 10 | 20-50 | 2-5x |
| HTTP 连接复用 | 无 | 连接池 | 30-50% |
| 批量操作性能 | 单条 | 批量 50 | 5-10x |
| 线程并发能力 | 16 固定 | 多池动态 | 3-5x |
| 响应体积 | 原始 | 压缩 | 60-80% ↓ |

**综合预期：系统吞吐量提升 3-5 倍，响应时间降低 40-60%**
