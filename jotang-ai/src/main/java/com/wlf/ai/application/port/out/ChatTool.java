package com.wlf.ai.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 可交给模型调用的一个工具。
 *
 * @param handler 入参由模型给出（JSON 反序列化后的键值对），返回值会被序列化成工具结果
 */
public record ChatTool(String name,
                       String description,
                       List<ChatToolParameter> parameters,
                       Function<Map<String, Object>, Object> handler) {

    public ChatTool {
        parameters = List.copyOf(parameters);
    }

    public Object invoke(Map<String, Object> arguments) {
        return handler.apply(arguments);
    }
}
