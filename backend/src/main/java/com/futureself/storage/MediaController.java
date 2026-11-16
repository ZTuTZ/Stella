package com.futureself.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 受控静态媒体路由（实施文档 §16.2）。
 * 仅通过 key 校验 + 目录越界防护暴露 storage 文件；不开放任意本地路径。
 * 禁止把完整 access token 等敏感内容经此路由暴露；仅用于音频等业务媒体。
 */
@RestController
public class MediaController {

    private final LocalStorageService storage;

    public MediaController(LocalStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/media/**")
    public ResponseEntity<FileSystemResource> serve(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/media/");
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String key = uri.substring(idx + "/media/".length());
        try {
            storage.validateKey(key);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Path file = storage.resolve(key);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }
}
