package com.wlf.ai.infrastructure.llm;

import java.util.Objects;

/** 一条送进模型的历史消息。 */
public record LlmMessage(LlmRole role, String content) {

    public LlmMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }
}
