package com.wlf.user.infrastructure.config;

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
 * 邮件队列的 RabbitMQ 拓扑与消息格式。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    /** 邮件交换机。direct，durable。 */
    public static final String MAIL_EXCHANGE = "jotang.mail.exchange";

    /** 重置邮件的路由键。 */
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
     * 死信队列。TTL 1 小时。
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
