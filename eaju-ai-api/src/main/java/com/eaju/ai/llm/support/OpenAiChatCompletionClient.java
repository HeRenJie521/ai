package com.eaju.ai.llm.support;

import com.eaju.ai.service.UpstreamAiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * OpenAI Chat Completions 形态 HTTP 调用（阻塞 JSON 与 SSE 流式）。
 */
@Component
public class OpenAiChatCompletionClient {

    private static final String COMPLETIONS_PATH = "/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public JsonNode post(String baseUrl, String apiKey, ObjectNode body) {
        String url = trimTrailingSlash(baseUrl) + COMPLETIONS_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url,
                    new org.springframework.http.HttpEntity<>(body, headers),
                    JsonNode.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw upstreamException(ex);
        }
    }

    public void postStream(String baseUrl, String apiKey, ObjectNode body, SseEmitter emitter) throws IOException {
        postStream(baseUrl, apiKey, body, emitter, null, null);
    }

    /**
     * 流式：请求体须已含 {@code "stream": true}；将上游 SSE 按行转发为 {@code event: chunk}，结束发 {@code event: done}。
     *
     * @param onEachChunkJson 每收到一条上游 {@code data:} JSON（不含 [DONE]）时回调，可为 null
     * @param upstreamHolder  非空时写入当前上游 HTTP 响应（可关闭），供客户端断开时在 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter#onCompletion} 中关闭以尽快结束上游拉流
     */
    public void postStream(String baseUrl, String apiKey, ObjectNode body, SseEmitter emitter,
                           Consumer<String> onEachChunkJson) throws IOException {
        postStream(baseUrl, apiKey, body, emitter, onEachChunkJson, null);
    }

    /**
     * @param upstreamHolder 见 {@link #postStream(String, String, ObjectNode, SseEmitter, Consumer)}
     */
    public void postStream(String baseUrl, String apiKey, ObjectNode body, SseEmitter emitter,
                           Consumer<String> onEachChunkJson,
                           AtomicReference<Closeable> upstreamHolder) throws IOException {
        if (!body.path("stream").asBoolean(false)) {
            body.put("stream", true);
        }
        String url = trimTrailingSlash(baseUrl) + COMPLETIONS_PATH;
        URI uri = URI.create(url);
        try {
            restTemplate.execute(uri, HttpMethod.POST, request -> {
                HttpHeaders h = request.getHeaders();
                h.setContentType(MediaType.APPLICATION_JSON);
                h.setBearerAuth(apiKey.trim());
                h.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));
                objectMapper.writeValue(request.getBody(), body);
            }, clientHttpResponse -> {
                if (upstreamHolder != null) {
                    upstreamHolder.set(clientHttpResponse);
                }
                try {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(clientHttpResponse.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                continue;
                            }
                            if (line.startsWith(":")) {
                                continue;
                            }
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    if (!sseSend(emitter, "done", "[DONE]")) {
                                        break;
                                    }
                                    break;
                                }
                                if (onEachChunkJson != null) {
                                    onEachChunkJson.accept(data);
                                }
                                if (!sseSend(emitter, "chunk", data)) {
                                    break;
                                }
                            } else {
                                if (!sseSend(emitter, "line", line)) {
                                    break;
                                }
                            }
                        }
                    }
                } finally {
                    if (upstreamHolder != null) {
                        Closeable held = upstreamHolder.getAndSet(null);
                        if (held != null) {
                            try {
                                held.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
                return null;
            });
        } catch (HttpStatusCodeException ex) {
            throw upstreamException(ex);
        }
    }

    /**
     * @return false 表示下游已不可写（用户停止/断开），应结束上游读取
     */
    private static boolean sseSend(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException ignored) {
            return false;
        }
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(OpenAiChatCompletionClient.class);

    private static UpstreamAiException upstreamException(HttpStatusCodeException ex) {
        String detail = ex.getResponseBodyAsString();
        String msg = "上游返回错误: HTTP " + ex.getRawStatusCode()
                + (StringUtils.hasText(detail) ? " — " + detail : "");
        log.error("[LLM错误] {}", msg);
        return new UpstreamAiException(msg, ex, ex.getRawStatusCode());
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String s = baseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
