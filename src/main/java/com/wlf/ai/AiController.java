package com.wlf.ai;

import com.wlf.ai.dto.AiChatHistoryResponse;
import com.wlf.ai.dto.AiChatRequest;
import com.wlf.ai.dto.AiChatResponse;
import com.wlf.ai.dto.AiSummaryRequest;
import com.wlf.ai.dto.AiSummaryResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模块的接口层：摘要（上传 / 编辑两条链路）与对话。
 */
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    // ==================== 摘要 ====================

    /**
     * 上传场景：把待上传的图片或文本交给模型，拿回一段建议的简介。
     */
    @PostMapping("/api/ai/summary")
    public ApiResponse<AiSummaryResponse> summarizeUpload(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @Valid @ModelAttribute AiSummaryRequest request) {
        return ApiResponse.ok(aiService.summarizeUpload(user.userId(), request));
    }

    /**
     * 编辑场景：按笔记 id 取附件生成摘要。
     */
    @PostMapping("/api/ai/summary/notes/{noteId}")
    public ApiResponse<AiSummaryResponse> summarizeNote(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable Long noteId,
                                                        @RequestParam(required = false) String title,
                                                        @RequestParam(required = false) String teacher) {
        return ApiResponse.ok(aiService.summarizeExistingNote(user.userId(), noteId, title, teacher));
    }

    // ==================== 对话 ====================

    /** 一问一答。 */
    @PostMapping("/api/ai/chat")
    public ApiResponse<AiChatResponse> chat(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.ok(aiService.chat(user.userId(), request.getMessage(), request.getConversationId()));
    }

    /**
     * 恢复。
     * 把该用户最近一次会话及其历史交回前端，刷新页面后据此接回上一段对话。
     */
    @GetMapping("/api/ai/chat/latest")
    public ApiResponse<AiChatHistoryResponse> latest(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(aiService.history(user.userId()));
    }
}
