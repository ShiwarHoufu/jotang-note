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
 *
 * <h2>为什么必须自定义，不能用自动配置的那个</h2>
 *
 * Boot 自动配置的 {@code RedisTemplate<Object, Object>} 用<b> JDK 序列化</b>：写进 Redis 的
 * 是一坨带类名的二进制。三个后果——{@code redis-cli} 里 {@code GET} 出来是乱码（出问题时
 * 等于没有观测手段）、缓存的值必须 {@code implements Serializable}、格式与语言绑死。
 * 换成 JSON 后这三条一起消失。
 *
 * <h2>为什么是 GenericJacksonJsonRedisSerializer，而不是名字更像的 GenericJackson2JsonRedisSerializer</h2>
 *
 * <p>后者才是网上教程里那个「标准答案」，但它<b>用的是 Jackson 2</b>
 * （{@code com.fasterxml.jackson.databind}）。本项目主栈是 Boot 4，已经全面转向 <b>Jackson 3</b>
 * （{@code tools.jackson}），Jackson 2 只是 {@code jjwt-jackson} 捎进来的、且是 {@code runtime}
 * 作用域——<b>编译期根本不可见</b>。要用它就得在 pom 里显式再引一套 Jackson 2，
 * 于是应用同时拖着两个大版本，两套注解、两套安全补丁，得不偿失。
 *
 * <p>{@link GenericJacksonJsonRedisSerializer}（没有那个「2」）是 spring-data-redis 4.0 新增的
 * Jackson 3 版本，正好对上主栈，无需任何额外依赖。
 *
 * <h2>为什么要开 default typing</h2>
 *
 * <p>模板的值类型是 {@code Object}，反序列化时 Jackson 拿不到目标类型。不开类型信息的话，
 * 读回来的是 {@code LinkedHashMap}，一出 {@code (SomeRecord) get(key)} 就 ClassCastException。
 * 开启后 JSON 里会多一个 {@code @class} 字段记录具体类型——这也是为什么 {@code redis-cli} 里
 * 看到的数据会带这个字段，属于预期。
 *
 * <p><b>类型校验器必须收紧</b>：default typing 配合放任的校验器是经典的反序列化利用面
 * （攻击者能控制 Redis 内容时，可借类型信息构造任意对象）。这里只放行本项目与集合、时间三类，
 * 缓存里实际会出现的类型都在其中。
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
