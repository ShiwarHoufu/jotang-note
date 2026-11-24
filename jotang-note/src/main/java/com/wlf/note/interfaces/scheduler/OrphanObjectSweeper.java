package com.wlf.note.interfaces.scheduler;

import com.wlf.note.application.service.PurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 孤儿对象对账的定时入口，只负责调度。 */
@Component
@ConditionalOnProperty(name = "jotang.purge.enabled", havingValue = "true", matchIfMissing = true)
public class OrphanObjectSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanObjectSweeper.class);

    private final PurgeService purgeService;
    private final int batchSize;

    public OrphanObjectSweeper(PurgeService purgeService,
                               @Value("${jotang.purge.batch-size}") int batchSize) {
        this.purgeService = purgeService;
        this.batchSize = batchSize;
    }

    /**
     * 跑一轮对账。
     *
     * <p>{@code fixedDelay} 而不是 {@code fixedRate}：对账耗时取决于积压里要打多少次 OSS。
     * 初始延迟刻意留长（见 {@code application.yaml}）应用刚起时正忙着建连接池，此刻插一轮是在抢资源。。
     */
    @Scheduled(initialDelayString = "${jotang.purge.initial-delay}",
            fixedDelayString = "${jotang.purge.fixed-delay}")
    public void sweep() {
        try {
            int purged = purgeService.sweep(batchSize);
            if (purged > 0) {
                log.info("孤儿对象对账清理了 {} 条笔记对象", purged);
            }
        } catch (RuntimeException e) {
            log.error("孤儿对象对账失败，本轮放弃，下一轮重来", e);
        }
    }
}
