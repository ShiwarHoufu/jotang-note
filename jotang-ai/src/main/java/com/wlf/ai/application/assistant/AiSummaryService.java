package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.command.SummaryNoteCommand;
import com.wlf.ai.application.assistant.command.SummaryUploadCommand;
import com.wlf.ai.application.assistant.prompt.SummaryPrompt;
import com.wlf.ai.application.assistant.result.SummaryResult;
import com.wlf.ai.application.port.out.ChatModelPort;
import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.application.port.out.NoteFileRef;
import com.wlf.ai.domain.assistant.AiSummaryFilePolicy;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * AI 摘要的应用服务：上传 / 编辑两条链路。
 *
 * <p>摘要是纯读、不落任何库表；生成结果交给前端，由用户决定是否保存。
 */
@Slf4j
@Service
public class AiSummaryService {

    /** 与 {@code note.summary} 的列宽一致（§5.4）：prompt 里要求模型不超，回程还要再截一次 */
    private static final int MAX_SUMMARY_LENGTH = 512;

    private final ChatModelPort chatModelPort;
    private final AiSummaryFilePolicy filePolicy;
    private final AiRateLimiter summaryRateLimiter;
    private final NoteContentReader noteContentReader;
    private final StorageService storageService;

    public AiSummaryService(ChatModelPort chatModelPort,
                            AiSummaryFilePolicy filePolicy,
                            @Qualifier("aiSummaryRateLimiter") AiRateLimiter summaryRateLimiter,
                            NoteContentReader noteContentReader,
                            StorageService storageService) {
        this.chatModelPort = chatModelPort;
        this.filePolicy = filePolicy;
        this.summaryRateLimiter = summaryRateLimiter;
        this.noteContentReader = noteContentReader;
        this.storageService = storageService;
    }

    /** 上传场景：
     *  文件来自请求体。
     */
    public SummaryResult summarizeUpload(SummaryUploadCommand command) {

        MultipartFile file = command.file();
        // 文件准入校验（扩展名、大小、文件头魔数）
        String contentType = inspectUpload(file);
        // 读出文件字节并组装模型输入
        Payload payload = Payload.of(contentType, bytesOf(file));

        summaryRateLimiter.requireSlot(command.userId());

        return new SummaryResult(summarize(payload, command.title(), command.teacher()));
    }

    /**
     * 编辑场景：
     * 附件已在 OSS，服务端自取。
     *
     * <p>{@code title} / {@code teacher} 由前端按表单当前值传来，而不是取库里存的——
     * 用户很可能刚改过标题、还没保存就点了生成。
     */
    public SummaryResult summarizeExistingNote(SummaryNoteCommand command) {
        // 归属、状态与附件位置一并校验
        NoteFileRef file = noteContentReader.requireOwnFile(command.noteId(), command.userId());

        // 编辑场景没有字节可验，只能按库里那列 content_type 判。前端本该不给这类笔记显示按钮，
        // 走到这里说明是绕过前端直接调的
        String contentType = file.contentType();
        filePolicy.requireSummarizable(contentType);
        Payload payload = Payload.of(contentType, readObject(contentType, file));

        summaryRateLimiter.requireSlot(command.userId());

        return new SummaryResult(summarize(payload, command.title(), command.teacher()));
    }

    /**
     * 已经过准入、解码与长度判定的输入。
     */
    private record Payload(String contentType, byte[] bytes, String body) {

        // 文本类会解码出正文，非文本只带字节
        static Payload of(String contentType, byte[] bytes) {
            return AiSummaryFilePolicy.isText(contentType)
                    ? new Payload(contentType, bytes, decodeText(bytes))
                    : new Payload(contentType, bytes, null);
        }

        boolean isText() {
            return body != null;
        }
    }

    /**
     * 按 MIME 分流。
     * 两条路给的 system 指令与请求体形态都不同，
     */
    private String summarize(Payload payload, String title, String teacher) {
        return payload.isText()
                ? summarizeText(payload, title, teacher)
                : summarizeImage(payload, title, teacher);
    }

    private String summarizeImage(Payload payload, String title, String teacher) {
        return callModel(payload.contentType() + "，" + payload.bytes().length + " 字节",
                () -> chatModelPort.chatWithImage(
                        SummaryPrompt.imageSystem(MAX_SUMMARY_LENGTH),
                        SummaryPrompt.imageUser(title, teacher),
                        payload.contentType(),
                        payload.bytes()));
    }

    /** 正文进 user 段而不进 media——这是文本路径与图片路径唯一的分野 */
    private String summarizeText(Payload payload, String title, String teacher) {
        String body = payload.body();
        return callModel(payload.contentType() + "，" + body.codePointCount(0, body.length()) + " 字",
                () -> chatModelPort.chat(
                        SummaryPrompt.textSystem(MAX_SUMMARY_LENGTH),
                        SummaryPrompt.textUser(title, teacher, body)));
    }

    /**
     * 一次模型调用。
     */
    private String callModel(String source, Supplier<String> call) {
        try {
            return truncate(call.get());
        } catch (RuntimeException e) {
            log.error("调用摘要模型失败，{}", source, e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /**
     * 正文的解码与字符上限判定，两者都在 50300 的 try 之外。
     *
     * <p>按 UTF-8 宽松解码：非法字节变成替换字符，不校验也不报错。上传场景的字节是用户直接
     * 送来的、没经过 {@code NoteFilePolicy}，但这段正文从不落库、也不回显，只喂给模型，最坏结果是
     * 白花一次调用（还是他自己的配额）——为它复制一份上传侧那套分块校验不值当。
     */
    private static String decodeText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        AiSummaryFilePolicy.requireTextWithinLimit(text);
        return text;
    }

    /** prompt 里已经要求模型不超 512 字，但不能指望它守约：超了的话用户点保存会撞上 {@code @Size(max = 512)} */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_SUMMARY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_SUMMARY_LENGTH));
    }

    /** 上传场景的准入*/
    private String inspectUpload(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return filePolicy.inspect(file.getOriginalFilename(), file.getSize(), content);
        } catch (IOException e) {
            log.error("读取待摘要文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("读取待摘要文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
    }

    /**
     * 读回附件字节。
     * 大小判断放在 {@link StorageService#open} 之前
     */
    private byte[] readObject(String contentType, NoteFileRef file) {
        filePolicy.requireWithinLimit(contentType, file.size());
        try (InputStream content = storageService.open(Bucket.NOTE, file.storageKey())) {
            byte[] bytes = AiSummaryFilePolicy.isText(contentType)
                    ? content.readNBytes((int) AiSummaryFilePolicy.MAX_TEXT_BYTES + 1)
                    : content.readAllBytes();
            // 库里那一列可能与对象实际大小不符；同一道闸按真实字节再算一次
            filePolicy.requireWithinLimit(contentType, (long) bytes.length);
            return bytes;
        } catch (IOException e) {
            log.error("读取笔记附件失败，key={}", file.storageKey(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取笔记文件失败");
        }
    }
}
