package com.wlf.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 形态：<b>什么都不做</b>。{@code jotang.purge.mode=none} 时顶上，让
 * {@code NoteService} 那类调用方不必知道开关的存在。
 *
 * <p>之所以能是个空实现，是因为「补偿删除失败留下的那一行日志」本就在调用方的 catch 里
 * （{@code NoteService#purgeQuietly}），不归这里管。这里多写一条反而会变成噪声：内联删除
 * 通常都成功，那时报「有个孤儿待清理」是在说不存在的事。
 *
 * <p>与邮件那边 {@code @Primary} 的写法不同：那里 {@code SmtpMailService} 必须<b>始终</b>存在
 * （消费者要用它发信），两个实现会同时在场；这里两个实现互斥，两个条件就够了，不必再挑一个
 * {@code @Primary} 出来。
 */
@Component
@ConditionalOnProperty(name = "jotang.purge.mode", havingValue = "none", matchIfMissing = true)
public class NoOpOrphanObjectPublisher implements OrphanObjectPublisher {

    @Override
    public void publish(Bucket bucket, String storageKey) {
        // 刻意留空：见类注释。这个类的存在本身就是「默认不接中间件」这条约定的落点
    }
}
