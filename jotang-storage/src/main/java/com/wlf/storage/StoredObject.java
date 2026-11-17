package com.wlf.storage;

/**
 * 上传结果：对象键 / 服务端判定的 Content-Type / 大小。
 *
 * <p>key 原样回传，调用方可直接落库（note_file.storage_key / user.avatar）。
 */
public record StoredObject(String key, String contentType, long size) {
}
