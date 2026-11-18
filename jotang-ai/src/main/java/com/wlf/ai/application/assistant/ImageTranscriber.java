package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.prompt.NoteToolPrompt;
import com.wlf.ai.application.port.out.ChatModelPort;
import org.springframework.stereotype.Service;

/** 把笔记图片转写成文字；tool result 只能带文本，主模型拿不到图片本身。 */
@Service
public class ImageTranscriber {

    private final ChatModelPort chatModelPort;

    public ImageTranscriber(ChatModelPort chatModelPort) {
        this.chatModelPort = chatModelPort;
    }

    public String transcribe(String contentType, byte[] image) {
        String raw = chatModelPort.chatWithImage(
                NoteToolPrompt.TRANSCRIBE_SYSTEM, "请转写这张图片。", contentType, image);
        return raw == null ? "" : raw.strip();
    }
}
