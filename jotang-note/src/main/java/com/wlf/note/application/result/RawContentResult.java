package com.wlf.note.application.result;

import java.io.InputStream;

/** 文本预览的原文与长度。 */
public record RawContentResult(long size, InputStream content) {
}
