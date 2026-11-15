package com.wlf.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NoteStateMachine} 的用例。见《概要设计》§4.1。
 *
 * <p>纯单元测试：迁移表是常量、类没有依赖，起 Spring 上下文只为了验一张 3×3 的表不划算。
 *
 * <p>三个状态两两组合共 <b>9 种</b>，下面三组用例把它们<b>全部覆盖</b>：
 * 4 条合法边、终态的 3 种出发、原地不动的 2 种。之所以要穷尽，是因为这张表的错法有两种，
 * 而它们看起来很像——「漏了一条合法边」（用户操作被拒）与「多了一条合法边」（不该发生的迁移被放行），
 * 只挑几条断言两种都测不出来。
 */
class NoteStateMachineTest {

    private final NoteStateMachine stateMachine = new NoteStateMachine();

    /** §4.1 状态图里的四条边，一条不少 */
    @Test
    void theFourDocumentedEdgesAreAllowed() {
        assertAllowed(NoteStatus.ONLINE, NoteStatus.OFFLINE);
        assertAllowed(NoteStatus.OFFLINE, NoteStatus.ONLINE);
        assertAllowed(NoteStatus.ONLINE, NoteStatus.DELETED);
        assertAllowed(NoteStatus.OFFLINE, NoteStatus.DELETED);
    }

    /**
     * {@code DELETED} 是终态，无出边——包括「再删一次」。后者正是删除接口的幂等
     * <b>不走</b>状态机的原因：那里在调用之前就把 {@code DELETED} 分支摘出去了。
     * 若哪天有人图省事把幂等判断删掉、直接调状态机，这条用例会让它当场炸出来。
     */
    @Test
    void deletedIsTerminal() {
        assertRejected(NoteStatus.DELETED, NoteStatus.ONLINE);
        assertRejected(NoteStatus.DELETED, NoteStatus.OFFLINE);
        assertRejected(NoteStatus.DELETED, NoteStatus.DELETED);
    }

    /**
     * 原地不动不是一次迁移。放行它的后果是无声的：状态没变而调用方以为改了，
     * 于是一次本该被发现的逻辑错误变成了一次「成功」的请求。
     */
    @Test
    void stayingInPlaceIsNotATransition() {
        assertRejected(NoteStatus.ONLINE, NoteStatus.ONLINE);
        assertRejected(NoteStatus.OFFLINE, NoteStatus.OFFLINE);
    }

    private void assertAllowed(NoteStatus from, NoteStatus to) {
        assertThatCode(() -> stateMachine.requireTransition(from, to))
                .doesNotThrowAnyException();
    }

    private void assertRejected(NoteStatus from, NoteStatus to) {
        assertThatThrownBy(() -> stateMachine.requireTransition(from, to))
                .isInstanceOf(IllegalStateException.class);
    }
}
