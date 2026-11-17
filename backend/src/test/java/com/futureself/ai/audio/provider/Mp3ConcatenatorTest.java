package com.futureself.ai.audio.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** MP3 拼接与 ID3 剥离。 */
class Mp3ConcatenatorTest {

    @Test
    void concatenatesByteStreams() {
        byte[] a = "ID3AAAA".getBytes(StandardCharsets.UTF_8);
        byte[] b = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x20};
        byte[] result = Mp3Concatenator.concat(List.of(a, b));
        assertThat(result.length).isEqualTo(a.length + b.length);
    }

    @Test
    void stripsLeadingId3Tag() {
        // 构造 ID3v2.3 头：'ID3' + version(2) + flags(1) + size(4，7 位编码)
        byte[] data = new byte[10 + 4 + 4];
        data[0] = 'I';
        data[1] = 'D';
        data[2] = '3';
        data[3] = 3;
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 0;
        data[8] = 0;
        data[9] = 4;
        byte[] frame = {(byte) 0xFF, (byte) 0xFB, 1, 2};
        System.arraycopy(frame, 0, data, 14, 4);

        byte[] stripped = Mp3Concatenator.stripLeadingId3(data);
        assertThat(stripped).containsExactly(frame);
    }

    @Test
    void keepsDataWithoutId3() {
        byte[] data = {(byte) 0xFF, (byte) 0xFB, 1, 2, 3};
        assertThat(Mp3Concatenator.stripLeadingId3(data)).isSameAs(data);
    }
}
