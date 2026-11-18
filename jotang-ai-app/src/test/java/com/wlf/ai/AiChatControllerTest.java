package com.wlf.ai;

import com.wlf.ai.application.assistant.AiChatService;
import com.wlf.ai.application.assistant.dto.AiChatHistoryResponse;
import com.wlf.ai.application.assistant.dto.AiChatResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link com.wlf.ai.interfaces.web.AiChatController} 的 HTTP 层验证：路由、参数绑定、鉴权与响应形状。
 *
 * <p>{@link AiChatService} 换成 mock——真调一次会花钱，而且结果取决于模型。
 * 编排在 {@link AiChatServiceTest} 里验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AiChatControllerTest {

    private static final Long USER_ID = 7L;
    private static final String CONVERSATION_ID = "3f1a0b7c-9d2e-4a51-8c60-7b4f2e1d0a33";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AiChatService aiChatService;

    // ==================== 鉴权 ====================

    /** {@code chat} 是付费调用，绝不能匿名可打（§8.3「所有内容接口要求登录」）；
     *  {@code latest} 不花钱，但读的是本人的会话内容，同样只能凭 token 认人 */
    @Test
    void allEndpointsRequireLogin() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/ai/chat/latest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ==================== 对话 ====================

    /** 不带 conversationId → 开新会话，回复里带回服务端生成的 id 供前端下一轮续聊 */
    @Test
    void chatReturnsReplyAndConversationIdInTheCommonEnvelope() throws Exception {
        when(aiChatService.chat(any(), anyString(), any()))
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
        verify(aiChatService).chat(USER_ID, "你好", null);
    }

    /** 带上上一轮的 id → 续聊，原样透传给 service */
    @Test
    void chatPassesExistingConversationIdThrough() throws Exception {
        when(aiChatService.chat(any(), anyString(), any()))
                .thenReturn(new AiChatResponse("接着答", CONVERSATION_ID));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"接着说\",\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID));

        verify(aiChatService).chat(USER_ID, "接着说", CONVERSATION_ID);
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
        when(aiChatService.chat(any(), anyString(), any()))
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
        when(aiChatService.chat(any(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE, "对话服务暂不可用，请稍后重试"));

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.message").value("对话服务暂不可用，请稍后重试"));
    }

    // ==================== 恢复最近会话 ====================

    @Test
    void latestReturnsHistoryInTheCommonEnvelope() throws Exception {
        when(aiChatService.history(USER_ID)).thenReturn(new AiChatHistoryResponse(CONVERSATION_ID, List.of(
                new AiChatHistoryResponse.Message("USER", "第一个问题"),
                new AiChatHistoryResponse.Message("ASSISTANT", "第一个回答"))));

        mockMvc.perform(get("/api/ai/chat/latest").header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[0].content").value("第一个问题"))
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"));
    }

    /**
     * 空载荷的 JSON 形态钉在这里：{@code conversationId} 是 {@code null}、{@code messages} 是空数组，
     * 状态码 200、业务码 0。前端恢复流程只判这个形态，它变了前端就跟着错。
     */
    @Test
    void latestReturnsEmptyPayloadWhenThereIsNothingToResume() throws Exception {
        when(aiChatService.history(USER_ID)).thenReturn(AiChatHistoryResponse.empty());

        mockMvc.perform(get("/api/ai/chat/latest").header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value(nullValue()))
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    /** 读谁的会话由 token 决定，不能由请求参数决定 */
    @Test
    void latestReadsTheSessionOfTheTokenOwner() throws Exception {
        when(aiChatService.history(USER_ID)).thenReturn(AiChatHistoryResponse.empty());

        mockMvc.perform(get("/api/ai/chat/latest").header("Authorization", bearer(USER_ID)))
                .andExpect(status().isOk());

        verify(aiChatService).history(USER_ID);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.issue(userId, "ZZTEST-user", "USER");
    }
}
