package com.wlf.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 存储抽象：流式上传、签发签名 URL、删除对象。
 * 所有 OSS 操作收敛于此，业务层不感知底层存储；OSS 为首个实现，V2 可平替 MinIO。
 * 见《概要设计》§1.1、§6.2、§6.7。
 *
 * <p>本层只提供「存 / 签 / 删」三件原语，<b>不含任何业务策略</b>：
 * 对象键怎么拼（§6.1 的 {@code notes/yyyy/MM/{uuid}.{ext}}）、TTL 多长、
 * 哪些扩展名合法、文件头魔数是什么，全部由调用方（note / user 模块）决定。
 * 这样 note 的单元测试可以直接 mock 本接口，不必碰 OSS SDK。
 */
public interface StorageService {

    /**
     * 流式上传，不把内容整体读进内存（单文件可达 100MB，§6.1）。
     *
     * @param size 字节数，<b>必须准确</b>——以 InputStream 入参时 SDK 无法自行推断长度
     * @return 上传结果；key 原样回传，便于调用方直接落库
     */
    StoredObject upload(Bucket bucket, String key, InputStream in, long size, String contentType);

    /**
     * 签发浏览器可直连的签名 URL。两种场景共用本方法：
     *
     * <ul>
     *   <li>预览：{@code downloadName} 传 null → 内联展示（TTL 5 分钟，§6.2）</li>
     *   <li>下载：传 {@code downloadName} → 附件形式并还原文件名（TTL 2 分钟，§6.2）</li>
     * </ul>
     *
     * <p><b>Content-Type 不在这里指定</b>：响应的类型取自对象自身元数据，即上传时由
     * {@link #upload} 写入的服务端判定值。这样既满足 §6.2「以服务端判定值为准」的安全意图，
     * 又比在读取时覆盖更强——类型在入口就被钉死。且阿里云 OSS 自 2025-01-20 起
     * 已禁止通过签名 URL 覆盖 {@code response-content-type}（错误码 0017-00000902），
     * 读取时覆盖这条路本身也走不通。
     *
     * @param downloadName 非 null 时以附件形式下载并还原该文件名；null 表示内联预览
     */
    String presignedUrl(Bucket bucket, String key, Duration ttl, String downloadName);

    /** 公共读桶（头像）的直连 URL，不签名（§6.7）。 */
    String publicUrl(Bucket bucket, String key);

    /**
     * 删除对象。对象不存在时同样成功（OSS 返回 204），调用方无需先判存在。
     *
     * <p>对应 §3.3「用户删除笔记后异步清理 OSS 对象」，不可恢复。
     */
    void delete(Bucket bucket, String key);
}
