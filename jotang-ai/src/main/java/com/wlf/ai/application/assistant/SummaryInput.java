package com.wlf.ai.application.assistant;

import com.wlf.ai.domain.summary.SummaryFilePolicy;

import java.nio.charset.StandardCharsets;

/** 已经过准入的摘要输入。 */
public record SummaryInput(String contentType, byte[] bytes, String body) {

    public static SummaryInput of(String contentType, byte[] bytes) {
        return SummaryFilePolicy.isText(contentType)
                ? new SummaryInput(contentType, bytes, decodeText(bytes))
                : new SummaryInput(contentType, bytes, null);
    }

    public boolean isText() {
        return body != null;
    }

    /** 按 UTF-8 宽松解码；这段正文不落库、不回显，只喂给模型。 */
    private static String decodeText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        SummaryFilePolicy.requireTextWithinLimit(text);
        return text;
    }
}
