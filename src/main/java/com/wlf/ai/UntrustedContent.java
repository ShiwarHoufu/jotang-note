package com.wlf.ai;

/**
 * 把用户上传的正文喂给模型时的信封锁标，由对话的 {@code NoteTools} 与摘要的文本路径共用。
 *
 * <p>信封本身不是安全边界（模型可以被说服），只是把「这是数据」标在离内容最近的地方。
 * 标记与擦除必须两处同一份：能伪造出闭合标记的一方就能提前跳出信封，
 * 两份副本只要有一处忘了擦，那边的信封就形同虚设。
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
