package com.wlf.config;

import com.wlf.ai.AiRateLimiter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 模块的 Bean 装配：{@link ChatClient} 与两条接口各自的限频器。
 *
 * <p><b>为什么显式声明成一个具名 Bean</b>，而不是让调用方各自注入 {@code ChatClient.Builder}
 * 再 {@code build()}：一是 service 只依赖一个稳定的入参，单元测试里可以直接把它换掉、
 * 完全不必碰 Spring AI；二是将来要加默认系统提示或默认选项时，有一个明确的落点。
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
