package com.futureself.generation;

import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.storage.StoredObject;
import com.futureself.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * 音频结果转存（实施文档 §15.5/§16）：
 * 供应商 URL 若可能过期，READY 后立即下载并转存到 StorageService（key: audio/{userId}/{storyId}/{uuid}.{ext}）。
 * 支持 file:（Mock fixture）与 http(s)（真实供应商）。
 */
@Service
public class AudioTransferService {

    private static final Logger log = LoggerFactory.getLogger(AudioTransferService.class);

    private final StorageService storageService;
    private final AudioUrlGuard urlGuard;
    private final HttpClient httpClient;

    public AudioTransferService(StorageService storageService, AudioUrlGuard urlGuard) {
        this.storageService = storageService;
        this.urlGuard = urlGuard;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 转存并返回存储元数据；失败抛 BusinessException（AUDIO_STORAGE_FAILED）。 */
    public StoredObject transfer(long userId, long storyId, AudioQueryResult queryResult) {
        String format = resolveFormat(queryResult);
        String key = "audio/%d/%d/%s.%s".formatted(userId, storyId, UUID.randomUUID(), format);
        try (InputStream input = open(queryResult.url())) {
            // 注意：open 前已由 urlGuard 校验 scheme/host/路径
            StoredObject saved = storageService.save(key, input, contentTypeOf(format));
            log.info("audio stored: key={} size={} storyId={}", saved.key(), saved.sizeBytes(), storyId);
            return saved;
        } catch (Exception e) {
            log.warn("audio transfer failed: storyId={} {}", storyId, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED);
        }
    }

    private InputStream open(String url) throws Exception {
        urlGuard.assertAllowed(url);
        if (url.startsWith("file:")) {
            return Files.newInputStream(Path.of(URI.create(url)));
        }
        HttpResponse<InputStream> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("audio download http " + response.statusCode());
        }
        return response.body();
    }

    private String resolveFormat(AudioQueryResult queryResult) {
        if (queryResult.format() != null && !queryResult.format().isBlank()) {
            return queryResult.format().toLowerCase();
        }
        String url = queryResult.url() == null ? "" : queryResult.url();
        int dot = url.lastIndexOf('.');
        if (dot > 0 && dot > url.lastIndexOf('/')) {
            String ext = url.substring(dot + 1);
            if (ext.matches("[a-zA-Z0-9]{2,5}")) {
                return ext.toLowerCase();
            }
        }
        return "mp3";
    }

    private String contentTypeOf(String format) {
        return switch (format) {
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            default -> "audio/mpeg";
        };
    }
}
