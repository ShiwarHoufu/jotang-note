package com.wlf.storage;

import com.wlf.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把「有个孤儿对象待删」投进 RabbitMQ，由 {@link OrphanObjectConsumer} 去删。
 * 见《功能说明：笔记上传》§2.5。
 *
 * <p>调用方是上传失败后的补偿路径：内联那次删除仍然要做（让对象尽快消失），本类只是让这条待办
 * 有个地方落地——因为这类孤儿 {@code note_file} 里没有行，定时对账永远扫不到。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.purge.mode", havingValue = "queue")
public class QueuedOrphanObjectPublisher implements OrphanObjectPublisher {

    private final RabbitTemplate rabbitTemplate;

    public QueuedOrphanObjectPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 启动横幅：它是「当前走队列」的唯一明显迹象（见 application.yaml 里 jotang.purge.mode 那段）
        log.info("补偿删除上报方式：RabbitMQ 队列 queue={} routingKey={}",
                RabbitConfig.ORPHAN_OBJECT_QUEUE, RabbitConfig.ORPHAN_OBJECT_ROUTING_KEY);
    }

    @Override
    public void publish(Bucket bucket, String storageKey) {
        OrphanObject orphan = new OrphanObject(bucket, storageKey);
        rabbitTemplate.convertAndSend(RabbitConfig.STORAGE_EXCHANGE, RabbitConfig.ORPHAN_OBJECT_ROUTING_KEY, orphan,
                message -> {
                    // 持久化——与 QueuedMailService 的 NON_PERSISTENT **正相反**。
                    // 那边是「别让明文令牌落进 Broker 的磁盘」，这边是「这条消息是孤儿唯一的记录，
                    // 必须活过 Broker 重启」。两处都对，别把其中一处当成另一处的笔误（§2.5 的对照表）
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });

        // 与邮件那边同理：convertAndSend 返回只代表「写进了 socket」。这里刻意不等发布确认——
        // 调用方在补偿路径上，本就在收尾，把它再变成一次等待没有意义
        log.debug("补偿删除已上报 bucket={} key={}", bucket, storageKey);
    }
}
