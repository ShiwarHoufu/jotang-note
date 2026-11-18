package com.wlf.ai.application.port.out;

/** 按用户、按用例取配额的出站端口。 */
public interface RateLimitPort {

    /** 取一个配额；超出时抛 42900。 */
    void acquire(Long userId, AiQuota quota);
}
