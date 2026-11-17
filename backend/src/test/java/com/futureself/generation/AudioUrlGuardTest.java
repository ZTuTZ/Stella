package com.futureself.generation;

import com.futureself.common.error.BusinessException;
import com.futureself.config.AppAudioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H-08：SSRF / 任意 file 读取防护。
 */
class AudioUrlGuardTest {

    @TempDir
    Path fixtureDir;

    @TempDir
    Path syncDir;

    @TempDir
    Path otherDir;

    private AudioUrlGuard guard(String allowlist) {
        return new AudioUrlGuard(new AppAudioProperties(
                "mock", "", "", "501004", 0, fixtureDir.toString(),
                "", "", "ap-guangzhou", syncDir.toString(), allowlist));
    }

    @Test
    void allowsFileInsideFixtureDir() throws Exception {
        Path file = Files.writeString(fixtureDir.resolve("a.mp3"), "x");
        assertThatCode(() -> guard("").assertAllowed(file.toUri().toString())).doesNotThrowAnyException();
        // 允许目录内的软链指向目录外文件 → 必须拒绝（real path 校验）
        Path outside = Files.writeString(otherDir.resolve("secret.mp3"), "secret");
        Path link = Files.createSymbolicLink(fixtureDir.resolve("link.mp3"), outside);
        assertThatThrownBy(() -> guard("").assertAllowed(link.toUri().toString()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsFileInsideSyncOutputDir() throws Exception {
        Path file = Files.writeString(syncDir.resolve("b.mp3"), "x");
        assertThatCode(() -> guard("").assertAllowed(file.toUri().toString())).doesNotThrowAnyException();
    }

    @Test
    void rejectsArbitraryFilePath() {
        assertThatThrownBy(() -> guard("").assertAllowed("file:///etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard("").assertAllowed("file://" + otherDir.resolve("escape.mp3")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInsecureAndPrivateHosts() {
        assertThatThrownBy(() -> guard("127.0.0.1").assertAllowed("http://127.0.0.1/a.mp3"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard("169.254.169.254").assertAllowed("https://169.254.169.254/meta"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard("localhost").assertAllowed("https://localhost/a.mp3"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNonAllowlistedHostAndAllowsListedPublicHost() {
        assertThatThrownBy(() -> guard("93.184.216.34").assertAllowed("https://evil.example.com/a.mp3"))
                .isInstanceOf(BusinessException.class);
        // 允许：https + allowlist 命中 + 公网字面量 IP（无需 DNS）
        assertThatCode(() -> guard("93.184.216.34").assertAllowed("https://93.184.216.34/a.mp3"))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyAllowlistRejectsAllRemoteUrls() {
        assertThatThrownBy(() -> guard("").assertAllowed("https://93.184.216.34/a.mp3"))
                .isInstanceOf(BusinessException.class);
    }
}
