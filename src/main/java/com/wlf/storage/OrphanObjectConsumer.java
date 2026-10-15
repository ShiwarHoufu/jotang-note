package com.wlf.storage;

import com.wlf.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 队列的消费端：把 {@link OrphanObject} 真的从桶里删掉。见《功能说明：笔记上传》§2.5。
 *
 * <p>它是个薄适配器，<b>不带任何业务判断</b>——判据已经在投递侧定死了（那个 key 是补偿删除
 * 没删掉的）。幂等来自 {@link StorageService#delete} 本身：对不存在的对象同样成功，所以
 * 重复消费、重投、人工重跑都不冲突。注意这条路径<b>完全不碰 {@code is_purged}</b>：这类孤儿
 * 压根没有 {@code note_file} 行可标。
 *
 * <p>失败必须原样外抛。本方法不 try/catch，抛出去容器才会按
 * {@code spring.rabbitmq.listener.simple.retry} 重试；重试耗尽后消息被送进死信队列等人处理。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.purge.mode", havingValue = "queue")
public class OrphanObjectConsumer {

    private final StorageService storageService;

    public OrphanObjectConsumer(StorageService storageService) {
        this.storageService = storageService;
        log.info("孤儿对象消费者已启动，监听 queue={}", RabbitConfig.ORPHAN_OBJECT_QUEUE);
    }

    /**
     * 一条消息 = 一个待删对象。
     */
    @RabbitListener(queues = RabbitConfig.ORPHAN_OBJECT_QUEUE)
    public void onMessage(OrphanObject orphan) {
        // 到这里说明消息确实是 OrphanObject：转换器的类型优先级是 INFERRED，
        // 目标类型只从本方法的签名推断，消息头里的 __TypeId__ 一概不看（见 RabbitConfig）
        log.debug("收到待清理孤儿对象 bucket={} key={}", orphan.bucket(), orphan.storageKey());
        storageService.delete(orphan.bucket(), orphan.storageKey());
    }
}
