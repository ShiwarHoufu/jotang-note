package com.wlf;

import com.wlf.storage.OssConfig;
import com.wlf.storage.OssStorageService;
import com.wlf.storage.QueuedOrphanObjectPublisher;
import com.wlf.storage.StorageRabbitConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * user-service 的独立启动类。
 */
@SpringBootApplication(scanBasePackages = {"com.wlf.user", "com.wlf.common"})
@MapperScan("com.wlf.user.infrastructure.persistence.mapper")
@Import({OssConfig.class, OssStorageService.class, StorageRabbitConfig.class, QueuedOrphanObjectPublisher.class})
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
