package com.wlf.storage;

/**
 * 存储桶。两个桶语义不同——笔记私有、头像公共读。
 */
public enum Bucket {

    /** 笔记文件：私有读写，读取一律经签名 URL。 */
    NOTE,

    /** 头像：公共读 / 私有写，浏览器直连读取，不签名。 */
    AVATAR
}
