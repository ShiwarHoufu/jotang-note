package com.wlf.note.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>三个任务：孤儿对象对账（打 OSS，可能慢）、浏览量回写（60s 一轮）、死信队列深度巡检
 * （一次 AMQP 查询，很快）。线程池配 2（{@code spring.task.scheduling.pool.size}），
 * 避免慢的 purge 把 60s 回写顶住。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
