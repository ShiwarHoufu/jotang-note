package com.wlf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>线程池用默认的单线程。目前只有一个任务，且 {@code fixedDelay} 保证同一任务不与自身重叠；
 * 将来加第二个耗时任务时要显式配 {@code TaskScheduler}，否则两者互相排队。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
