package com.wlf.ai.chat;

import com.wlf.ai.chat.dto.ChatHistoryResponse;
import com.wlf.ai.chat.dto.ChatRequest;
import com.wlf.ai.chat.dto.ChatResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI 对话接口：一问一答与恢复最近一次会话。 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 一问一答。 */
    @PostMapping("/api/ai/chat")
    public ApiResponse<ChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody ChatRequest request) {
        ChatService.Reply result = chatService.chat(user.userId(), request.message(), request.conversationId());
        return ApiResponse.ok(new ChatResponse(result.reply(), result.conversationId()));
    }

    /** 恢复：把该用户最近一次会话及其历史交回前端。 */
    @GetMapping("/api/ai/chat/latest")
    public ApiResponse<ChatHistoryResponse> latest(@AuthenticationPrincipal AuthenticatedUser user) {
        ChatService.History result = chatService.history(user.userId());
        List<ChatHistoryResponse.Message> messages = result.messages().stream()
                .map(row -> new ChatHistoryResponse.Message(row.role(), row.content()))
                .toList();
        return ApiResponse.ok(new ChatHistoryResponse(result.conversationId(), messages));
    }
}
