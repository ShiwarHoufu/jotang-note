package com.wlf.ai.infrastructure.client.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import feign.Response;
import feign.codec.ErrorDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * 把 note 的 {@code {code, message}} 响应体翻回业务异常，保持与本地调用一致的错误语义。
 */
public class NoteClientErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    public NoteClientErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() != null) {
            try (InputStream body = response.body().asInputStream()) {
                JsonNode node = objectMapper.readTree(body);
                int code = node.path("code").asInt();
                String message = node.path("message").asString();
                return new BusinessException(ErrorCode.fromCode(code), message);
            } catch (Exception ignored) {
                // 响应体不是约定的信封，退回通用错误
            }
        }
        return new BusinessException(ErrorCode.SERVER_ERROR, "笔记服务暂不可用");
    }
}
