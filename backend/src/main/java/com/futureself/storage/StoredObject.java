package com.futureself.storage;

/**
 * 存储对象元数据。url 为前端可访问地址（dev 为 /media/{key} 相对路径）。
 */
public record StoredObject(String key, String url, String contentType, long sizeBytes) {
}
