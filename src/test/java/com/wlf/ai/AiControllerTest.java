package com.wlf.ai;

import com.wlf.ai.dto.AiChatResponse;
import com.wlf.ai.dto.AiSummaryResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AiController} 的 HTTP 层验证：路由、参数绑定、鉴权与响应形状。
 *
 * <p>{@link AiService} 换成 mock——真调一次会花钱，而且结果取决于模型。
 * 编排在 {@link AiServiceTest} 与 {@link AiSummaryImagePolicyTest} 里验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AiControllerTest {

    private static final Long USER_ID = 7L;
    private static final String CONVERSATION_ID = "3f1a0b7c-9d2e-4a51-8c60-7b4f2e1d0a33";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AiService aiService;

    // ==================== 鉴权 ====================

    /** 三个端点都是付费调用，绝不能匿名可打（§8.3「所有内容接口要求登录」） */
    @Test
    void allEndpointsRequireLogin() throws Exception {
        mockMvc.perform(multipart("/api/ai/summary")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(post("/api/ai/summary/notes/{id}", 42L))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /**
     * 登录者必须取自 JWT，不能是请求参数——否则调用方就能决定「按谁的配额限频、按谁计费」。
     * 这条断言把 userId 钉死在 token 里那个值上。
     */
    @Test
    void uploaderComesFromJwtNotFromRequest() throws Exception {
        when(aiService.summarizeUpload(eq(USER_ID), any()))
                .thenReturn(new AiSummaryResponse("摘要"));

        mockMvc.perform(multipart("/api/ai/summary")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("title", "高数复习")
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk());

        verify(aiService).summarizeUpload(eq(USER_ID), any());
    }

    // ==================== 摘要：上传场景 ====================

    @Test
    void uploadReturnsSummaryInTheCommonEnvelope() throws Exception {
        when(aiService.summarizeUpload(any(), any()))
                .thenReturn(new AiSummaryResponse("这是一份高数期中复习提纲。"));

        mockMvc.perform(multipart("/api/ai/summary")
                        .file(new MockMultipartFile("file", "板书.png", "image/png", PNG))
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.summary").value("这是一份高数期中复习提纲。"));
    }

    /** 没选文件必须是带字段明细的 40001，而不是掉进兜底的 50000 */
    @Test
    void uploadWithoutFileReturnsFieldLevelParamError() throws Exception {
        mockMvc.perform(multipart("/api/ai/summary")
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("file"));
    }

    // ==================== 摘要：编辑场景 ====================

    @Test
    void editEndpointBindsNoteIdAndPassesOptionalContext() throws Exception {
        when(aiService.summarizeExistingNote(any(), any(), any(), any()))
                .thenReturn(new AiSummaryResponse("摘要"));

        mockMvc.perform(post("/api/ai/summary/notes/{id}", 42L)
                        .param("title", "高数复习")
                        .param("teacher", "张老师")
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("摘要"));

        verify(aiService).summarizeExistingNote(USER_ID, 42L, "高数复习", "张老师");
    }

    /** 上下文是可选的：前端表单还没填完也能生成（§5.8） */
    @Test
    void editEndpointWorksWithoutContext() throws Exception {
        when(aiService.summarizeExistingNote(any(), any(), any(), any()))
                .thenReturn(new AiSummaryResponse("摘要"));

        mockMvc.perform(post("/api/ai/summary/notes/{id}", 42L)
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk());

        verify(aiService).summarizeExistingNote(eq(USER_ID), eq(42L), isNull(), isNull());
    }

    // ==================== 摘要：错误码映射 ====================

    /**
     * 上游不可用必须是 <b>503 / 50300</b>，且 <b>data 为 null</b>——
     * 与 50000 刻意分开，前端据此判断该不该让用户重试（§5.7）。
     */
    @Test
    void upstreamFailureIsMappedTo503WithAiUnavailableCode() throws Exception {
        when(aiService.summarizeUpload(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(multipart("/api/ai/summary")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /** 限频走的是另一个码：42300 是「账号锁定」，42900 才是「调用太频繁」（§5.7） */
    @Test
    void rateLimitIsMappedTo429WithItsOwnCode() throws Exception {
        when(aiService.summarizeUpload(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_RATE_LIMITED, "生成过于频繁，请 3 分钟后再试"));

        mockMvc.perform(multipart("/api/ai/summary")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .header("Authorization", bearer(USER_ID)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900));
    }

    // ==================== 对话 ====================

    /** 不带 conversationId → 开新会话，回复里带回服务端生成的 id 供前端下一轮续聊 */
    @Test
    void chatReturnsReplyAndConversationIdInTheCommonEnvelope() throws Exception {
        when(aiService.chat(any(), anyString(), any()))
                .thenReturn(new AiChatResponse("你好，有什么可以帮你？", CONVERSATION_ID));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.reply").value("你好，有什么可以帮你？"))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID));

        // 用户与消息都取自请求，userId 钉死在 JWT 里那个值上
        verify(aiService).chat(USER_ID, "你好", null);
    }

    /** 带上上一轮的 id → 续聊，原样透传给 service */
    @Test
    void chatPassesExistingConversationIdThrough() throws Exception {
        when(aiService.chat(any(), anyString(), any()))
                .thenReturn(new AiChatResponse("接着答", CONVERSATION_ID));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"接着说\",\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID));

        verify(aiService).chat(USER_ID, "接着说", CONVERSATION_ID);
    }

    /** 会话标识超长是参数问题，不是「这段会话不存在」 */
    @Test
    void overlongConversationIdReturnsFieldLevelParamError() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\",\"conversationId\":\"" + "x".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("conversationId"));
    }

    /** 陌生会话（不存在，或属于别人）：40400，不区分这两种情形 */
    @Test
    void strangerConversationIsMappedTo404() throws Exception {
        when(aiService.chat(any(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\",\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /** 空消息必须是带字段明细的 40001，而不是掉进兜底的 50000 */
    @Test
    void blankMessageReturnsFieldLevelParamError() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("message"));
    }

    /** 字段整个缺席走的是同一条校验 */
    @Test
    void missingMessageReturnsFieldLevelParamError() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data[0].field").value("message"));
    }

    @Test
    void chatUpstreamFailureIsMappedTo503WithAiUnavailableCode() throws Exception {
        when(aiService.chat(any(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "对话服务暂不可用，请稍后重试"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.message").value("对话服务暂不可用，请稍后重试"));
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.issue(userId, "ZZTEST-user", "USER");
    }
}
