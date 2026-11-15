package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * favorite 表。见《概要设计》§3.2、§6.5。
 *
 * <p>没有「收藏时间倒序」之外的业务列，四列而已——这正是软删除换来的：
 * {@code note_id} 指向的 note 行永不物理删除，所以这个引用永不悬空，
 * 收藏列表直接 JOIN 读 {@code note.status} 就能渲染「已下架 / 已删除」占位，
 * <b>不必在 favorite 里存标题快照</b>（D4，§3.3）。
 *
 * <p>去重靠 {@code uk_user_note} 唯一索引兜底，<b>不在 Java 侧做「先查再插」</b>：
 * 那套在并发下会漏，两个请求可以同时查到「没收藏过」。重复收藏撞唯一键后由
 * {@code FavoriteService} 转成 40902（§3.3）。
 */
@Getter
@Setter
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏者。与 {@code note.uploader_id} 无关，这里说的是「谁收藏了」而非「谁传的」 */
    private Long userId;

    /** 被收藏的笔记。可为 OFFLINE / DELETED 状态的笔记（§6.5 不解除收藏关系） */
    private Long noteId;

    /**
     * 由建表语句的 {@code DEFAULT CURRENT_TIMESTAMP} 兜底，Java 侧不赋值——
     * 与 {@code note} 的三个计数列同理：MyBatis-Plus 默认跳过 null 字段，
     * 在 Java 侧再写一遍时间只会多一处可能与 DDL 不一致的地方。
     */
    private LocalDateTime createdAt;
}
