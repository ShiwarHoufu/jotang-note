package com.wlf.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wlf.ai.application.port.out.ChatMessage;
import com.wlf.ai.application.port.out.ChatTool;
import com.wlf.ai.application.port.out.ChatToolParameter;
import com.wlf.ai.domain.assistant.AiMessageRole;
import com.wlf.ai.infrastructure.client.model.SpringAiChatModelAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpringAiChatModelAdapter} 的单元测试：只验「端口契约如何落到 Spring AI 的请求上」。
 *
 * <p>业务编排在 {@code AiChatServiceTest} / {@code AiSummaryServiceTest}，工具行为在
 * {@code NoteToolsTest}；这里补的是那两层都碰不到的胶水：消息分段、工具 schema、
 * 回调参数与图片媒介。
 */
class SpringAiChatModelAdapterTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private ChatClient.CallResponseSpec call;
    private SpringAiChatModelAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class);
        call = mock(ChatClient.CallResponseSpec.class);
        adapter = new SpringAiChatModelAdapter(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
    }

    // ==================== 多轮对话 ====================

    /** 系统指令、历史、本轮消息各自落位，顺序不能乱 */
    @Test
    void chatWiresSystemHistoryAndUserMessage() {
        stubChat("回答");
        ChatTool echo = new ChatTool("echo", "回显", List.of(
                new ChatToolParameter("text", ChatToolParameter.Type.STRING, "要回显的文本")),
                args -> args.get("text"));

        String reply = adapter.chat("系统指令", List.of(
                new ChatMessage(AiMessageRole.USER, "旧问"),
                new ChatMessage(AiMessageRole.ASSISTANT, "旧答")), "新问", List.of(echo));

        assertThat(reply).isEqualTo("回答");
        verify(spec).system("系统指令");

        ArgumentCaptor<List<Message>> messages = messagesCaptor();
        assertThat(messages.getValue()).hasSize(3);
        assertThat(messages.getValue().get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.getValue().get(0).getText()).isEqualTo("旧问");
        assertThat(messages.getValue().get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.getValue().get(1).getText()).isEqualTo("旧答");
        assertThat(messages.getValue().get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.getValue().get(2).getText()).isEqualTo("新问");
    }

    /** 工具定义要生成完整的 JSON Schema，且回调真的把模型给的反序列化参数交给 handler */
    @Test
    void chatRegistersToolCallbacksWithSchemaAndRoutesArguments() {
        stubChat("回答");
        ChatTool echo = new ChatTool("echo", "回显", List.of(
                new ChatToolParameter("text", ChatToolParameter.Type.STRING, "要回显的文本")),
                args -> args.get("text"));

        adapter.chat("系统", List.of(), "问题", List.of(echo));

        ArgumentCaptor<List<ToolCallback>> callbacks = toolCallbacksCaptor();
        assertThat(callbacks.getValue()).hasSize(1);
        ToolCallback callback = callbacks.getValue().get(0);
        ToolDefinition definition = callback.getToolDefinition();
        assertThat(definition.name()).isEqualTo("echo");
        assertThat(definition.description()).isEqualTo("回显");

        JsonNode schema = readSchema(definition.inputSchema());
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.at("/properties/text/type").asText()).isEqualTo("string");
        assertThat(schema.at("/properties/text/description").asText()).isEqualTo("要回显的文本");
        assertThat(schema.at("/required/0").asText()).isEqualTo("text");

        assertThat(callback.call("{\"text\":\"你好\"}")).contains("你好");
    }

    @Test
    void chatWithoutToolsRegistersNoCallbacks() {
        stubChat("回答");

        adapter.chat("系统", List.of(), "问题", List.of());

        verify(spec, never()).toolCallbacks(anyList());
    }

    // ==================== 图文 ====================

    /** 图片必须作为 media 进 user 段，MIME 用调用方给的判定结果 */
    @Test
    void chatWithImageSendsTextAndMedia() {
        stubImage("摘要");

        adapter.chatWithImage("系统", "说明", "image/png", PNG);

        verify(spec).system("系统");
        Consumer<ChatClient.PromptUserSpec> user = userSpecConsumer();
        ChatClient.PromptUserSpec userSpec = stubUserSpec();
        user.accept(userSpec);

        verify(userSpec).text("说明");
        ArgumentCaptor<Resource> image = ArgumentCaptor.forClass(Resource.class);
        verify(userSpec).media(eq(MimeTypeUtils.parseMimeType("image/png")), image.capture());
        assertThat(((ByteArrayResource) image.getValue()).getByteArray()).containsExactly(PNG);
    }

    /** 模型返回 null 不算出错：端口这一层就把它折成空串 */
    @Test
    void nullModelTextBecomesEmptyString() {
        stubChat(null);

        assertThat(adapter.chat("系统", List.of(), "问题", List.of())).isEmpty();
    }

    // ==================== 辅助 ====================

    private void stubChat(String reply) {
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.toolCallbacks(anyList())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn(reply);
    }

    @SuppressWarnings("unchecked")
    private void stubImage(String reply) {
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(any(Consumer.class))).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn(reply);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Message>> messagesCaptor() {
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(spec).messages(captor.capture());
        return captor;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ToolCallback>> toolCallbacksCaptor() {
        ArgumentCaptor<List<ToolCallback>> captor = ArgumentCaptor.forClass(List.class);
        verify(spec).toolCallbacks(captor.capture());
        return captor;
    }

    @SuppressWarnings("unchecked")
    private Consumer<ChatClient.PromptUserSpec> userSpecConsumer() {
        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(spec).user(captor.capture());
        return captor.getValue();
    }

    private ChatClient.PromptUserSpec stubUserSpec() {
        ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
        when(userSpec.text(anyString())).thenReturn(userSpec);
        return userSpec;
    }

    private JsonNode readSchema(String schema) {
        try {
            return objectMapper.readTree(schema);
        } catch (Exception e) {
            throw new AssertionError("inputSchema 不是合法 JSON：" + schema, e);
        }
    }
}
