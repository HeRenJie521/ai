package com.eaju.ai.web;

import com.eaju.ai.service.EajuOpenUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * 代理上传至 eaju-open-api，返回公网可访问的 {@code url}，供视觉模型与落库。
 */
@RestController
@RequestMapping("/api/file")
public class FileUploadController {

    private final EajuOpenUploadService eajuOpenUploadService;

    public FileUploadController(EajuOpenUploadService eajuOpenUploadService) {
        this.eajuOpenUploadService = eajuOpenUploadService;
    }

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5 MB

    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 5 MB，当前：" + (file.getSize() / 1024 / 1024) + " MB");
        }
        String url = eajuOpenUploadService.forwardAndExtractUrl(file);
        return Collections.singletonMap("url", url);
    }
}
