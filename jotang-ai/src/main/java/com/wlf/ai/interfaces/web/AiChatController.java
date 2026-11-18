package com.wlf.ai.interfaces.web;

import com.wlf.ai.application.assistant.AiService;
import com.wlf.ai.application.assistant.dto.AiChatHistoryResponse;
import com.wlf.ai.application.assistant.dto.AiChatRequest;
import com.wlf.ai.application.assistant.dto.AiChatResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话接口：一问一答与恢复最近一次会话。
 */
@RestController
public class AiChatController {

    private final AiService aiService;

    public AiChatController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 一问一答。 */
    @PostMapping("/api/ai/chat")
    public ApiResponse<AiChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.ok(aiService.chat(user.userId(), request.getMessage(), request.getConversationId()));
    }

    /**
     * 恢复。
     * 把该用户最近一次会话及其历史交回前端。
     */
    @GetMapping("/api/ai/chat/latest")
    public ApiResponse<AiChatHistoryResponse> latest(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(aiService.history(user.userId()));
    }
}
