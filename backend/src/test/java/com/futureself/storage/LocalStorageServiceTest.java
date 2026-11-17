package com.futureself.storage;

import com.futureself.config.AppStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1：本地存储 save/get/delete 往返 + 路径穿越防护。
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorageService(new AppStorageProperties("local", tempDir.toString(), "/media", 15));
    }

    @Test
    void saveGetDeleteRoundtrip() throws Exception {
        byte[] content = "fake-mp3-bytes".getBytes(StandardCharsets.UTF_8);
        String key = "audio/1/2/abc123.mp3";

        StoredObject saved = storage.save(key, new ByteArrayInputStream(content), "audio/mpeg");
        assertThat(saved.key()).isEqualTo(key);
        assertThat(saved.url()).isEqualTo("/media/audio/1/2/abc123.mp3");
        assertThat(saved.sizeBytes()).isEqualTo(content.length);
        assertThat(Files.isRegularFile(tempDir.resolve(key))).isTrue();

        Optional<StoredObject> got = storage.get(key);
        assertThat(got).isPresent();
        assertThat(got.get().sizeBytes()).isEqualTo(content.length);

        storage.delete(key);
        assertThat(storage.get(key)).isEmpty();
        // 重复删除静默成功（deleteIfExists 语义）：再次调用不抛异常即通过
        storage.delete(key);
    }

    @Test
    void rejectsTraversalKeys() {
        assertThatThrownBy(() -> storage.save("../evil.mp3", new ByteArrayInputStream(new byte[0]), "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("a/../b.mp3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("a//b.mp3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("audio/1/x.mp3/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("/abs/path.mp3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("a b.mp3")).isInstanceOf(IllegalArgumentException.class);
    }
}
