package com.wlf.ai.domain.chat;

/** 单轮对话的附件读取预算。 */
public final class ReadBudget {

    private final int max;
    private int used;

    public ReadBudget(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive");
        }
        this.max = max;
    }

    public boolean canRead() {
        return used < max;
    }

    public void use() {
        used++;
    }

    public int max() {
        return max;
    }
}
