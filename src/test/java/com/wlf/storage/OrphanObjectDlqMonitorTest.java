package com.wlf.storage;

import com.wlf.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrphanObjectDlqMonitor} 的单元测试：mock 掉 {@code AmqpAdmin}，不起上下文也不需要 Broker。
 *
 * <p><b>「非 0 才告警」这条分支本身没有断言</b>——它只体现在日志级别上，而本项目没有捕获日志
 * 的测试工具。这里能钉的是另外两件会静默出错的事：看的是不是那条队列、失败会不会把调度器打挂。
 */
class OrphanObjectDlqMonitorTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final OrphanObjectDlqMonitor monitor = new OrphanObjectDlqMonitor(amqpAdmin);

    @Test
    void looksAtTheOrphanObjectDeadLetterQueue() {
        when(amqpAdmin.getQueueInfo(RabbitConfig.ORPHAN_OBJECT_DLQ))
                .thenReturn(new QueueInformation(RabbitConfig.ORPHAN_OBJECT_DLQ, 3, 0));

        monitor.reportDepth();

        verify(amqpAdmin).getQueueInfo(RabbitConfig.ORPHAN_OBJECT_DLQ);
    }

    /** 队列不存在（比如声明失败过）时 getQueueInfo 会抛，这里必须咽掉 */
    @Test
    void aMissingQueueDoesNotKillTheScheduledTask() {
        when(amqpAdmin.getQueueInfo(anyString())).thenThrow(new AmqpException("队列不存在"));

        assertThatCode(monitor::reportDepth).doesNotThrowAnyException();
    }
}
