package com.eaju.ai.web;

import com.eaju.ai.service.UpstreamAiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通过 {@link HttpServletResponse} 直接写 JSON，避免客户端 {@code Accept: text/event-stream}（如 SSE 聊天）
 * 时 {@code ResponseEntity} 走消息转换器触发 {@code HttpMediaTypeNotAcceptableException}。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final ObjectMapper objectMapper;

    public ApiExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, Map<String, Object> body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.resetBuffer();
        } catch (IllegalStateException ignored) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleUnreadable(HttpMessageNotReadableException ex, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", "请求体无法解析: " + ex.getMostSpecificCause().getMessage());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        writeJson(response, HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException ex, HttpServletResponse response) throws IOException {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", msg);
        body.put("status", HttpStatus.BAD_REQUEST.value());
        writeJson(response, HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleBadRequest(IllegalArgumentException ex, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", ex.getMessage());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        writeJson(response, HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public void handleConfig(IllegalStateException ex, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", ex.getMessage());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        writeJson(response, HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(UpstreamAiException.class)
    public void handleUpstream(UpstreamAiException ex, HttpServletResponse response) throws IOException {
        HttpStatus status = mapUpstreamToResponseStatus(ex.getUpstreamHttpStatus());
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", ex.getMessage());
        body.put("status", status.value());
        body.put("upstreamHttpStatus", ex.getUpstreamHttpStatus());
        writeJson(response, status, body);
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneric(Exception ex, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("error", ex.getMessage());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR, body);
    }

    /**
     * 将常见上游鉴权/配额问题映射为同码，便于与 502 区分；其余非 2xx 仍走 502。
     */
    private static HttpStatus mapUpstreamToResponseStatus(int upstream) {
        if (upstream == 401 || upstream == 403 || upstream == 429) {
            return HttpStatus.valueOf(upstream);
        }
        if (upstream >= 400 && upstream < 500) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (upstream >= 500) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
