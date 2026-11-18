package com.wlf.ai.application.port.out;

import com.wlf.ai.domain.assistant.AiMessageRole;

/**
 * 回放给模型的一条历史消息，只含角色与正文。
 */
public record ChatMessage(AiMessageRole role, String content) {
}
