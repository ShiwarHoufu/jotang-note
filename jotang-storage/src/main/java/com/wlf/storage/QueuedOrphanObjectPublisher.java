package com.wlf.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把「有个孤儿对象待删」投进 RabbitMQ，由 {@link OrphanObjectConsumer} 去删。
 *
 * <p>调用方是上传失败后的补偿路径
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.purge.mode", havingValue = "queue")
public class QueuedOrphanObjectPublisher implements OrphanObjectPublisher {

    private final RabbitTemplate rabbitTemplate;

    public QueuedOrphanObjectPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 启动横幅。（见 application.yaml 里 jotang.purge.mode 那段）
        log.info("补偿删除上报方式：RabbitMQ 队列 queue={} routingKey={}",
                StorageRabbitConfig.ORPHAN_OBJECT_QUEUE, StorageRabbitConfig.ORPHAN_OBJECT_ROUTING_KEY);
    }

    @Override
    public void publish(Bucket bucket, String storageKey) {
        OrphanObject orphan = new OrphanObject(bucket, storageKey);
        rabbitTemplate.convertAndSend(StorageRabbitConfig.STORAGE_EXCHANGE, StorageRabbitConfig.ORPHAN_OBJECT_ROUTING_KEY, orphan,
                message -> {
                    // 持久化
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });

        log.debug("补偿删除已上报 bucket={} key={}", bucket, storageKey);
    }
}
