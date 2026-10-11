package com.wlf.mail;

import com.wlf.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link QueuedMailService} 的单元测试：mock 掉 {@code RabbitTemplate}，
 * <b>不起 Spring 上下文，也不需要 Broker</b>。
 *
 * <p>真投递由 {@code @Tag("mq")} 的 {@code MailDispatchMqTest} 覆盖。这里要钉的是三件
 * 「发到哪儿、以什么形式发、发不出去怎么办」——它们错了都<b>不会当场报错</b>：
 *
 * <ul>
 *   <li>交换机 / 路由键写错 → 消息被静静地丢弃（开了 {@code mandatory} 才退回并记日志）</li>
 *   <li>持久化模式没设成 {@code NON_PERSISTENT} → 明文令牌落进 Broker 的磁盘</li>
 *   <li>异常被吞掉 → 调用方的降级判断永远走不到</li>
 * </ul>
 */
class QueuedMailServiceTest {

    private static final String TO = "zztest-qms@example.com";
    private static final String SUBJECT = "【Jotang Note】重置你的登录密码";
    private static final String BODY = "你好，请打开 http://example.com/reset?token=abc";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final QueuedMailService mailService = new QueuedMailService(rabbitTemplate);

    /** 交换机与路由键用 {@link RabbitConfig} 的常量比对，把「拓扑名笔误」钉在编译期之外 */
    @Test
    void publishesToTheDeclaredExchangeAndRoutingKey() {
        mailService.send(TO, SUBJECT, BODY);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.MAIL_EXCHANGE),
                eq(RabbitConfig.MAIL_ROUTING_KEY),
                payload.capture(),
                any(MessagePostProcessor.class));

        assertThat(payload.getValue()).isInstanceOfSatisfying(MailDispatch.class, dispatch -> {
            assertThat(dispatch.to()).isEqualTo(TO);
            assertThat(dispatch.subject()).isEqualTo(SUBJECT);
            assertThat(dispatch.textBody()).isEqualTo(BODY);
            // messageId 让日志能对上「哪一条」，而不必把正文打出来（正文里有令牌）
            assertThat(dispatch.messageId()).isNotBlank();
        });
    }

    /**
     * 非持久化投递：正文里带着明文令牌，别让它落进 Broker 的磁盘。
     *
     * <p>这条只挡「Broker 重启后消息还在」这一种情形，挡不住「消息还在队列里时被人从管理台看」
     * ——那半边靠 5672/15672 不对公网开放，以及两个 TTL 把驻留时间压到有限（§8.3）。
     * 但少一层是一层，所以这一条要钉住。
     */
    @Test
    void messagesAreNotPersistedSoThePlaintextTokenDoesNotReachTheBrokerDisk() {
        mailService.send(TO, SUBJECT, BODY);

        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), postProcessor.capture());

        MessageProperties properties = new MessageProperties();
        Message processed = postProcessor.getValue().postProcessMessage(new Message(new byte[0], properties));

        assertThat(processed.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
    }

    /**
     * Broker 连不上时异常必须<b>原样外抛</b>：要不要让「发不出信」影响这次请求，
     * 是调用方（{@code PasswordResetService}）的决定。
     *
     * <p>本类若在这里把异常咽掉，调用方就永远看不到失败——它会以为「消息投出去了」，
     * 而实际上一条都没发。这正是 {@code MailDispatchConsumer} 那边
     * 「失败必须外抛」的同一条规矩，只是换了个位置。
     */
    @Test
    void amqpFailurePropagatesSoTheCallerCanDecide() {
        doThrow(new AmqpException("broker 不可达"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        assertThatThrownBy(() -> mailService.send(TO, SUBJECT, BODY))
                .isInstanceOf(AmqpException.class);
    }
}
