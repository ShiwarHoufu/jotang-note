package com.wlf.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RabbitConfig} 的单元测试：直接 new 配置类调它的 {@code @Bean} 方法，
 * <b>不起 Spring 上下文，也不需要 Broker</b>。形状照 {@code RedisConfigTest}。
 *
 * <p>要钉住的是那几个<b>错了不会当场报错</b>的参数：
 *
 * <ul>
 *   <li>队列上的 {@code x-dead-letter-exchange}：少了它，{@code default-requeue-rejected=false}
 *       就从「重试耗尽进死信队列」变成「重试耗尽直接丢消息」——而丢掉的那些邮件里
 *       有用户的重置令牌，用户只会觉得「这网站不发信」，运维侧则一片安静</li>
 *   <li>两个 TTL：它们共同决定「明文令牌在 Broker 里最多待多久」这个数字（§8.3）</li>
 *   <li>转换器的类型优先级：{@link JacksonJavaTypeMapper.TypePrecedence#INFERRED}
 *       是「消息头不能决定反序列化成哪个类」的落点</li>
 * </ul>
 *
 * <p>真打 Broker 的那一层（声明是否成功、消息是否真的流转）由 {@code @Tag("mq")} 的
 * {@code MailDispatchMqTest} 覆盖。
 */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void resetQueueDeadLettersToTheDeadLetterExchange() {
        Queue queue = config.passwordResetQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.PASSWORD_RESET_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.MAIL_DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.MAIL_DEAD_ROUTING_KEY);
    }

    /** 工作队列的 TTL 是「明文令牌在 Broker 里的最长驻留」的第一个上限 */
    @Test
    void resetQueueHasABoundedResidenceTime() {
        assertThat(config.passwordResetQueue().getArguments())
                .containsKey("x-message-ttl")
                .containsKey("x-max-length");
    }

    /** 死信队列的 TTL 是第二个上限：进了死信也不能无限期留着那串明文令牌 */
    @Test
    void deadLetterQueueAlsoExpires() {
        Queue dlq = config.passwordResetDeadLetterQueue();

        assertThat(dlq.getName()).isEqualTo(RabbitConfig.PASSWORD_RESET_DLQ);
        assertThat(dlq.getArguments()).containsKey("x-message-ttl");
    }

    /**
     * 类型优先级必须是 {@code INFERRED}：目标类型只从监听方法签名推断，
     * 消息头里的 {@code __TypeId__} 一概不看。
     *
     * <p>若哪天有人换成默认的 {@code TYPE_ID}，这条会红——那一改等于
     * <b>让消息自己声明要变成哪个类</b>，正是 {@code RedisConfig} 里那段
     * 「default typing 是经典的反序列化利用面」说的事，只是换到了 MQ 上。
     */
    @Test
    void converterTrustsTheListenerSignatureRatherThanTheMessageHeader() {
        MessageConverter converter = config.mailMessageConverter();

        assertThat(converter).isInstanceOfSatisfying(JacksonJsonMessageConverter.class,
                jackson -> assertThat(jackson.getTypePrecedence())
                        .isEqualTo(JacksonJavaTypeMapper.TypePrecedence.INFERRED));
    }

    /** 交换机名与绑定用的常量必须与队列声明的死信参数对得上，否则消息退回时无处可去 */
    @Test
    void topologyNamesDoNotCollide() {
        assertThat(RabbitConfig.MAIL_EXCHANGE).isNotEqualTo(RabbitConfig.MAIL_DLX);
        assertThat(RabbitConfig.PASSWORD_RESET_QUEUE).isNotEqualTo(RabbitConfig.PASSWORD_RESET_DLQ);
        assertThat(RabbitConfig.MAIL_ROUTING_KEY).isNotEqualTo(RabbitConfig.MAIL_DEAD_ROUTING_KEY);
    }
}
