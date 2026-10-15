package com.wlf.note;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 笔记状态迁移。
 *
 * <p>本类只回答允不允许，不做写入。落地语句在各 Service 里
 */
@Component
public class NoteStateMachine {

    /**
     * 迁移表。
     * ONLINE / OFFLINE / DELETED。
     * DELETED为终态。
     */
    private static final Map<NoteStatus, Set<NoteStatus>> ALLOWED = Map.of(
            NoteStatus.ONLINE, Set.of(NoteStatus.OFFLINE, NoteStatus.DELETED),
            NoteStatus.OFFLINE, Set.of(NoteStatus.ONLINE, NoteStatus.DELETED),
            NoteStatus.DELETED, Set.of());

    /**
     * 校验一次状态迁移是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 该迁移不在表里（含从终态 {@code DELETED} 出发的任何迁移，
     *                               以及 {@code from == to} 这种原地不动的调用）
     */
    public void requireTransition(NoteStatus from, NoteStatus to) {
        // from 为 null 时直接 NPE 也好过静默放行：调用方没读到状态就该在那里先报 40400
        if (!ALLOWED.get(from).contains(to)) {
            throw new IllegalStateException("非法的笔记状态迁移：" + from + " → " + to);
        }
    }
}
