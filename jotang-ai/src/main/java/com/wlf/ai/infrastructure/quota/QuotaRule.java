package com.wlf.ai.infrastructure.quota;

import java.time.Duration;

/**
 * 一个额度档位。
 *
 * @param action 被拒文案的主语，如「生成」「对话」
 */
public record QuotaRule(int maxRequests, Duration window, String action) {

    public QuotaRule {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
    }
}
