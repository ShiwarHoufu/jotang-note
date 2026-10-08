package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除流程要读的两列，{@link NoteMapper#selectForDelete} 的返回类型。
 *
 * <p><b>只为这两列开一个投影，而不是复用 {@code BaseMapper#selectById}</b>：后者会把标题、简介、
 * 教师、三个计数、{@code created_at} 等十几列一并拖回来，而删除只关心「这是谁的」与「现在什么状态」。
 * {@code NoteMapper#selectFavoriteCount} 的注释已经为同一件事否决过一次同类做法。
 *
 * <p><b>用 setter 而不是 record</b>：与 {@code NoteListRow} / {@code NoteTagRef} 一致。
 * 这两个字段类型不同（{@code Long} / {@code String}），位置绑定眼下不会出错，
 * 但本项目里读取行的投影一律是这个形状，多一种写法只会让人多问一句为什么。
 */
@Getter
@Setter
public class NoteDeleteRow {

    /** 上传者。与 JWT 里的当前用户比对，不一致就是 40300 */
    private Long uploaderId;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED} 的原始字符串，由调用方转成 {@link NoteStatus} */
    private String status;
}
