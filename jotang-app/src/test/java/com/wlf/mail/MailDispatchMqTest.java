package com.wlf.mail;

import com.wlf.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 队列链路的<b>真实 Broker</b> 集成测试：拓扑声明、消费、重试、死信。见《概要设计》§6.6。
 *
 * <p>默认被 pom 的 {@code excludedGroups} 排除，单独运行：
 * <pre>
 *   docker compose up -d
 *   ./mvnw test -DexcludedGroups= -Dgroups=mq
 * </pre>
 *
 * <p><b>为什么非要有这一层。</b>mock 验不到「Broker 那边实际发生了什么」：
 * 队列参数声明成功没有、重试耗尽之后消息到底去了哪里、死信交换机配没配对。
 * 这几件事全靠配置，而配置错了<b>不会报错</b>——消息静静地消失，日志一片正常。
 * 与 {@code OrphanObjectSweeperOssTest} 是同一条理由：有些链路只有真打过去才验得了。
 *
 * <p>发信本身被 {@link MockitoBean} 换掉了：这里要验的是「消息怎么流转」，
 * 不是 SMTP。所以本类<b>不需要真实的 SMTP 授权码</b>，只需要一个跑着的 Broker。
 */
@Tag("mq")
@SpringBootTest(properties = "jotang.mail.mode=queue")
@ActiveProfiles("local")
class MailDispatchMqTest {

    /** 重试退避是 2s + 4s，再算上死信路由，30 秒足够宽裕 */
    private static final long DLQ_WAIT_MILLIS = 30_000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    /**
     * 把 {@link SmtpMailService} 换成 mock。
     *
     * <p>{@code MailDispatchConsumer} 注入的是这个<b>具体类</b>（不是接口，
     * 理由见它的类注释），所以这里按具体类替换，消费者拿到的才是 mock。
     */
    @MockitoBean
    private SmtpMailService smtpMailService;

    /**
     * 每条用例前清空两个队列。
     *
     * <p>与库表不同，Broker 里的消息**不在测试事务的回滚范围内**——上一次跑剩下的消息
     * 会被这次消费掉，让断言看起来「多了一次」。这是 OSS 那个集成测试要手工清桶的同一类问题。
     */
    @BeforeEach
    void drainQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.PASSWORD_RESET_QUEUE, false);
        amqpAdmin.purgeQueue(RabbitConfig.PASSWORD_RESET_DLQ, false);
    }

    @Test
    void aValidMessageIsConsumedAndMailsOut() {
        rabbitTemplate.convertAndSend(RabbitConfig.MAIL_EXCHANGE, RabbitConfig.MAIL_ROUTING_KEY,
                new MailDispatch(UUID.randomUUID().toString(), "zztest-mq-ok@example.com", "主题", "正文"));

        // timeout(...) 而不是 sleep：消费者是另一个线程，什么时候跑完没有保证
        verify(smtpMailService, timeout(20_000))
                .send("zztest-mq-ok@example.com", "主题", "正文");
    }

    /**
     * 重试耗尽 → 进死信队列。这条是整个 Phase 2 最核心的行为。
     *
     * <p>它同时验了三件配套的事：{@code retry} 真的在重试（SMTP 被调了 3 次）、
     * {@code default-requeue-rejected=false} 让消息没有被无限重投、
     * 队列上的 {@code x-dead-letter-exchange} 真的把消息路由到了死信队列。
     * 少了任何一样，这条都会以不同的方式失败——而少了第三样时，消息是<b>直接消失</b>。
     */
    @Test
    void exhaustedRetriesLandInTheDeadLetterQueue() {
        doThrow(new AmqpException("SMTP 挂了"))
                .when(smtpMailService).send(anyString(), anyString(), anyString());

        rabbitTemplate.convertAndSend(RabbitConfig.MAIL_EXCHANGE, RabbitConfig.MAIL_ROUTING_KEY,
                new MailDispatch(UUID.randomUUID().toString(), "zztest-mq-fail@example.com", "主题", "正文"));

        Message dead = rabbitTemplate.receive(RabbitConfig.PASSWORD_RESET_DLQ, DLQ_WAIT_MILLIS);

        assertThat(dead).as("重试耗尽的邮件应当落进死信队列，而不是被丢掉").isNotNull();
        // x-death 的 reason：rejected 表示「被 reject 且不重新入队」，正是我们要的那条路
        assertThat(xDeathReasons(dead)).contains("rejected");
        // max-retries = 3（Boot 4 的键名，见 application.yaml）→ 1 次初始 + 3 次重试 = 4 次。
        // 这个数字是**实测**出来的：最初按「3 次」写，跑出来是 4 次，才发现 Boot 4 把
        // max-attempts 改名成了 max-retries，而写错的属性被静默忽略、默认值恰好也是 3 次重试
        verify(smtpMailService, times(4)).send(anyString(), anyString(), anyString());
    }

    /**
     * 正文不是合法 JSON 的消息也必须进死信队列，而不是把消费者打挂。
     *
     * <p><b>能验的是「进了死信队列且一封邮件都没发」。</b>「它有没有被重试」在外部观察不到
     * ——容器内的重试不产生新的投递，{@code x-death} 计数因此恒为 1，而 SMTP 一次都没被调用
     * 也就无从计数。不把观察不到的事写成断言。
     */
    @Test
    void malformedPayloadEndsUpInTheDeadLetterQueue() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitConfig.MAIL_EXCHANGE, RabbitConfig.MAIL_ROUTING_KEY,
                new Message("{ 这不是合法的 JSON".getBytes(StandardCharsets.UTF_8), properties));

        Message dead = rabbitTemplate.receive(RabbitConfig.PASSWORD_RESET_DLQ, DLQ_WAIT_MILLIS);

        assertThat(dead).isNotNull();
        assertThat(xDeathReasons(dead)).contains("rejected");
        verify(smtpMailService, never()).send(anyString(), anyString(), anyString());
    }

    /**
     * {@code x-death} 是 Broker 加上去的头，记录这条消息「死过几次、因为什么」。
     *
     * <p>没有它就只能断言「消息在死信队列里」，而分不清它是被 reject 进来的、
     * 还是因为 TTL 到期被转过来的——后者意味着消息在队列里放坏了，是完全不同的故障。
     */
    private static List<String> xDeathReasons(Message message) {
        List<Map<String, ?>> header = message.getMessageProperties().getXDeathHeader();
        assertThat(header).as("死信消息应当带着 x-death 头").isNotNull();
        return header.stream().map(entry -> String.valueOf(entry.get("reason"))).toList();
    }
}
