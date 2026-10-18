package com.wlf.config;

import com.wlf.ai.AiRateLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 模块的 Bean 装配：{@link ChatClient} 与两条接口各自的限频器。
 *
 * <p>另一个副作用需要知道：本项目仍是「只启用 chat 一个模态」的配置
 * （{@code spring.ai.model.*}，§7.1）。若哪天把 embedding 等模态打开，它们的 Bean
 * 会在启动时就要求凭据——那是条会拦住整个应用的失败路径。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient aiChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 摘要限频：每用户 20 次 / 小时
     */
    @Bean
    public AiRateLimiter aiSummaryRateLimiter() {
        return new AiRateLimiter(20, Duration.ofHours(1), "生成");
    }

    /**
     * 对话限频：每用户 30 次 / 小时。
     */
    @Bean
    public AiRateLimiter aiChatRateLimiter() {
        return new AiRateLimiter(30, Duration.ofHours(1), "对话");
    }
}
