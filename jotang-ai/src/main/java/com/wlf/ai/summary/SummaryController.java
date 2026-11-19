package com.wlf.ai.summary;

import com.wlf.ai.summary.dto.SummaryResponse;
import com.wlf.ai.summary.dto.SummaryUploadRequest;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 摘要接口：上传场景与编辑场景两条链路。 */
@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** 上传场景：把待上传的图片或文本交给模型，拿回一段建议的简介。 */
    @PostMapping("/api/ai/summary")
    public ApiResponse<SummaryResponse> summarizeUpload(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @ModelAttribute SummaryUploadRequest request) {
        String summary = summaryService.summarizeUpload(user.userId(), request.getFile(),
                request.getTitle(), request.getTeacher());
        return ApiResponse.ok(new SummaryResponse(summary));
    }

    /** 编辑场景：按笔记 id 取附件生成摘要。 */
    @PostMapping("/api/ai/summary/notes/{noteId}")
    public ApiResponse<SummaryResponse> summarizeNote(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long noteId,
                                                      @RequestParam(required = false) String title,
                                                      @RequestParam(required = false) String teacher) {
        String summary = summaryService.summarizeNote(user.userId(), noteId, title, teacher);
        return ApiResponse.ok(new SummaryResponse(summary));
    }
}
