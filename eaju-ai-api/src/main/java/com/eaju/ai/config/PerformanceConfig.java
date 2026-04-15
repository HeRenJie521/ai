package com.eaju.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能优化配置
 * 包含多个线程池和异步处理配置
 */
@Configuration
@EnableAsync
public class PerformanceConfig {

    @Value("${app.thread-pool.core-size:8}")
    private int corePoolSize;

    @Value("${app.thread-pool.max-size:32}")
    private int maxPoolSize;

    @Value("${app.thread-pool.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${app.thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * 通用异步任务线程池
     * 用于处理后台异步任务
     */
    @Bean(name = "taskExecutor", destroyMethod = "shutdown")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("async-task-");
        executor.setDaemon(true);
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * IO 密集型任务线程池
     * 用于处理数据库查询、外部 API 调用等 IO 操作
     * IO 密集型推荐配置：CPU 核心数 * 2
     */
    @Bean(name = "ioExecutor", destroyMethod = "shutdown")
    public Executor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cpuCores * 2);
        executor.setMaxPoolSize(cpuCores * 4);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("io-task-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * ChatStream 线程池配置（优化版）
     * 用于处理聊天流式响应
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "chat-stream-" + n.getAndIncrement());
                t.setDaemon(true);
                // 设置较低的线程优先级，避免影响其他任务
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
        // 根据 CPU 核心数动态调整线程数
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(16, cpuCores * 2);
        return Executors.newFixedThreadPool(poolSize, tf);
    }

    /**
     * 缓存管理线程池
     * 用于处理 Redis 缓存操作
     */
    @Bean(name = "cacheExecutor", destroyMethod = "shutdown")
    public Executor cacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cache-task-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
