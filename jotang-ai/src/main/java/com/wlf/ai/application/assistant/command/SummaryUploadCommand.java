package com.wlf.ai.application.assistant.command;

/** 上传场景的用例输入。 */
public record SummaryUploadCommand(Long userId, UploadedFile file, String title, String teacher) {
}
