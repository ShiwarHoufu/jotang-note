package com.wlf.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件等。
 *
 * <p>分页插件依赖 {@code mybatis-plus-jsqlparser}（已在 pom 中），
 * 缺少该依赖时 {@link PaginationInnerInterceptor} 无法做 count 语句改写。
 *
 * <p>分页深度上限（§3.3：最多 50 页 / 前 1000 条）不在这里配，
 * 而是在各列表接口的服务层用「page × size 超过阈值即拒绝」实现——
 * 插件层的 maxLimit 只能截断单页大小，管不住页码。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
