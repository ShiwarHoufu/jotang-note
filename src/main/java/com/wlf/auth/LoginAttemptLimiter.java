package com.wlf.auth;

import org.springframework.stereotype.Component;

/**
 * 登录限频：仅账号维度，连续失败 5 次锁定 10 分钟；本地内存实现。
 * 见《概要设计》§6.3（决策 D3）。
 */
@Component
public class LoginAttemptLimiter {
}
