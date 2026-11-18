package com.wlf.ai.domain.chat;

import com.wlf.ai.domain.summary.SummaryFilePolicy;

/** 对话工具的检索与读取规则。 */
public final class NoteToolPolicy {

    /** 同名笔记最多回几条。标题在库上没有唯一约束 */
    public static final int MAX_HITS = 5;

    /** 单轮最多真读几份附件 */
    public static final int MAX_EXTRACTIONS = 2;

    /** 单份附件上限，与摘要同口径 */
    public static final long MAX_ATTACHMENT_BYTES = SummaryFilePolicy.MAX_SIZE_BYTES;

    /** 单份正文最多给模型多少字 */
    public static final int MAX_TEXT_CHARS = 4000;

    /** 模型常照搬用户话里的书名号。只剥首尾——标题中间真的出现《》不该动 */
    public static String normalizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.strip();
        if (title.length() >= 2) {
            char first = title.charAt(0);
            char last = title.charAt(title.length() - 1);
            boolean quoted = (first == '《' && last == '》')
                    || (first == '“' && last == '”')
                    || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'');
            if (quoted) {
                title = title.substring(1, title.length() - 1).strip();
            }
        }
        return title;
    }

    /** 按 code point 截：正文里可能有 emoji，按 UTF-16 单元截会把代理对劈成两半 */
    public static String truncate(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_TEXT_CHARS)) + "……（已截断）";
    }

    private NoteToolPolicy() {
    }
}
