package com.wlf.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑与消息格式。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    /** 邮件交换机。direct，durable。 */
    public static final String MAIL_EXCHANGE = "jotang.mail.exchange";

    /** 重置邮件的路由键。目前是唯一的键。 */
    public static final String MAIL_ROUTING_KEY = "mail.password-reset";

    /** 重置邮件的工作队列。名字里的 {@code .q} 是队列后缀，与死信队列的 {@code .dlq} 区分。 */
    public static final String PASSWORD_RESET_QUEUE = "jotang.mail.password-reset.q";

    /** 死信交换机。独立的 exchange，不挂在 {@link #MAIL_EXCHANGE} 上。 */
    public static final String MAIL_DLX = "jotang.mail.dlx";

    /** 死信路由键 */
    public static final String MAIL_DEAD_ROUTING_KEY = "mail.password-reset.dead";

    /**
     * 死信队列。<b>刻意不挂消费者</b>——它是取证面，不是重试池。
     *
     * <p>挂上消费者就等于「把这些必然失败的邮件再打一遍」，而它们失败的原因（SMTP 凭据错、
     * 收件人不存在、正文格式变了）需要人看一眼才知道。管理台上看到它非空时的处置见 §7.3。
     */
    public static final String PASSWORD_RESET_DLQ = "jotang.mail.password-reset.dlq";

    // ==================== 存储清理链路：补偿删除的孤儿对象 ====================
    // 这条链路的参数取舍与邮件那条**刻意相反**，逐条对照见 orphanObjectQueue() 与
    // 《功能说明：笔记上传》§2.5 的对照表——别拿「统一两套 MQ 配置」当清理。

    /**
     * 存储清理交换机。**不挂在 {@link #MAIL_EXCHANGE} 上**：一条 exchange 加个路由键确实更省，
     * 但那样 {@code jotang.mail.*} 这个名字就开始承载存储消息了。拓扑名是代码契约，宁多一个 Bean。
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
     * 孤儿对象的死信队列。<b>刻意不挂消费者</b>——与邮件那条同理，它是取证面而非重试池，
     * 但这里的处置不同：一条消息 = 一个还在桶里的孤儿对象，等人工按记录去删。
     */
    public static final String ORPHAN_OBJECT_DLQ = "jotang.storage.orphan-object.dlq";

    @Bean
    public DirectExchange mailExchange() {
        // durable=true：Broker 重启后交换机还在。autoDelete=false：没有绑定也不自动摘除
        return new DirectExchange(MAIL_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange mailDeadLetterExchange() {
        return new DirectExchange(MAIL_DLX, true, false);
    }

    /**
     * 工作队列。
     */
    @Bean
    public Queue passwordResetQueue() {
        return QueueBuilder.durable(PASSWORD_RESET_QUEUE)
                .deadLetterExchange(MAIL_DLX)
                .deadLetterRoutingKey(MAIL_DEAD_ROUTING_KEY)
                .ttl(600_000)
                .maxLength(10_000)
                .build();
    }

    /**
     * 死信队列。TTL 1 小时后自灭——<b>取证明不等于留证据</b>。
     *
     * <p>它与工作队列的 {@code x-message-ttl} 是两个独立的上限：消息在队列里最多待 10 分钟，
     * 进了死信队列再最多待 1 小时。加起来就是「一封发不出去的邮件，连同它正文里的明文令牌，
     * 在 Broker 里最多存在 70 分钟」。这个数字要写进 §8.3。
     *
     * <p>不给死信队列再挂死信：那会变成一条没有出口的链。到点自灭是它的终点。
     */
    @Bean
    public Queue passwordResetDeadLetterQueue() {
        return QueueBuilder.durable(PASSWORD_RESET_DLQ)
                .ttl(3_600_000)
                .build();
    }

    @Bean
    public Binding passwordResetBinding(Queue passwordResetQueue, DirectExchange mailExchange) {
        return BindingBuilder.bind(passwordResetQueue).to(mailExchange).with(MAIL_ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetDeadLetterBinding(Queue passwordResetDeadLetterQueue,
                                                  DirectExchange mailDeadLetterExchange) {
        return BindingBuilder.bind(passwordResetDeadLetterQueue)
                .to(mailDeadLetterExchange)
                .with(MAIL_DEAD_ROUTING_KEY);
    }

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
     * 孤儿对象死信队列。<b>无 TTL</b>——邮件那条死信队列的 1 小时自灭是「取证明不等于留证据」，
     * 而这里要留的就是证据本身：一条记录对应一个还躺在桶里的对象。
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

    /**
     * 消息格式：JSON，用Jackson 3
     */
    @Bean
    public MessageConverter amqpMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    /**
     * 消息路由不到任何队列时记一条错误日志。
     */
    @Bean
    public RabbitTemplateCustomizer unroutableMessageLogger() {
        return template -> template.setReturnsCallback(returned ->
                log.error("消息无法路由，已被 Broker 退回：exchange={} routingKey={} reply={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
