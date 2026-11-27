package com.wlf.ai.chat.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.GuardrailRefusal;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeGuardAdvisorTest {

    @Mock
    private DecisionModel decisionModel;
    @Mock
    private CallAdvisorChain chain;

    private ScopeGuardAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new ScopeGuardAdvisor(decisionModel);
    }

    @Test
    void outOfScopeWithHighProbabilityRefuses() {
        stubDecision(0.95);

        ChatClientResponse response = advisor.adviseCall(request("火锅还是烤肉"), chain);

        assertThat(GuardrailRefusal.isRefusal(response.chatResponse())).isTrue();
        assertThat(GuardrailRefusal.text(response.chatResponse())).isEqualTo(ScopeGuardAdvisor.REFUSAL);
        verify(chain, never()).nextCall(any());
    }

    @Test
    void atThresholdRefuses() {
        stubDecision(0.8);

        ChatClientResponse response = advisor.adviseCall(request("火锅还是烤肉"), chain);

        assertThat(GuardrailRefusal.isRefusal(response.chatResponse())).isTrue();
        verify(chain, never()).nextCall(any());
    }

    @Test
    void undecidedPasses() {
        stubDecision(0.5);
        when(chain.nextCall(any())).thenReturn(passResponse());

        advisor.adviseCall(request("火锅还是烤肉"), chain);

        verify(chain).nextCall(any());
    }

    @Test
    void inScopePasses() {
        stubDecision(0.1);
        when(chain.nextCall(any())).thenReturn(passResponse());

        advisor.adviseCall(request("推免对综测有什么要求"), chain);

        verify(chain).nextCall(any());
    }

    @Test
    void decisionFailurePasses() {
        when(decisionModel.call(any())).thenThrow(new IllegalStateException("ollama down"));
        when(chain.nextCall(any())).thenReturn(passResponse());

        advisor.adviseCall(request("推免对综测有什么要求"), chain);

        verify(chain).nextCall(any());
    }

    @Test
    void followUpCarriesRecentContext() {
        stubDecision(0.1);
        when(chain.nextCall(any())).thenReturn(passResponse());

        advisor.adviseCall(request("那第二条呢", "推免对综测有什么要求", "不低于专业前 40%。"), chain);

        ArgumentCaptor<DecisionPrompt> captor = ArgumentCaptor.forClass(DecisionPrompt.class);
        verify(decisionModel).call(captor.capture());
        FieldsInput input = (FieldsInput) captor.getValue().getInstructions();
        assertThat(input.getFields().get("latest_message")).isEqualTo("那第二条呢");
        assertThat(input.getFields().get("recent_context")).asString()
                .contains("用户：推免对综测有什么要求")
                .contains("助手：不低于专业前 40%。");
    }

    private void stubDecision(double probability) {
        when(decisionModel.call(any())).thenAnswer(invocation -> {
            DecisionPrompt prompt = invocation.getArgument(0);
            return DecisionResponse.builder(prompt)
                    .answer("scope", YesNoAnswer.builder().probability(probability).build())
                    .build();
        });
    }

    private static ChatClientRequest request(String text, String... history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("system"));
        for (int i = 0; i < history.length; i++) {
            messages.add(i % 2 == 0 ? new UserMessage(history[i]) : new AssistantMessage(history[i]));
        }
        messages.add(new UserMessage(text));
        return ChatClientRequest.builder().prompt(new Prompt(messages)).build();
    }

    private static ChatClientResponse passResponse() {
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage("正常回答"))))
                        .build())
                .context(Map.of())
                .build();
    }
}
