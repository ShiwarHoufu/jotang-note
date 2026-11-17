package com.wlf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.wlf.app.client")
public class JotangNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(JotangNoteApplication.class, args);
    }

}
