package com.wlf.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 存储抽象：流式上传、读取、签发签名 URL、删除对象。
 * 所有 OSS 操作收敛于此，业务层不感知底层存储；OSS 为首个实现，V2 可平替 MinIO。
 * 见《概要设计》§1.1、§6.2、§6.7。
 *
 * <p>本层只提供「存 / 读 / 签 / 删」四件原语，<b>不含任何业务策略</b>：
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
     * 打开对象供读取。返回的流<b>由调用方负责关闭</b>（关闭流即释放底层 HTTP 连接），
     * 请配合 try-with-resources 使用。
     *
     * <p>用途是 §6.2 的 MD / 文本预览：这类文件体积小，由后端代理转发而不是签发直连 URL，
     * 以便固定响应头（{@code Content-Type: text/plain; charset=utf-8} 与
     * {@code X-Content-Type-Options: nosniff}），不给浏览器直接解析原文件的机会。
     * 图片 / PDF 不走这里，它们走 {@link #presignedUrl} 直连 OSS。
     *
     * <p>刻意返回 {@code InputStream} 而非 OSS SDK 的 {@code OSSObject}：不让 SDK 类型
     * 泄漏进业务层，V2 平替 MinIO 时本签名无需改动。响应头、状态校验等流程留在调用方。
     *
     * @throws com.wlf.common.BusinessException 对象不存在或 OSS 故障时抛 50000。
     *         正常的业务流不会走到「对象不存在」——读取前调用方已校验笔记状态，
     *         故该情形属数据不一致（库里记着、对象没了），按服务端错误处理
     */
    InputStream open(Bucket bucket, String key);

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
