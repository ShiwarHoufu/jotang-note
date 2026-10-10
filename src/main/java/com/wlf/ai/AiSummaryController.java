package com.wlf.ai;

import com.wlf.ai.dto.AiSummaryRequest;
import com.wlf.ai.dto.AiSummaryResponse;
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
 * AI 摘要接口。见《概要设计》§5.8、§6.8。
 *
 * <p>两个端点对应两条链路，差别只有「文件从哪来」：
 *
 * <ul>
 *   <li>{@code POST /api/ai/summary} —— <b>上传场景</b>。此刻文件只在浏览器手上
 *       （既不在 OSS 也不在库里），所以只能收 multipart，由前端把它交上来</li>
 *   <li>{@code POST /api/ai/summary/notes/{noteId}} —— <b>编辑场景</b>。附件早在 OSS，
 *       服务端自己取，前端不必重传</li>
 * </ul>
 *
 * <p>两个接口都要求登录（{@code SecurityConfig} 的 {@code anyRequest().authenticated()} 已覆盖），
 * 且<b>登录者一律取自 JWT 而非请求参数</b>：请求参数传 userId 就等于让调用方决定「按谁的身份
 * 计费、按谁的配额限频」。
 *
 * <p>两者都是 <b>POST</b>：它们不是对资源的幂等覆写，而是「生成一段建议」这个命名动作，
 * 每次调用都真的会去调一次付费模型。
 */
@RestController
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;

    public AiSummaryController(AiSummaryService aiSummaryService) {
        this.aiSummaryService = aiSummaryService;
    }

    /**
     * 上传场景：把待上传的图片交给模型，拿回一段建议的简介。
     *
     * <p>用 {@code @ModelAttribute} 而非 {@code @RequestBody}：multipart 里文件部分不是 JSON。
     * {@code @Valid} 保证「没选文件」得到的是带字段明细的 40001，而不是掉进兜底的 50000——
     * 理由见 {@link AiSummaryRequest}。
     */
    @PostMapping("/api/ai/summary")
    public ApiResponse<AiSummaryResponse> summarizeUpload(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @Valid @ModelAttribute AiSummaryRequest request) {
        return ApiResponse.ok(aiSummaryService.summarizeUpload(user.userId(), request));
    }

    /**
     * 编辑场景：按笔记 id 取附件生成摘要。
     *
     * <p>{@code title} / {@code teacher} 走查询参数而不是请求体：本接口本来就没有别的输入，
     * 为两个纯上下文参数再引一层 JSON 不划算。它们是<b>可选的</b>——
     * 前端按表单当前值传，取不到也能生成（见 {@code AiSummaryService#summarizeExistingNote}）。
     *
     * <p>此处不给这两个参数加 {@code @Size} 校验，长度上限由 service 内部截断兜住：
     * 它们只被拼进 prompt，超长不构成数据风险，没必要为它再引一个请求对象。
     */
    @PostMapping("/api/ai/summary/notes/{noteId}")
    public ApiResponse<AiSummaryResponse> summarizeNote(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable Long noteId,
                                                        @RequestParam(required = false) String title,
                                                        @RequestParam(required = false) String teacher) {
        return ApiResponse.ok(aiSummaryService.summarizeExistingNote(user.userId(), noteId, title, teacher));
    }
}
