package com.futureself.ai.audio.provider;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * MP3 片段拼接：逐段去掉开头 ID3v2 标签后按字节串联。
 *
 * <p>说明：腾讯云基础合成返回的 mp3 为完整文件；直接拼接在绝大多数解码器可正常播放。
 * 去除后续片段的 ID3 标签可避免中途出现元数据块导致的兼容性问题。
 */
public final class Mp3Concatenator {

    private Mp3Concatenator() {
    }

    public static byte[] concat(List<byte[]> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            if (part == null || part.length == 0) {
                continue;
            }
            out.writeBytes(stripLeadingId3(part));
        }
        return out.toByteArray();
    }

    /** 去掉开头的 ID3v2 标签（若有）；无标签原样返回。 */
    static byte[] stripLeadingId3(byte[] data) {
        if (data.length > 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int size = ((data[6] & 0x7F) << 21)
                    | ((data[7] & 0x7F) << 14)
                    | ((data[8] & 0x7F) << 7)
                    | (data[9] & 0x7F);
            int footer = (data[5] & 0x10) != 0 ? 10 : 0;
            int total = 10 + size + footer;
            if (total > 0 && total < data.length) {
                return Arrays.copyOfRange(data, total, data.length);
            }
        }
        return data;
    }
}
