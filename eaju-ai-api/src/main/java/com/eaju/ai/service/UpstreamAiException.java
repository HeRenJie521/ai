package com.eaju.ai.service;

/**
 * 调用大模型上游失败时抛出，可携带上游 HTTP 状态码便于网关层映射。
 */
public class UpstreamAiException extends RuntimeException {

    /** 上游 HTTP 状态码；未知时为 -1 */
    private final int upstreamHttpStatus;

    public UpstreamAiException(String message, Throwable cause, int upstreamHttpStatus) {
        super(message, cause);
        this.upstreamHttpStatus = upstreamHttpStatus;
    }

    public int getUpstreamHttpStatus() {
        return upstreamHttpStatus;
    }
}
