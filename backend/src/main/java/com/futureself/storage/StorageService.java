package com.futureself.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * 文件存储抽象（实施文档 §16）。业务层不得依赖具体 SDK。
 * key 约定示例：audio/{userId}/{storyId}/{uuid}.mp3
 */
public interface StorageService {

    /** 保存对象，返回包含稳定访问 URL 的元数据。 */
    StoredObject save(String key, InputStream input, String contentType);

    /** 读取对象元数据（不返回流；业务层通过 publicUrl 下载/播放）。 */
    Optional<StoredObject> get(String key);

    /** 删除对象；不存在时静默成功。 */
    void delete(String key);

    /**
     * 生成短时读取 URL（H-03）：私有对象必须通过有效期签名访问；
     * local dev 直接返回 /media 路由，prod 由 OSS/COS 生成签名 URL。
     */
    String createReadUrl(String key, Duration ttl);
}
