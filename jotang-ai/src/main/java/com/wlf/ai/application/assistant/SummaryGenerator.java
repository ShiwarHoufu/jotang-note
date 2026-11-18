package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.prompt.SummaryPrompt;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.domain.summary.SummaryTextPolicy;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/** 摘要的一次模型调用：按 MIME 分流、组装 prompt、回程截断。 */
@Slf4j
@Service
public class SummaryGenerator {

    private final ChatModelPort chatModelPort;

    public SummaryGenerator(ChatModelPort chatModelPort) {
        this.chatModelPort = chatModelPort;
    }

    public String generate(SummaryInput input, String title, String teacher) {
        return input.isText()
                ? generateText(input, title, teacher)
                : generateImage(input, title, teacher);
    }

    private String generateImage(SummaryInput input, String title, String teacher) {
        return callModel(input.contentType() + "，" + input.bytes().length + " 字节",
                () -> chatModelPort.chatWithImage(
                        SummaryPrompt.imageSystem(),
                        SummaryPrompt.imageUser(title, teacher),
                        input.contentType(),
                        input.bytes()));
    }

    private String generateText(SummaryInput input, String title, String teacher) {
        String body = input.body();
        return callModel(input.contentType() + "，" + body.codePointCount(0, body.length()) + " 字",
                () -> chatModelPort.chat(
                        SummaryPrompt.textSystem(),
                        SummaryPrompt.textUser(title, teacher, body)));
    }

    private String callModel(String source, Supplier<String> call) {
        try {
            return SummaryTextPolicy.truncateSummary(call.get());
        } catch (RuntimeException e) {
            log.error("调用摘要模型失败，{}", source, e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }
}
