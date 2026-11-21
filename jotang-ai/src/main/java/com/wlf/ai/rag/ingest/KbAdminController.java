package com.wlf.ai.rag.ingest;

import com.wlf.ai.rag.ingest.dto.KbReindexResponse;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识库运维接口：手动触发全量导入。 */
@RestController
public class KbAdminController {

    private final KbIngestionService ingestionService;

    public KbAdminController(KbIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 全量导入知识库目录。
     *
     * <p>只允许 ADMIN
     */
    @PostMapping("/api/ai/kb/reindex")
    public ApiResponse<KbReindexResponse> reindex(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!user.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        KbIngestionService.IngestReport report = ingestionService.ingestAll();
        return ApiResponse.ok(new KbReindexResponse(report.total(), report.indexed(), report.failed()));
    }
}
