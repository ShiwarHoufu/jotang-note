package com.wlf.ai.application.port.out;

/**
 * 工具的一个入参，最终会生成 JSON Schema 里的一条 property。
 */
public record ChatToolParameter(String name, Type type, String description) {

    public enum Type {
        STRING, INTEGER, NUMBER, BOOLEAN
    }
}
