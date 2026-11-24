package com.wlf.ai.chat.dto;

import com.wlf.common.ErrorCode;

/**
 * 流式对话的一条 SSE 事件；data 是它的 JSON 序列化。
 *
 * @param type           start / delta / done / error
 * @param text           增量文本，仅 delta 有
 * @param conversationId 会话 id，start 与 done 有
 * @param code           业务码，仅 error 有
 * @param message        错误提示，仅 error 有
 */
public record ChatStreamEvent(String type, String text, String conversationId, Integer code, String message) {

    public static final String START = "start";
    public static final String DELTA = "delta";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    public static ChatStreamEvent start(String conversationId) {
        return new ChatStreamEvent(START, null, conversationId, null, null);
    }

    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent(DELTA, text, null, null, null);
    }

    public static ChatStreamEvent done(String conversationId) {
        return new ChatStreamEvent(DONE, null, conversationId, null, null);
    }

    public static ChatStreamEvent error(ErrorCode errorCode, String message) {
        return new ChatStreamEvent(ERROR, null, null, errorCode.getCode(), message);
    }
}
