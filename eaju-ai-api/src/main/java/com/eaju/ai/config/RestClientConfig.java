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
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;

/**
 * REST 客户端性能优化配置
 * 使用 Apache HttpClient 连接池提升性能
 * 支持本地部署的 HTTPS 服务（忽略 SSL 证书验证）
 */
@Configuration
public class RestClientConfig {

    @Value("${app.http-client.connect-timeout-ms:30000}")
    private int connectTimeout;

    @Value("${app.http-client.read-timeout-ms:10000}")
    private int readTimeout;

    @Value("${app.http-client.connection-request-timeout-ms:10000}")
    private int connectionRequestTimeout;

    @Value("${app.http-client.max-total:200}")
    private int maxTotal;

    @Value("${app.http-client.max-per-route:50}")
    private int maxPerRoute;

    @Value("${app.http-client.validate-after-inactivity-ms:1000}")
    private int validateAfterInactivity;

    @Value("${app.http-client.time-to-live-minutes:10}")
    private int timeToLiveMinutes;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // 配置 SSL 上下文（信任所有证书，用于本地部署的 HTTPS 服务）
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (chain, authType) -> true)
                .build();

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
                .setExpectContinueEnabled(false)  // 禁用 Expect: 100-continue，避免 chunked 编码问题
                .setStaleConnectionCheckEnabled(true)  // 启用过期连接检查
                .build();

        // 配置 SSL Socket Factory
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1.2", "TLSv1.3"},
                null,
                NoopHostnameVerifier.INSTANCE // 忽略主机名验证
        );

        // 构建 HttpClient
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionTimeToLive(timeToLiveMinutes, java.util.concurrent.TimeUnit.MINUTES)
                .evictIdleConnections(30, java.util.concurrent.TimeUnit.SECONDS)
                .evictExpiredConnections()
                .setSSLSocketFactory(socketFactory)
                .disableRedirectHandling()  // 禁用自动重定向
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate template = new RestTemplate(factory);
        // Spring Boot 2.7 / Java 8: StringHttpMessageConverter 默认 ISO-8859-1，
        // 业务系统返回 Content-Type: application/json 时不带 charset，导致中文 key 被乱码。
        // 统一改为 UTF-8，确保中文字段名与字段值都能正确读取。
        template.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });
        return template;
    }
}
