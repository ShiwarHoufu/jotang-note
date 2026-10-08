package com.wlf.note;

/**
 * 笔记状态。见《概要设计》§4.1。
 *
 * <pre>
 *   [*] --上传成功--&gt; ONLINE --管理员下架--&gt; OFFLINE --管理员恢复--&gt; ONLINE
 *                       |                     |
 *                       +---- 上传者删除 ------+--&gt; DELETED --&gt; [*]
 * </pre>
 *
 * <p>与 {@code user.role} / {@code user.status} 一样，实体里存的是 {@link #name()} 字符串
 * （{@code note.status VARCHAR(16)}），本枚举只用于业务逻辑判断，不做 MyBatis-Plus 的枚举映射。
 *
 * <p>{@link #DELETED} 是<b>终态</b>，没有出边；{@link #OFFLINE} 只能回到 {@link #ONLINE}。
 * 迁移的合法性由 {@link NoteStateMachine} 统一把关，任何模块都不得直接改 {@code note.status}。
 */
public enum NoteStatus {

    /** 正常上架：列表 / 搜索 / 详情可见，允许预览、下载、收藏 */
    ONLINE,

    /** 管理员下架：不出现在列表与搜索中；详情页提示「已下架」，禁止预览 / 下载 / 收藏 */
    OFFLINE,

    /** 上传者删除：软删除，行保留，终态不可逆；收藏列表借此渲染「笔记已删除」占位 */
    DELETED
}
