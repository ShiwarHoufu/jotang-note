package com.wlf.storage;

import org.springframework.stereotype.Service;

/**
 * StorageService 的阿里云 OSS 实现，读写一律走内网 Endpoint。
 * 见《概要设计》§1.2。
 */
@Service
public class OssStorageService implements StorageService {
}
