package com.wlf.ai.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/** 一篇笔记附件的元信息与内容入口；内容按需打开。 */
public record NoteAttachment(String contentType, Long size, Opener opener) {

    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }

    public InputStream open() throws IOException {
        return opener.open();
    }
}
