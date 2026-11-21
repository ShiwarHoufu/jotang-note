package com.wlf.ai.config;

import com.wlf.ai.chat.advisor.LoggingAdvisor;
import com.wlf.ai.infrastructure.quota.AiQuota;
import com.wlf.ai.infrastructure.quota.FixedWindowRateLimiter;
import com.wlf.ai.infrastructure.quota.QuotaRule;
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
    public ChatClient aiChatClient(ChatClient.Builder builder, LoggingAdvisor loggingAdvisor) {
        return builder.defaultAdvisors(loggingAdvisor).build();
    }

    @Bean
    public FixedWindowRateLimiter aiRateLimiter() {
        return new FixedWindowRateLimiter(Map.of(
                AiQuota.SUMMARY, new QuotaRule(20, Duration.ofHours(1), "生成"),
                AiQuota.CHAT, new QuotaRule(30, Duration.ofHours(1), "对话")));
    }
}
