package com.wlf.ai;

import com.wlf.storage.OssConfig;
import com.wlf.storage.OssStorageService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * ai-service 的独立启动类。
 */
@SpringBootApplication(
        scanBasePackages = {"com.wlf.ai", "com.wlf.common"},
        exclude = RabbitAutoConfiguration.class)
@EnableFeignClients(basePackages = "com.wlf.ai.infrastructure.client")
@MapperScan("com.wlf.ai.infrastructure.persistence")
@Import({OssConfig.class, OssStorageService.class})
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
