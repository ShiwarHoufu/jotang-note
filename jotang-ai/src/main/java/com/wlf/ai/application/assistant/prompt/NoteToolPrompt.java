package com.wlf.ai.application.assistant.prompt;

/** 对话工具的描述与图片转写指令。 */
public final class NoteToolPrompt {

    public static final String TRANSCRIBE_SYSTEM = """
            你在转写一份笔记图片，转写结果会给另一个模型阅读。

            要求：
            1. 逐字转写图中所有可见文字，保留原有的分点、公式与符号；
            2. 不要总结、不要概括、不要补充图中没有的内容；
            3. 图里没有文字时，客观说明它是什么（手写板书 / 截图 / 与课程无关的照片）；
            4. 直接输出转写结果，不要任何前缀或解释性文字。
            """;

    public static final String SEARCH_NOTES_DESCRIPTION =
            "按标题精确查找本平台在线的笔记，返回笔记 id、标题、教师与简介。"
                    + "用户想找某篇笔记、或需要某篇笔记的内容时使用。"
                    + "查不到就据实告诉用户没找到，不要编造标题或内容。";

    public static final String READ_NOTE_DESCRIPTION = "根据笔记 id 读取一篇在线笔记附件的内容。";

    private NoteToolPrompt() {
    }
}
