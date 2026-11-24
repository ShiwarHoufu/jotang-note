package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.UploadedFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/** MultipartFile 到应用层 UploadedFile 的适配。 */
final class MultipartUploadedFile implements UploadedFile {

    private final MultipartFile file;

    MultipartUploadedFile(MultipartFile file) {
        this.file = file;
    }

    @Override
    public String originalFilename() {
        return file.getOriginalFilename();
    }

    @Override
    public long size() {
        return file.getSize();
    }

    @Override
    public InputStream open() throws IOException {
        return file.getInputStream();
    }
}
