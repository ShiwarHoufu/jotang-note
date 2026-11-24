package com.wlf.note.application.command;

import java.io.IOException;
import java.io.InputStream;

/** 应用层的上传文件抽象，屏蔽 MultipartFile；允许重复打开流。 */
public interface UploadedFile {

    String originalFilename();

    long size();

    InputStream open() throws IOException;
}
