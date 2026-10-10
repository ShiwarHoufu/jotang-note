package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 「这篇笔记的附件在哪、谁能读它」，{@link NoteMapper#selectFileOwnership} 的返回类型。
 * 见《概要设计》§5.8（ai 模块的编辑场景）。
 *
 * <p><b>与 {@link NoteOwnershipRow} 的差别只有一点</b>：那个回答「能不能<b>写</b>」，
 * 只带归属与状态；这个回答「能不能<b>读附件</b>」，所以多带 {@code storage_key} 与
 * {@code content_type} 两列，并且<b>不加锁</b>——读完之后没有要保护的写入，
 * 而模型调用要几秒到几十秒，把行锁持那么久是纯粹的伤害。
 *
 * <p>依然单开投影而不复用 {@code selectDetail}：后者 JOIN 五张表、拖回十几列，
 * 而这里只要四列。{@link NoteOwnershipRow} 的注释已经为同一件事否决过一次
 * 「复用更宽的查询」。
 *
 * <p>用 setter 而不是 record：与 {@code NoteListRow} / {@code NoteTagRef} /
 * {@code NoteOwnershipRow} 一致，本项目里读取行的投影一律是这个形状。
 */
@Getter
@Setter
public class NoteFileOwnershipRow {

    /** 上传者。与 JWT 里的当前用户比对，不一致就是 40300 */
    private Long uploaderId;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED} 的原始字符串，由调用方转成 {@link NoteStatus} */
    private String status;

    /** OSS 对象键，供 {@code StorageService#open} 读取字节 */
    private String storageKey;

    /**
     * 字节数。
     *
     * <p>带它是为了<b>在打开 OSS 之前就能拒掉过大的对象</b>：{@code StorageService} 只有
     * 「存 / 读 / 签 / 删」四件原语，没有 stat，所以「读回来再判大小」会把一个上百 MB 的对象
     * 先整个拉进堆里——正是上传侧 {@code NoteFilePolicy} 靠 {@code MultipartFile#getSize()}
     * 提前拦掉的那件事。
     */
    private Long size;

    /**
     * 服务端判定的类型（§6.2 的写入侧判定值，读取时无从覆写）。
     *
     * <p>ai 用它回答两个问题：这份附件能不能摘要（是不是光栅图），以及该以什么 MIME 交给模型。
     */
    private String contentType;
}
