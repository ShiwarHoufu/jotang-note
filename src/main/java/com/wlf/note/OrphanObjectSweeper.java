package com.wlf.note;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿对象对账的定时入口：定期调 {@link NoteService#sweepOrphanObjects}，把「已删除、
 * 但对象还没被物理清理」的笔记补清掉。
 *
 * <p>本类只做两件事：按节奏触发、把异常咽下去。业务规则一条都不在这里，这样对账逻辑
 * 能被直接调用与断言，不必为了测它去等一个定时器。
 *
 * <p>用轮询而不是消息队列：判据（{@code is_purged = 0}）本来就在库里，轮询不需要任何中间件，
 * 也不存在「消息丢了就永远没人知道」这种失败形态——最坏只是晚一轮。将来 MQ 做快路径时
 * 本类应当保留：MQ 解决「快」，它解决「漏了能发现」。
 */
@Component
@ConditionalOnProperty(name = "jotang.purge.enabled", havingValue = "true", matchIfMissing = true)
public class OrphanObjectSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanObjectSweeper.class);

    private final NoteService noteService;
    private final int batchSize;

    public OrphanObjectSweeper(NoteService noteService,
                               @Value("${jotang.purge.batch-size}") int batchSize) {
        this.noteService = noteService;
        this.batchSize = batchSize;
    }

    /**
     * 跑一轮对账。
     *
     * <p>{@code fixedDelay} 而不是 {@code fixedRate}：对账耗时取决于积压里要打多少次 OSS，
     * 后者会让慢的轮次排队、延迟越积越多。这里顺带保证了同一任务不会与自身重叠。
     * 初始延迟刻意留长（见 {@code application.yaml}）：对账是兜底，晚一小时没有代价，
     * 而应用刚起时正忙着建连接池，此刻插一轮是在抢资源。
     *
     * <p>异常在这里咽掉，说清「本轮放弃、下一轮重来」。能冒到这里的只剩「候选集查不出来」
     * 这类整体故障（比如数据库连不上）——单条失败已经被 {@code sweepOrphanObjects} 挡在循环里了。
     */
    @Scheduled(initialDelayString = "${jotang.purge.initial-delay}",
            fixedDelayString = "${jotang.purge.fixed-delay}")
    public void sweep() {
        try {
            int purged = noteService.sweepOrphanObjects(batchSize);
            if (purged > 0) {
                // 只在真清掉了东西时记录：对账绝大多数轮次是空跑，成功日志会淹掉别的信息
                log.info("孤儿对象对账清理了 {} 条笔记对象", purged);
            }
        } catch (RuntimeException e) {
            log.error("孤儿对象对账失败，本轮放弃，下一轮重来", e);
        }
    }
}
