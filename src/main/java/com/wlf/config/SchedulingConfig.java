package com.wlf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>线程池用默认的单线程。目前两个任务：孤儿对象对账（打 OSS，可能慢）与死信队列深度巡检
 * （一次 AMQP 查询，很快）。两者都用 {@code fixedDelay}，各自不与自身重叠；单线程意味着巡检
 * 可能排在某个慢轮次后面等着——这是可以接受的，它本来就只是「有人看一眼」。
 *
 * <p>将来加第三个<b>耗时</b>任务时要显式配 {@code TaskScheduler}，否则几者互相排队。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
