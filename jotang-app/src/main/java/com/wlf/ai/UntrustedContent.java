package com.wlf.ai;

/**
 * 把用户上传的正文喂给模型时的信封锁标，由对话的 {@code NoteTools} 与摘要的文本路径共用。
 */
final class UntrustedContent {

    static final String BEGIN = "<<<NOTE_CONTENT>>>";
    static final String END = "<<<END_NOTE_CONTENT>>>";

    /** 擦掉正文里出现的标记本身，否则可以用一个闭合标记提前跳出信封 */
    static String defuse(String text) {
        return text.replace(BEGIN, "").replace(END, "");
    }

    private UntrustedContent() {
    }
}
