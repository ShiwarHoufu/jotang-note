package com.wlf.ai.rag.chat;

import com.wlf.ai.rag.chat.dto.RagChatRequest;
import com.wlf.ai.rag.chat.dto.RagChatResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** RAG 问答接口：基于知识库的多轮问答。 */
@RestController
public class RagChatController {

    private final RagChatService ragChatService;

    public RagChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    /** 一问一答。 */
    @PostMapping("/api/ai/rag/chat")
    public ApiResponse<RagChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody RagChatRequest request) {
        RagChatService.Reply result = ragChatService.chat(user.userId(), request.message(),
                request.conversationId());
        return ApiResponse.ok(new RagChatResponse(result.reply(), result.conversationId()));
    }
}
