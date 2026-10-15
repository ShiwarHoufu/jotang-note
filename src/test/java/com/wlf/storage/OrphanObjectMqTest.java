package com.wlf.storage;

import com.wlf.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 孤儿对象队列链路的<b>真实 Broker</b> 集成测试：拓扑声明、消费、重试、死信。
 * 形状照 {@code MailDispatchMqTest}，理由也相同——mock 验不到「Broker 那边实际发生了什么」，
 * 而配置错了不会报错，消息只是静静地消失或者永远留在队列里。
 *
 * <p>默认被 pom 的 {@code excludedGroups} 排除，单独运行：
 * <pre>
 *   docker compose up -d
 *   ./mvnw test -DexcludedGroups= -Dgroups=mq
 * </pre>
 */
@Tag("mq")
@SpringBootTest(properties = "jotang.purge.mode=queue")
@ActiveProfiles("local")
class OrphanObjectMqTest {

    /** 重试退避是 2s + 4s，再算上死信路由，30 秒足够宽裕 */
    private static final long DLQ_WAIT_MILLIS = 30_000;

    private static final String OK_KEY = "notes/2026/10/zztest-mq-orphan-ok.png";
    private static final String FAIL_KEY = "notes/2026/10/zztest-mq-orphan-fail.png";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    /** 要验的是「消息怎么流转」，不是 OSS。所以这里不需要网络，也打不到真桶 */
    @MockitoBean
    private StorageService storageService;

    /**
     * 每条用例前清空两个队列。Broker 里的消息不在测试事务的回滚范围内，
     * 上一次跑剩下的会被这次消费掉，让断言看起来「多了一次」（与 {@code MailDispatchMqTest} 同理）。
     */
    @BeforeEach
    void drainQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.ORPHAN_OBJECT_QUEUE, false);
        amqpAdmin.purgeQueue(RabbitConfig.ORPHAN_OBJECT_DLQ, false);
    }

    @Test
    void aReportedOrphanIsConsumedAndDeleted() {
        rabbitTemplate.convertAndSend(RabbitConfig.STORAGE_EXCHANGE, RabbitConfig.ORPHAN_OBJECT_ROUTING_KEY,
                new OrphanObject(Bucket.NOTE, OK_KEY));

        // timeout(...) 而不是 sleep：消费者是另一个线程，什么时候跑完没有保证
        verify(storageService, timeout(20_000)).delete(Bucket.NOTE, OK_KEY);
    }

    /**
     * 重试耗尽 → 进死信队列。这条是整条链路最核心的行为：它同时验了
     * {@code retry} 真的在重试、{@code default-requeue-rejected=false} 没有无限重投、
     * 以及这条队列的 {@code x-dead-letter-exchange} 真的接上了。
     *
     * <p>少了第三样时消息是<b>直接消失</b>——而这条链路的全部意义就是「别让它无声无息地消失」。
     */
    @Test
    void exhaustedRetriesLandInTheDeadLetterQueue() {
        doThrow(new AmqpException("OSS 挂了")).when(storageService).delete(any(), anyString());

        rabbitTemplate.convertAndSend(RabbitConfig.STORAGE_EXCHANGE, RabbitConfig.ORPHAN_OBJECT_ROUTING_KEY,
                new OrphanObject(Bucket.NOTE, FAIL_KEY));

        Message dead = rabbitTemplate.receive(RabbitConfig.ORPHAN_OBJECT_DLQ, DLQ_WAIT_MILLIS);

        assertThat(dead).as("重试耗尽的孤儿对象应当落进死信队列，而不是被丢掉").isNotNull();
        assertThat(xDeathReasons(dead)).contains("rejected");
        // 与邮件那条一致：max-retries = 3 → 1 次初始 + 3 次重试 = 4 次
        verify(storageService, times(4)).delete(any(), anyString());
    }

    /** {@code x-death} 记录这条消息「死过几次、因为什么」，用它把「被 reject」与「TTL 到期」分开 */
    private static List<String> xDeathReasons(Message message) {
        List<Map<String, ?>> header = message.getMessageProperties().getXDeathHeader();
        assertThat(header).as("死信消息应当带着 x-death 头").isNotNull();
        return header.stream().map(entry -> String.valueOf(entry.get("reason"))).toList();
    }
}
