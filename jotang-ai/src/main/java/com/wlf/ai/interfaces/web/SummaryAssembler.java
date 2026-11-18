package com.wlf.ai.interfaces.web;

import com.wlf.ai.application.assistant.command.SummaryNoteCommand;
import com.wlf.ai.application.assistant.command.SummaryUploadCommand;
import com.wlf.ai.application.assistant.result.SummaryResult;
import com.wlf.ai.interfaces.web.dto.AiSummaryRequest;
import com.wlf.ai.interfaces.web.dto.AiSummaryResponse;
import org.springframework.stereotype.Component;

/** 摘要 HTTP 契约与用例输入/输出之间的转换。 */
@Component
public class SummaryAssembler {

    public SummaryUploadCommand toCommand(Long userId, AiSummaryRequest request) {
        return new SummaryUploadCommand(userId, request.getFile(), request.getTitle(), request.getTeacher());
    }

    public SummaryNoteCommand toCommand(Long userId, Long noteId, String title, String teacher) {
        return new SummaryNoteCommand(userId, noteId, title, teacher);
    }

    public AiSummaryResponse toResponse(SummaryResult result) {
        return new AiSummaryResponse(result.summary());
    }
}
