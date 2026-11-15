package com.wlf.storage;

import com.wlf.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿对象死信队列的深度巡检：非空就说明有对象没删掉、在等人处理。
 *
 * <p>这是本次改动<b>收益的一半</b>。投 MQ 把「静默泄漏」变成「队列里一条看得见的积压」，
 * 但看得见的前提是有人看——《概要设计》§7 里「死信队列没有任何监控」那条未决项，在这条链路上
 * 会直接抵消掉投递带来的好处：泄漏只是换了个地方藏。
 *
 * <p>所以这里只做<b>最低限度</b>的一件事：定时看一眼深度，非 0 才打日志（形状照
 * {@code OrphanObjectSweeper}——绝大多数轮次是空跑，成功日志会淹掉别的信息）。Actuator、
 * 告警通道那两档是 §7 里更高的事，本轮不做。
 *
 * <p>它<b>不消费</b>死信队列里的消息，也不重投：那些对象失败的原因（OSS 持续不可用、权限变了、
 * 桶被删了）需要人看一眼才知道，自动重投只是把必然失败的动作再打一遍。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.purge.mode", havingValue = "queue")
public class OrphanObjectDlqMonitor {

    private final AmqpAdmin amqpAdmin;

    public OrphanObjectDlqMonitor(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /**
     * 异常在这里咽掉：这是个定时任务，抛出去只会让这一轮失败，而下一轮到点照样会跑。
     */
    @Scheduled(initialDelayString = "${jotang.purge.dlq-check.initial-delay}",
            fixedDelayString = "${jotang.purge.dlq-check.fixed-delay}")
    public void reportDepth() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(RabbitConfig.ORPHAN_OBJECT_DLQ);
            if (info != null && info.getMessageCount() > 0) {
                log.warn("孤儿对象死信队列非空，有待人工清理的对象 queue={} depth={}",
                        RabbitConfig.ORPHAN_OBJECT_DLQ, info.getMessageCount());
            }
        } catch (RuntimeException e) {
            log.error("孤儿对象死信队列深度巡检失败，本轮放弃，下一轮重来", e);
        }
    }
}
