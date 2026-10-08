package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 「这是谁的」与「现在什么状态」两列，{@link NoteMapper#selectOwnershipForUpdate} 的返回类型。
 *
 * <p><b>删除与编辑共用这一行投影。</b>两个流程要做的事在这一点上完全相同：锁住该行、
 * 确认是本人的、看清当前状态，然后才决定后面怎么写。删除拿状态去问
 * {@link NoteStateMachine}，编辑拿状态去挡 {@code DELETED}——判据不同，但要读的东西一样。
 * 早先它叫 {@code NoteDeleteRow} 且只服务删除；编辑切片来了之后两条路线需要的列一字不差，
 * 再复制一份就成了同一件事的两份副本（两处都得记得「顺手」加上新列），故改为中性名字共用。
 *
 * <p><b>只为这两列开一个投影，而不是复用 {@code BaseMapper#selectById}</b>：后者会把标题、简介、
 * 教师、三个计数、{@code created_at} 等十几列一并拖回来，而本流程只关心「这是谁的」与「现在什么状态」。
 * {@code NoteMapper#selectFavoriteCount} 的注释已经为同一件事否决过一次同类做法。
 *
 * <p><b>用 setter 而不是 record</b>：与 {@code NoteListRow} / {@code NoteTagRef} 一致。
 * 这两个字段类型不同（{@code Long} / {@code String}），位置绑定眼下不会出错，
 * 但本项目里读取行的投影一律是这个形状，多一种写法只会让人多问一句为什么。
 */
@Getter
@Setter
public class NoteOwnershipRow {

    /** 上传者。与 JWT 里的当前用户比对，不一致就是 40300 */
    private Long uploaderId;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED} 的原始字符串，由调用方转成 {@link NoteStatus} */
    private String status;
}
