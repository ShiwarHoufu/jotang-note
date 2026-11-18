package com.wlf.ai.application.assistant.command;

import java.io.IOException;
import java.io.InputStream;

/** 待摘要的上传文件；opener 每次调用都返回新的流。 */
public record UploadedFile(String filename, long size, Opener opener) {

    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }
}
