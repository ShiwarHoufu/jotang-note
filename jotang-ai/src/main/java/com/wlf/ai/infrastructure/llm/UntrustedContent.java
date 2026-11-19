package com.wlf.ai.infrastructure.llm;

/** 把用户上传的正文喂给模型时的信封锁标。 */
public final class UntrustedContent {

    public static final String BEGIN = "<<<NOTE_CONTENT>>>";
    public static final String END = "<<<END_NOTE_CONTENT>>>";

    /** 擦掉正文里出现的标记本身，否则可以用一个闭合标记提前跳出信封 */
    public static String defuse(String text) {
        return text.replace(BEGIN, "").replace(END, "");
    }

    private UntrustedContent() {
    }
}
