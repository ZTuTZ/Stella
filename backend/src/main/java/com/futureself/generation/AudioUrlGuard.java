package com.futureself.generation;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppAudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 音频结果 URL 安全校验（H-08）：
 * <ul>
 *   <li>file: 仅允许 mock fixture 目录与 tencent-sync 输出目录内（real path 防软链/.. 越界）</li>
 *   <li>http(s): 仅 https；host 必须命中配置 allowlist；解析后不得是 loopback/link-local/私网/组播地址</li>
 * </ul>
 * 日志不输出完整 URL（可能包含对象路径），只记录 host/scheme。
 */
@Component
public class AudioUrlGuard {

    private static final Logger log = LoggerFactory.getLogger(AudioUrlGuard.class);

    private final AppAudioProperties props;

    public AudioUrlGuard(AppAudioProperties props) {
        this.props = props;
    }

    /** 校验 URL；不通过时抛 AUDIO_STORAGE_FAILED（受控业务错误，不泄露内部路径）。 */
    public void assertAllowed(String url) {
        if (url == null || url.isBlank()) {
            reject("blank");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            reject("malformed");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if ("file".equals(scheme)) {
            assertLocalFileAllowed(uri);
            return;
        }
        if ("https".equals(scheme)) {
            assertRemoteAllowed(uri);
            return;
        }
        reject(scheme.isEmpty() ? "no-scheme" : scheme);
    }

    private void assertLocalFileAllowed(URI uri) {
        List<Path> allowedBases = List.of(
                Path.of(props.mockFixtureDir()).toAbsolutePath().normalize(),
                Path.of(props.syncOutputDir()).toAbsolutePath().normalize());
        try {
            Path file = Path.of(uri).toAbsolutePath().normalize();
            Path real = Files.exists(file) ? file.toRealPath() : file;
            for (Path base : allowedBases) {
                Path realBase = Files.exists(base) ? base.toRealPath() : base;
                if (real.startsWith(realBase)) {
                    return;
                }
            }
            reject("file-outside-allowed-dir");
        } catch (Exception e) {
            reject("file-unreadable");
        }
    }

    private void assertRemoteAllowed(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            reject("no-host");
            return;
        }
        if (!hostAllowed(host)) {
            reject("host-not-allowlisted");
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                reject("unresolvable");
                return;
            }
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    reject("private-address");
                    return;
                }
            }
        } catch (Exception e) {
            reject("unresolvable");
        }
    }

    private boolean hostAllowed(String host) {
        List<String> allowlist = Arrays.stream(props.resultHostAllowlist().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
        String normalized = host.toLowerCase();
        for (String allowed : allowlist) {
            if (normalized.equals(allowed) || normalized.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private void reject(String reason) {
        log.warn("audio url rejected: {}", reason);
        throw new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED, "audio url not allowed");
    }
}
