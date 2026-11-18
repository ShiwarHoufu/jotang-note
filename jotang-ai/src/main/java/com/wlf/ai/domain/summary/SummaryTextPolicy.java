package com.wlf.ai.domain.summary;

/** 摘要文本的长度与上下文清洗规则。 */
public final class SummaryTextPolicy {

    /** 与 {@code note.summary} 的列宽一致 */
    public static final int MAX_SUMMARY_LENGTH = 512;

    /** 上下文里标题 / 教师的长度上限，照抄建表语句的列宽 */
    public static final int MAX_TITLE_LENGTH = 128;
    public static final int MAX_TEACHER_LENGTH = 64;

    /** prompt 里已经要求模型不超，但不能指望它守约 */
    public static String truncateSummary(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_SUMMARY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_SUMMARY_LENGTH));
    }

    /**
     * 压掉换行与控制字符再截断：换行是这类输入里最容易被用来「伪造出下一行指令」的东西。
     *
     * @return 清洗后的值；空白输入返回 {@code null}，由 prompt 决定占位文案
     */
    public static String sanitizeContext(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flattened = value.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return flattened.length() > maxLength ? flattened.substring(0, maxLength) : flattened;
    }

    private SummaryTextPolicy() {
    }
}
