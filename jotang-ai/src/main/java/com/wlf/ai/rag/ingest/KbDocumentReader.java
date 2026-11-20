package com.wlf.ai.rag.ingest;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 把找到的 {@link Resource} 交给 Spring AI 的 Tika reader，只额外做准入与空文档检查。 */
@Slf4j
@Component
public class KbDocumentReader {

    /** 单份文件的大小上限 */
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    /** 正文上限*/
    private static final int MAX_TEXT_CHARS = 1_000_000;
    /** 低于这个字数多半是扫描件或空文件 */
    private static final int MIN_TEXT_CHARS = 20;

    private static final Set<String> SUPPORTED = Set.of(
            "md", "markdown", "txt", "pdf", "doc", "docx", "html", "htm");
    private static final Set<String> PLAIN_TEXT = Set.of("md", "markdown", "txt");

    private static final Charset GB18030 = Charset.forName("GB18030");

    public List<Document> read(Resource resource) {
        String filename = filenameOf(resource);
        String extension = extensionOf(filename);
        if (!SUPPORTED.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "知识库暂不支持这种文件类型：." + extension);
        }
        requireSize(resource, filename);

        List<Document> documents;
        try {
            // 纯文本走 TextReader：Tika 对无 MIME 的 GBK 文本会直接读成空内容
            documents = PLAIN_TEXT.contains(extension)
                    ? readPlainText(resource)
                    : new TikaDocumentReader(resource).get();
        } catch (IOException | RuntimeException e) {
            log.error("解析文档失败：{}", filename, e);
            throw new BusinessException(ErrorCode.FILE_INVALID, "解析文档失败：" + filename);
        }
        documents = stripBom(documents);
        requireText(filename, documents);
        return documents;
    }

    private static List<Document> readPlainText(Resource resource) throws IOException {
        byte[] bytes = resource.getInputStream().readAllBytes();
        ByteArrayResource source = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return resource.getFilename();
            }
        };

        TextReader reader = new TextReader(source);
        reader.setCharset(detectCharset(bytes));
        return reader.get();
    }

    /** 先认 BOM，再按 UTF-8 严格解码，失败退回 GB18030。 */
    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return GB18030;
        }
    }

    /** 文本用 UTF-8 读带 BOM 的文件时，BOM 会变成正文首字符。 */
    private static List<Document> stripBom(List<Document> documents) {
        return documents.stream().map(document -> {
            String text = document.getText();
            if (text == null || !text.startsWith("﻿")) {
                return document;
            }
            return Document.builder()
                    .id(document.getId())
                    .text(text.substring(1))
                    .metadata(document.getMetadata())
                    .build();
        }).toList();
    }

    private static String filenameOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件缺少文件名");
        }
        return filename;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void requireSize(Resource resource, String filename) {
        long size;
        try {
            size = resource.contentLength();
        } catch (IOException e) {
            log.error("读取文件大小失败：{}", filename, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
        if (size <= 0) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容为空：" + filename);
        }
        if (size > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件超过 " + (MAX_FILE_BYTES >> 20) + "MB：" + filename);
        }
    }

    private static void requireText(String filename, List<Document> documents) {
        int chars = documents.stream()
                .mapToInt(document -> document.getText() == null
                        ? 0
                        : document.getText().codePointCount(0, document.getText().length()))
                .sum();
        if (chars < MIN_TEXT_CHARS) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "没能从文档中提取到有效文字，可能是扫描件：" + filename);
        }
        if (chars > MAX_TEXT_CHARS) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文档正文超过 " + MAX_TEXT_CHARS + " 字：" + filename);
        }
    }
}
