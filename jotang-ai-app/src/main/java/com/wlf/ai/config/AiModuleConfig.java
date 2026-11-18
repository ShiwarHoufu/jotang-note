package com.wlf.ai.config;

import com.wlf.ai.application.port.out.AiQuota;
import com.wlf.ai.application.port.out.RateLimitPolicy;
import com.wlf.ai.application.port.out.RateLimitPort;
import com.wlf.ai.domain.summary.SummaryFilePolicy;
import com.wlf.ai.infrastructure.ratelimit.InMemoryRateLimitAdapter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * AI 模块的装配根：ChatClient 与限流策略。
 *
 * <p>本项目目前只启用 chat 一个模态；若打开 embedding 等模态，
 * 它们的 Bean 会在启动时要求凭据。
 */
@Configuration
public class AiModuleConfig {

    @Bean
    public ChatClient aiChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public SummaryFilePolicy summaryFilePolicy() {
        return new SummaryFilePolicy();
    }

    @Bean
    public RateLimitPort rateLimitPort() {
        return new InMemoryRateLimitAdapter(Map.of(
                AiQuota.SUMMARY, new RateLimitPolicy(20, Duration.ofHours(1), "生成"),
                AiQuota.CHAT, new RateLimitPolicy(30, Duration.ofHours(1), "对话")));
    }
}
