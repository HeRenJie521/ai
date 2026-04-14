package com.eaju.ai.util;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 与业务端一致：全串 MD5（32 位小写十六进制）后取子串 [8, 24)，即 {@code substring(8, 24)} 共 16 字符。
 */
public final class DmsLoginPasswordCodec {

    private DmsLoginPasswordCodec() {
    }

    public static String encodeForDmsLogin(String rawPassword) {
        if (rawPassword == null) {
            rawPassword = "";
        }
        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        if (md5.length() < 24) {
            return md5;
        }
        return md5.substring(8, 24);
    }
}
