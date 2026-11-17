package com.wlf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * note-service 的独立启动类。默认扫 {@code com.wlf}，storage 的消费者一并装配。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.wlf.note.infrastructure.client")
public class NoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteApplication.class, args);
    }

}
