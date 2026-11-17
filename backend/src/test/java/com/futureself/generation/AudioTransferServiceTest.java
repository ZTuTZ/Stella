package com.futureself.generation;

import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.common.error.BusinessException;
import com.futureself.config.AppAudioProperties;
import com.futureself.storage.StorageService;
import com.futureself.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-08：转存前必须经过 URL 校验；合法 file fixture 可转存，越界路径/内网地址被拒。
 */
class AudioTransferServiceTest {

    @TempDir
    Path fixtureDir;

    private AudioTransferService service(StorageService storage) {
        AppAudioProperties props = new AppAudioProperties("mock", "", "", "501004", 0,
                fixtureDir.toString(), "", "", "ap-guangzhou", fixtureDir.toString(), "");
        return new AudioTransferService(storage, new AudioUrlGuard(props));
    }

    @Test
    void transfersAllowedLocalFixture() throws Exception {
        Path file = Files.writeString(fixtureDir.resolve("ok.mp3"), "audio-bytes");
        StorageService storage = mock(StorageService.class);
        when(storage.save(anyString(), any(InputStream.class), anyString()))
                .thenReturn(new StoredObject("audio/1/2/x.mp3", "/media/audio/1/2/x.mp3", "audio/mpeg", 11L));

        StoredObject saved = service(storage).transfer(1L, 2L, new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, file.toUri().toString(), "mp3", 1000L, null, null));
        assertThat(saved.key()).isEqualTo("audio/1/2/x.mp3");
    }

    @Test
    void rejectsEscapePathAndPrivateUrl() {
        StorageService storage = mock(StorageService.class);
        AudioTransferService svc = service(storage);
        assertThatThrownBy(() -> svc.transfer(1L, 2L, new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, "file:///etc/passwd", "mp3", null, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> svc.transfer(1L, 2L, new AudioQueryResult(
                AudioQueryResult.JobStatus.READY, "http://127.0.0.1/a.mp3", "mp3", null, null, null)))
                .isInstanceOf(BusinessException.class);
    }
}
