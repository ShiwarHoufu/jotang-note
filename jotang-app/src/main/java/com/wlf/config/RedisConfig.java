package com.wlf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis 配置：把 {@link RedisTemplate} 的序列化器换成「键为字符串、值为 JSON」。
 */
@Configuration
public class RedisConfig {

    /**
     * 值的序列化器：JSON + 类型信息。
     *
     * <p>单独提成 Bean 而非塞在 {@link #redisTemplate} 里，一是便于复用（将来若需要
     * 独立的 {@code StringRedisTemplate} 之外的第二套模板），二是让序列化规则能被单测直接捶
     * ——见 {@code RedisConfigTest}，它不需要 Spring 上下文，也不需要真的连 Redis。
     */
    @Bean
    public RedisSerializer<Object> redisJsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        // 缓存里会出现的：本项目自己的记录类型（含嵌套的 DTO）
                        .allowIfSubType("com.wlf.")
                        // List / Map 等集合实现（List.of() 产出的是 ImmutableCollections）
                        .allowIfSubType("java.util.")
                        // LocalDateTime 等（Jackson 3 已内置 java.time 支持，无需额外模块）
                        .allowIfSubType("java.time.")
                        .build())
                .typePropertyName("@class")
                .build();
    }

    /**
     * 业务用模板：键为 String，值为 JSON。
     *
     * <p>键用 {@link StringRedisSerializer} 而不是跟着值一起走 JSON：键是给人看的，
     * 需要能在 {@code redis-cli} 里直接 {@code KEYS note:*} 敲出来。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       RedisSerializer<Object> redisJsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(redisJsonSerializer);
        template.setHashValueSerializer(redisJsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
