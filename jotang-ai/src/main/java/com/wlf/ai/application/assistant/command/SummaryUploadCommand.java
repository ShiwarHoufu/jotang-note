package com.wlf.ai.application.assistant.command;

import org.springframework.web.multipart.MultipartFile;

/** 上传场景的用例输入；file 暂为 web 类型，后续步骤换成中性输入。 */
public record SummaryUploadCommand(Long userId, MultipartFile file, String title, String teacher) {
}
