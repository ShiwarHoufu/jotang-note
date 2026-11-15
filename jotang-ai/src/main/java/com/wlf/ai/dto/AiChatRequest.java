package com.wlf.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话入参。
 */
@Getter
@Setter
public class AiChatRequest {

    /** 用户这一轮说的话。 */
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息不能超过 2000 个字符")
    private String message;

    /**
     * 上一轮返回的会话 id。留空 = 开一段新会话。
     */
    @Size(max = 36, message = "会话标识不合法")
    private String conversationId;
}
