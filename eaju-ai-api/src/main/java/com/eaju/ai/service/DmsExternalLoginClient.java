package com.eaju.ai.service;

import com.eaju.ai.util.DmsLoginPasswordCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 调用 DMS {@code appUserLogin}：GET {@code DmsInterface?method=appUserLogin&data={json}}。
 */
@Component
public class DmsExternalLoginClient {

    private static final Logger log = LoggerFactory.getLogger(DmsExternalLoginClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String loginMethod;
    private final String loginType;

    public DmsExternalLoginClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.auth.dms-base-url}") String baseUrl,
            @Value("${app.auth.dms-login-method:appUserLogin}") String loginMethod,
            @Value("${app.auth.dms-login-type:1}") String loginType) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.trim();
        this.loginMethod = loginMethod.trim();
        this.loginType = loginType.trim();
    }

    /**
     * @return 登录成功后的 JSON 根节点（由上层解析手机号等）
     */
    public JsonNode login(String phone, String rawPassword) throws Exception {
        String pwd = DmsLoginPasswordCodec.encodeForDmsLogin(rawPassword != null ? rawPassword : "");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("phone", phone.trim());
        payload.put("password", pwd);
        payload.put("type", loginType);
        String dataJson = objectMapper.writeValueAsString(payload);

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("method", loginMethod)
                .queryParam("data", dataJson)
                .build()
                .encode()
                .toUri();

        log.info("[DMS 密码登录] 请求 URL: {}", uri.toString());
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("登录服务返回异常状态: " + response.getStatusCodeValue());
            }
            String body = response.getBody().trim();
            if (!StringUtils.hasText(body)) {
                throw new IllegalArgumentException("登录服务返回空内容");
            }
            if (body.startsWith("<")) {
                log.warn("登录接口返回非 JSON（疑似 HTML）: {}", body.substring(0, Math.min(120, body.length())));
                throw new IllegalArgumentException("登录服务返回非 JSON");
            }
            return objectMapper.readTree(body);
        } catch (RestClientException ex) {
            log.warn("调用登录接口失败: {}", ex.toString());
            throw new IllegalArgumentException("无法连接登录服务，请稍后重试");
        }
    }

    /**
     * 使用指定 loginType 登录（用于免密登录场景）。
     * @param phone 手机号
     * @param loginType 登录类型（如 "22" 表示免密登录）
     * @return 登录成功后的 JSON 根节点
     */
    public JsonNode loginWithLoginType(String phone, String loginType) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("phone", phone.trim());
        payload.put("type", loginType);
        String dataJson = objectMapper.writeValueAsString(payload);

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("method", loginMethod)
                .queryParam("data", dataJson)
                .build()
                .encode()
                .toUri();

        log.info("[DMS 免密登录] 请求 URL: {}", uri.toString());
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("登录服务返回异常状态：" + response.getStatusCodeValue());
            }
            String body = response.getBody().trim();
            if (!StringUtils.hasText(body)) {
                throw new IllegalArgumentException("登录服务返回空内容");
            }
            if (body.startsWith("<")) {
                log.warn("登录接口返回非 JSON（疑似 HTML）: {}", body.substring(0, Math.min(120, body.length())));
                throw new IllegalArgumentException("登录服务返回非 JSON");
            }
            return objectMapper.readTree(body);
        } catch (RestClientException ex) {
            log.warn("调用登录接口失败：{}", ex.toString());
            throw new IllegalArgumentException("无法连接登录服务，请稍后重试");
        }
    }
}
