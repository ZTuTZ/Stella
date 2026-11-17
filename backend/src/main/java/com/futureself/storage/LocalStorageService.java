package com.futureself.storage;

import com.futureself.config.AppStorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 本地文件存储实现（dev，实施文档 §16.2）。
 * 保存到 app.storage.local-dir（默认 ./data/media/），通过受控 /media 路由访问。
 * key 校验统一走 {@link #validateKey(String)}，杜绝路径穿越。
 */
@Service
public class LocalStorageService implements StorageService {

    private final AppStorageProperties properties;
    private final Path baseDir;

    public LocalStorageService(AppStorageProperties properties) {
        this.properties = properties;
        this.baseDir = Path.of(properties.localDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject save(String key, InputStream input, String contentType) {
        validateKey(key);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = input) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(target);
            return new StoredObject(key, publicUrl(key), contentType, size);
        } catch (IOException e) {
            throw new IllegalStateException("storage save failed for key: " + key, e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        validateKey(key);
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredObject(key, publicUrl(key), Files.probeContentType(target), Files.size(target)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("storage delete failed for key: " + key, e);
        }
    }

    /** key 规则：字母开头；仅 [A-Za-z0-9._/-]；禁止 ".."、空白与连续斜杠；长度 3~500。 */
    public void validateKey(String key) {
        if (key == null
                || key.length() < 3
                || key.length() > 500
                || !key.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                || key.contains("..")
                || key.contains("//")
                || key.endsWith("/")) {
            throw new IllegalArgumentException("invalid storage key");
        }
    }

    /** 解析 key 到基础目录内的绝对路径；越界即拒绝（双保险，key 正则已禁止 ".."）。 */
    public Path resolve(String key) {
        validateKey(key);
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("storage key escapes base dir");
        }
        return resolved;
    }

    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + key;
    }

    @Override
    public String createReadUrl(String key, java.time.Duration ttl) {
        // 本地开发/测试：受控 /media 路由（由 MediaController 提供，仅 local/test profile）
        return publicUrl(key);
    }
}
