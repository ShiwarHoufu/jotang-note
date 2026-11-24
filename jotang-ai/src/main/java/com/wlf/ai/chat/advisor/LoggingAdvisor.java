package com.wlf.ai.chat.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 打印每次模型调用的请求与响应。 */
@Slf4j
@Component
public class LoggingAdvisor implements BaseAdvisor {

    private static final JsonMapper JSON = JacksonUtils.getDefaultJsonMapper();

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("ChatClientRequest: {}", request);
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        log.info("ChatClientResponse:\n{}", response);
        return response;
    }

    /** 排在链尾：看到的是增强后真正发给模型的请求，工具循环的每一轮也会打印。 */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }



    private static String toJson(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return "null";
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(chatResponse);
        } catch (JacksonException e) {
            return String.valueOf(chatResponse);
        }
    }


}
