package com.wlf.note.interfaces.scheduler;

import com.wlf.note.application.service.ViewCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 浏览量回写的定时入口，只负责调度。 */
@Component
@ConditionalOnProperty(name = "jotang.view.flush.enabled", havingValue = "true", matchIfMissing = true)
public class ViewCountFlusher {

    private static final Logger log = LoggerFactory.getLogger(ViewCountFlusher.class);

    private final ViewCountService viewCountService;
    private final int batchSize;

    public ViewCountFlusher(ViewCountService viewCountService,
                            @Value("${jotang.view.flush.batch-size}") int batchSize) {
        this.viewCountService = viewCountService;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${jotang.view.flush.initial-delay}",
            fixedDelayString = "${jotang.view.flush.fixed-delay}")
    public void flush() {
        try {
            int flushed = viewCountService.flush(batchSize);
            if (flushed > 0) {
                log.info("浏览量回写 {} 条", flushed);
            }
        } catch (RuntimeException e) {
            log.error("浏览量回写失败，下一轮重试", e);
        }
    }

    /**
     * 关停时补回写一批，减少未落库计数的丢失。
     *
     * <p>监听 {@link ContextClosedEvent} 而非 {@code @PreDestroy}：前者在
     * {@code SmartLifecycle#stop} 之前发布，此时 Redis 连接还没关。
     */
    @EventListener(ContextClosedEvent.class)
    public void flushOnShutdown() {
        log.info("应用关停，补回写一次浏览量");
        flush();
    }
}
