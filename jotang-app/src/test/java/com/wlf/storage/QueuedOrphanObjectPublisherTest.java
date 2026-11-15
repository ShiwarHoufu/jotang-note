package com.wlf.storage;

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
 * {@link QueuedOrphanObjectPublisher} 的单元测试：mock 掉 {@code RabbitTemplate}，
 * <b>不起 Spring 上下文，也不需要 Broker</b>。形状照 {@code QueuedMailServiceTest}。
 *
 * <p>真投递由 {@code @Tag("mq")} 的 {@code OrphanObjectMqTest} 覆盖。
 */
class QueuedOrphanObjectPublisherTest {

    private static final String KEY = "notes/2026/10/00000000-0000-0000-0000-000000000000.png";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final QueuedOrphanObjectPublisher publisher = new QueuedOrphanObjectPublisher(rabbitTemplate);

    /** 交换机与路由键用 {@link RabbitConfig} 的常量比对，把「拓扑名笔误」钉在编译期之外 */
    @Test
    void publishesToTheDeclaredExchangeAndRoutingKey() {
        publisher.publish(Bucket.NOTE, KEY);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.STORAGE_EXCHANGE),
                eq(RabbitConfig.ORPHAN_OBJECT_ROUTING_KEY),
                payload.capture(),
                any(MessagePostProcessor.class));

        // 桶必须原样进载荷：消费者只照它去删，不自己猜
        assertThat(payload.getValue()).isEqualTo(new OrphanObject(Bucket.NOTE, KEY));
    }

    /**
     * 持久化投递——<b>与 {@code QueuedMailServiceTest} 那条 NON_PERSISTENT 正相反</b>。
     *
     * <p>这里没有秘密要防落盘，要防的是「消息丢了」：它是孤儿唯一的记录，丢一条就是永久泄漏。
     * 这条与 {@code RabbitConfigTest} 的 TTL 那两条合起来钉住同一个取舍，别让「统一两套 MQ 配置」
     * 把任意一边改掉。
     */
    @Test
    void messagesArePersistentBecauseTheMessageIsTheOnlyRecordOfTheOrphan() {
        publisher.publish(Bucket.NOTE, KEY);

        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), postProcessor.capture());

        MessageProperties properties = new MessageProperties();
        Message processed = postProcessor.getValue().postProcessMessage(new Message(new byte[0], properties));

        assertThat(processed.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    /**
     * Broker 连不上时异常必须<b>原样外抛</b>：这里只是「再试都不会成功」的第一层，
     * 要不要让它影响这次上传的收场，是调用方（{@code NoteService#purgeQuietly}）的决定。
     */
    @Test
    void amqpFailurePropagatesSoTheCallerCanDecide() {
        doThrow(new AmqpException("broker 不可达"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        assertThatThrownBy(() -> publisher.publish(Bucket.NOTE, KEY))
                .isInstanceOf(AmqpException.class);
    }
}
