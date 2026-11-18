package com.wlf.ai.interfaces.web;

import com.wlf.ai.application.assistant.AiService;
import com.wlf.ai.application.assistant.dto.AiSummaryRequest;
import com.wlf.ai.application.assistant.dto.AiSummaryResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 摘要接口：上传场景与编辑场景两条链路。
 */
@RestController
public class AiSummaryController {

    private final AiService aiService;

    public AiSummaryController(AiService aiService) {
        this.aiService = aiService;
    }

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
}
