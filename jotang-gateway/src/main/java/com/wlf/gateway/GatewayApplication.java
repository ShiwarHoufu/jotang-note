package com.wlf.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。
 *
 * <p>只扫描本模块：jotang-common 里有 Servlet 专属组件，不能被 WebFlux 应用扫进来。
 */
@SpringBootApplication(scanBasePackages = "com.wlf.gateway")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
