package com.wlf.ai.interfaces.web;

import com.wlf.ai.application.assistant.AiChatService;
import com.wlf.ai.application.assistant.command.ChatCommand;
import com.wlf.ai.application.assistant.result.ChatHistoryResult;
import com.wlf.ai.application.assistant.result.ChatResult;
import com.wlf.ai.interfaces.web.dto.AiChatHistoryResponse;
import com.wlf.ai.interfaces.web.dto.AiChatRequest;
import com.wlf.ai.interfaces.web.dto.AiChatResponse;
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

    private final AiChatService aiChatService;
    private final ChatAssembler assembler;

    public AiChatController(AiChatService aiChatService, ChatAssembler assembler) {
        this.aiChatService = aiChatService;
        this.assembler = assembler;
    }

    /** 一问一答。 */
    @PostMapping("/api/ai/chat")
    public ApiResponse<AiChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody AiChatRequest request) {
        ChatCommand command = assembler.toCommand(user.userId(), request);
        ChatResult result = aiChatService.chat(command);
        return ApiResponse.ok(assembler.toResponse(result));
    }

    /**
     * 恢复。
     * 把该用户最近一次会话及其历史交回前端。
     */
    @GetMapping("/api/ai/chat/latest")
    public ApiResponse<AiChatHistoryResponse> latest(@AuthenticationPrincipal AuthenticatedUser user) {
        ChatHistoryResult result = aiChatService.history(user.userId());
        return ApiResponse.ok(assembler.toResponse(result));
    }
}
