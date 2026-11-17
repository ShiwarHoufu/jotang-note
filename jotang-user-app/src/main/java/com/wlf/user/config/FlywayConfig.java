package com.wlf.user.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 显式执行 Flyway 迁移。Spring Boot 4 的 Flyway 自动配置不在当前依赖里，与 ai-service 保持一致。
 */
@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }
}
