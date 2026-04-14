package com.eaju.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 将文件转发到 eaju-open 上传接口，供前端同源上传并拿到公网 URL（供多模态模型使用）。
 */
@Service
public class EajuOpenUploadService {

    private static final Logger log = LoggerFactory.getLogger(EajuOpenUploadService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.file-upload.proxy-url:https://wx.51eanj.com/eaju-open-api/api/file/upload/file}")
    private String proxyUrl;

    public EajuOpenUploadService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String forwardAndExtractUrl(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String filename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "upload.bin";
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("file", resource);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<MultiValueMap<String, Object>>(body, headers);
        ResponseEntity<String> resp;
        try {
            resp = restTemplate.postForEntity(proxyUrl, entity, String.class);
        } catch (Exception ex) {
            log.warn("转发上传失败: {}", ex.getMessage());
            throw new IllegalStateException("上传服务暂不可用: " + ex.getMessage());
        }
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("上传返回异常 HTTP " + resp.getStatusCodeValue());
        }
        return parseHttpUrl(resp.getBody());
    }

    private String parseHttpUrl(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        int code = root.path("code").asInt(0);
        boolean success = root.path("success").asBoolean(false);
        if (code != 200 || !success) {
            String msg = root.path("message").asText(root.path("msg").asText("上传失败"));
            throw new IllegalStateException(msg);
        }
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            throw new IllegalStateException("上传返回无 result");
        }
        String url = textOrNull(result.get("httpUrl"));
        if (!StringUtils.hasText(url)) {
            url = textOrNull(result.get("url"));
        }
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("上传返回无 URL");
        }
        return url.trim();
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull() || !n.isTextual()) {
            return null;
        }
        return n.asText();
    }
}
