package com.wlf.ai.chat.tool;

import com.wlf.ai.infrastructure.vectorstore.KbChunkFields;
import org.springframework.ai.document.Document;

import java.util.Map;

/** 一条知识库检索命中。 */
public record KbHit(String title, String source, String text) {

    static KbHit from(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KbHit(
                stringValue(metadata.get(KbChunkFields.TITLE)),
                sourceOf(metadata),
                document.getText() == null ? "" : document.getText());
    }

    /** 溯源优先用官网地址；manifest 接入前 source_url 为空，退回知识库内的相对路径。 */
    private static String sourceOf(Map<String, Object> metadata) {
        String url = stringValue(metadata.get(KbChunkFields.SOURCE_URL));
        return url.isEmpty() ? stringValue(metadata.get(KbChunkFields.DOCUMENT_PATH)) : url;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
