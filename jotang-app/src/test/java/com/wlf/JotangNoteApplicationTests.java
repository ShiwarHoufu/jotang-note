package com.wlf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 与其余测试类一致：不加 local profile 会走 application.yaml 的默认库凭据，
// 连不上本机 MySQL（3307），上下文直接起不来。
@SpringBootTest
@ActiveProfiles("local")
class JotangNoteApplicationTests {

    @Test
    void contextLoads() {
    }

}
