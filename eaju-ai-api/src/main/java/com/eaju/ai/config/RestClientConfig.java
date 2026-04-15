package com.eaju.ai.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

/**
 * REST 客户端性能优化配置
 * 使用 Apache HttpClient 连接池提升性能
 */
@Configuration
public class RestClientConfig {

    @Value("${app.http-client.connect-timeout-ms:10000}")
    private int connectTimeout;

    @Value("${app.http-client.read-timeout-ms:120000}")
    private int readTimeout;

    @Value("${app.http-client.connection-request-timeout-ms:5000}")
    private int connectionRequestTimeout;

    @Value("${app.http-client.max-total:100}")
    private int maxTotal;

    @Value("${app.http-client.max-per-route:20}")
    private int maxPerRoute;

    @Value("${app.http-client.validate-after-inactivity-ms:1000}")
    private int validateAfterInactivity;

    @Value("${app.http-client.time-to-live-minutes:10}")
    private int timeToLiveMinutes;

    @Bean
    public RestTemplate restTemplate() {
        // 配置连接池
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        connectionManager.setValidateAfterInactivity(validateAfterInactivity);

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setExpectContinueEnabled(false)
                .build();

        // 构建 HttpClient
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionTimeToLive(timeToLiveMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .evictIdleConnections(30, java.util.concurrent.TimeUnit.SECONDS)
                .evictExpiredConnections()
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}
