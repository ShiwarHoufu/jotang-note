package com.wlf.storage;

/**
 * 存储抽象：流式上传、Range 读文件头、签发签名 URL、删除对象。
 * 所有 OSS 操作收敛于此，业务层不感知底层存储；OSS 为首个实现，V2 可平替 MinIO。
 * 见《概要设计》§1.1、§6.7。
 */
public interface StorageService {
}
