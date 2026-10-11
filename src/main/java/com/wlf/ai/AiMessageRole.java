package com.wlf.ai;

/**
 * 一条 AI 消息的角色。存的是 {@link #name()} 字符串（{@code ai_message.role VARCHAR(16)}），
 * 与 {@code NoteStatus} 同理：本枚举只用于业务判断，不做 MyBatis-Plus 的枚举映射。
 */
public enum AiMessageRole {

    /** 用户说的话 */
    USER,

    /** 模型的回答 */
    ASSISTANT
}
