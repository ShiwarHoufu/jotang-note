package com.wlf.storage;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 孤儿对象清理的 RabbitMQ 拓扑。
 *
 * <p>参数取舍与邮件那条刻意相反：工作队列不设 TTL 与长度上限，死信队列也不自灭。
 * 消息是孤儿对象唯一的待办记录，静默销毁等于让泄漏继续。
 */
@Configuration
public class StorageRabbitConfig {

    /**
     * 存储清理交换机。不与邮件交换机混用：拓扑名是代码契约。
     */
    public static final String STORAGE_EXCHANGE = "jotang.storage.exchange";

    /** 孤儿对象待清理的路由键。 */
    public static final String ORPHAN_OBJECT_ROUTING_KEY = "storage.orphan-object.purge";

    /** 孤儿对象的工作队列。 */
    public static final String ORPHAN_OBJECT_QUEUE = "jotang.storage.orphan-object.q";

    /** 存储清理的死信交换机。 */
    public static final String STORAGE_DLX = "jotang.storage.dlx";

    /** 存储清理的死信路由键。 */
    public static final String ORPHAN_OBJECT_DEAD_ROUTING_KEY = "storage.orphan-object.dead";

    /**
     * 孤儿对象的死信队列。<b>刻意不挂消费者</b>——它是取证面而非重试池，
     * 一条消息 = 一个还在桶里的孤儿对象，等人工按记录去删。
     */
    public static final String ORPHAN_OBJECT_DLQ = "jotang.storage.orphan-object.dlq";

    @Bean
    public DirectExchange storageExchange() {
        return new DirectExchange(STORAGE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange storageDeadLetterExchange() {
        return new DirectExchange(STORAGE_DLX, true, false);
    }

    /**
     * 孤儿对象工作队列。
     */
    @Bean
    public Queue orphanObjectQueue() {
        return QueueBuilder.durable(ORPHAN_OBJECT_QUEUE)
                .deadLetterExchange(STORAGE_DLX)
                .deadLetterRoutingKey(ORPHAN_OBJECT_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 孤儿对象死信队列。无 TTL。
     */
    @Bean
    public Queue orphanObjectDeadLetterQueue() {
        return QueueBuilder.durable(ORPHAN_OBJECT_DLQ).build();
    }

    @Bean
    public Binding orphanObjectBinding(Queue orphanObjectQueue, DirectExchange storageExchange) {
        return BindingBuilder.bind(orphanObjectQueue).to(storageExchange).with(ORPHAN_OBJECT_ROUTING_KEY);
    }

    @Bean
    public Binding orphanObjectDeadLetterBinding(Queue orphanObjectDeadLetterQueue,
                                                 DirectExchange storageDeadLetterExchange) {
        return BindingBuilder.bind(orphanObjectDeadLetterQueue)
                .to(storageDeadLetterExchange)
                .with(ORPHAN_OBJECT_DEAD_ROUTING_KEY);
    }
}
