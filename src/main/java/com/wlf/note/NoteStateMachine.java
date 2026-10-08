package com.wlf.note;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 笔记状态迁移：ONLINE / OFFLINE / DELETED，DELETED 为终态。
 * 状态变更集中在此，禁止 Controller 直接改 status。见《概要设计》§4.1。
 *
 * <pre>
 *   ONLINE --管理员下架--&gt; OFFLINE --管理员恢复--&gt; ONLINE
 *      |                       |
 *      +---- 上传者删除 --------+--&gt; DELETED --&gt; [*]
 * </pre>
 *
 * <p>§4.1 的状态图已经把这张表完全确定下来，所以四条边一次写全：删除只用其中两条
 * （{@code ONLINE→DELETED}、{@code OFFLINE→DELETED}），管理员的下架 / 恢复切片来了直接复用，
 * 不必再定一次形状——分两次写同一张迁移表，只会让第二次的人先去猜第一次为什么那么切。
 *
 * <p><b>本类只回答「这一步允不允许」，不做写入。</b>落地语句在各 Service 里：
 * 本类不注入任何 Mapper，也就没有事务边界的问题。要的是「迁移的合法性只有这一处判」，
 * 而不是把写操作也收进来。
 */
@Component
public class NoteStateMachine {

    /**
     * 迁移表。三个状态<b>一个都不能少</b>，包括终态：{@code DELETED} 的值是空集而不是省掉这一项
     * 「终态没有出边」与「这张表漏配了 DELETED」必须是两件看起来不一样的事，
     * 省掉的话 {@code get(from)} 返回 null，「漏配」就被伪装成了「终态」。
     * 同理，将来 {@link NoteStatus} 加了第四个取值，这里会因为查不到而当场炸出来，
     * 而不是默默允许一切。
     */
    private static final Map<NoteStatus, Set<NoteStatus>> ALLOWED = Map.of(
            NoteStatus.ONLINE, Set.of(NoteStatus.OFFLINE, NoteStatus.DELETED),
            NoteStatus.OFFLINE, Set.of(NoteStatus.ONLINE, NoteStatus.DELETED),
            NoteStatus.DELETED, Set.of());

    /**
     * 校验一次状态迁移是否合法，不合法就抛。
     *
     * <p><b>签名是 {@code (from, to)} 而不是「每条边一个方法」</b>（如 {@code canOffline()} /
     * {@code canRestore()}）：那样四个调用点会各自把目标状态写进方法名里，将来加一条边就得记得
     * 改所有地方；这里目标状态是参数，迁移表仍是唯一的判据。管理员切片调用的形状也一样：
     * 下架 {@code requireTransition(ONLINE, OFFLINE)}、恢复 {@code requireTransition(OFFLINE, ONLINE)}。
     *
     * <p><b>抛 {@link IllegalStateException} 而不是 {@code BusinessException(50000)}</b>：
     * 迁移非法意味着调用方先读错了状态、或代码里写死了一条不存在的边，都是程序缺陷而非用户错误，
     * 没有合适的业务码可给。这里若图省事抛一个业务码，就会有人把它当成契约的一部分——
     * 等管理员接口里 {@code DELETED→OFFLINE} 变成真实的用户动作时，「50000 服务器内部错误」
     * 是会把排查方向带偏的。走异常最终仍由兜底翻成 50000，但它不是一个被承诺的码。
     *
     * @param from 当前状态，取自刚读出的那一行——不能是缓存或调用方猜的值
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
