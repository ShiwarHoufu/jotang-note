package com.wlf.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link OrphanObjectConsumer} 的单元测试：mock 掉 {@link StorageService}，
 * <b>不起 Spring 上下文，也不需要 Broker</b>。消息怎么流转由 {@code @Tag("mq")} 的
 * {@code OrphanObjectMqTest} 覆盖。
 */
class OrphanObjectConsumerTest {

    private static final String KEY = "notes/2026/10/00000000-0000-0000-0000-000000000000.png";

    private final StorageService storageService = mock(StorageService.class);
    private final OrphanObjectConsumer consumer = new OrphanObjectConsumer(storageService);

    @Test
    void deletesTheObjectCarriedByTheMessage() {
        consumer.onMessage(new OrphanObject(Bucket.NOTE, KEY));

        verify(storageService).delete(Bucket.NOTE, KEY);
    }

    /** 桶跟着载荷走，不在消费者里写死 NOTE：这条链路留了给头像复用的口子（见 {@link OrphanObject}） */
    @Test
    void routesToTheBucketCarriedByTheMessage() {
        consumer.onMessage(new OrphanObject(Bucket.AVATAR, KEY));

        verify(storageService).delete(Bucket.AVATAR, KEY);
    }

    /**
     * 删除失败必须原样外抛：抛出去容器才会重试，重试耗尽后消息进死信队列。
     * 本方法若在这里把异常咽掉，消息会被正常 ack，孤儿就此无声无息地留下——比不投 MQ 还糟。
     */
    @Test
    void deleteFailurePropagatesSoTheContainerRetriesAndDeadLetters() {
        doThrow(new IllegalStateException("模拟 OSS 故障"))
                .when(storageService).delete(Bucket.NOTE, KEY);

        assertThatThrownBy(() -> consumer.onMessage(new OrphanObject(Bucket.NOTE, KEY)))
                .isInstanceOf(IllegalStateException.class);
    }
}
