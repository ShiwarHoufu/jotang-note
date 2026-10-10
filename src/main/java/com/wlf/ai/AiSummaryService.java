package com.wlf.ai;

import com.wlf.ai.dto.AiSummaryRequest;
import com.wlf.ai.dto.AiSummaryResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.NoteFileOwnershipRow;
import com.wlf.note.NoteService;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * AI 摘要：把一张图片交给模型，换回一段建议的简介。
 *
 * <p>超时、失败、限频、密钥、费用五件事都在这条链路上现定，不复用登录限频或邮件那两条。
 *
 * <p>只读、无状态、不落任何东西。不存在「生成过但没保存」这类需要过期与清理的中间态。
 * <p>两条链路的差别只是文件从哪来
 */
@Slf4j
@Service
public class AiSummaryService {

    /**
     * 摘要长度上限，与 {@code note.summary} 的列宽一致（§5.4）。
     *
     * <p>它同时出现在两处：prompt 里要求模型的字数，以及回程的截断。两处都要有——
     * 见 {@link #truncate}。
     */
    private static final int MAX_SUMMARY_LENGTH = 512;

    /** 上下文里标题的长度上限，照抄 {@code note.title} 的列宽 */
    private static final int MAX_TITLE_LENGTH = 128;

    /** 上下文里教师的长度上限，照抄 {@code note.teacher} 的列宽 */
    private static final int MAX_TEACHER_LENGTH = 64;

    /** 用户没填时给模型的占位文案，而不是留空——空串会让模型以为「标题是空的」并自行编一个 */
    private static final String BLANK_CONTEXT = "（用户未填写）";

    /**
     * 系统指令。
     *
     * <p>指令放在 system 段、用户可控的标题与教师放在 user 段
     */
    private static final String SYSTEM_PROMPT = """
            你是校园笔记托管站的摘要助手。用户会给你一张笔记图片，并为这份笔记写一段简介。

            要求：
            1. 只写图片里确实有的内容，不要补充图片之外的背景知识，也不要编造；
            2. 直接输出简介正文，不要任何前缀、标题、引号或解释性文字；
            3. 用中文，控制在 %d 个字符以内；
            4. 如果图片不是一份可摘要的学习资料（例如与课程无关的生活照），
               就据实说明它是什么，不要硬凑成笔记简介。
            """;

    private final ChatClient chatClient;
    private final AiSummaryImagePolicy imagePolicy;
    private final AiSummaryRateLimiter rateLimiter;
    private final NoteService noteService;
    private final StorageService storageService;

    public AiSummaryService(ChatClient aiChatClient,
                            AiSummaryImagePolicy imagePolicy,
                            AiSummaryRateLimiter rateLimiter,
                            NoteService noteService,
                            StorageService storageService) {
        this.chatClient = aiChatClient;
        this.imagePolicy = imagePolicy;
        this.rateLimiter = rateLimiter;
        this.noteService = noteService;
        this.storageService = storageService;
    }

    /**
     * 上传场景：图片来自请求体。
     *
     * <p>顺序是「先本地校验、再取配额、最后调用」
     */
    public AiSummaryResponse summarizeUpload(Long userId, AiSummaryRequest request) {
        MultipartFile file = request.getFile();
        String contentType = inspectUpload(file);
        byte[] image = bytesOf(file);

        rateLimiter.requireSlot(userId);
        return new AiSummaryResponse(summarize(contentType, image, request.getTitle(), request.getTeacher()));
    }

    /**
     * 编辑场景：附件早已在 OSS，服务端自取，前端不必重传。
     *
     * <p>{@code title} / {@code teacher} 由前端按<b>表单当前值</b>传来，而不是取库里存的：
     * 用户很可能刚改过标题、还没保存就点了生成，用库里的旧值当上下文是错的。
     */
    public AiSummaryResponse summarizeExistingNote(Long userId, Long noteId, String title, String teacher) {
        // 归属、状态与附件位置一并校验：非本人 40300、已删除 40301、OFFLINE 放行（同 §5.4 编辑）
        NoteFileOwnershipRow file = noteService.requireOwnFile(noteId, userId);

        if (!imagePolicy.supports(file.getContentType())) {
            // 前端本该不给非图片笔记显示按钮（§5.8「前端控可见性、后端控可行性」），
            // 走到这里说明是绕过前端直接调的，据实报 42200 而不是 50000
            throw new BusinessException(ErrorCode.FILE_INVALID, "这篇笔记的附件不是图片，无法生成摘要");
        }

        byte[] image = readObject(file);

        rateLimiter.requireSlot(userId);
        return new AiSummaryResponse(summarize(file.getContentType(), image, title, teacher));
    }

    /**
     * 组装并执行一次模型调用。<b>所有上游异常都在这里收口成 50300。</b>
     *
     * <p><b>不做降级</b>：没有「读不出来就退回用元数据凑一段」这种兜底（那是 V2 文档类摘要的
     * 口径，§6.8）。MVP 的白名单里只有图片，图片要么能读要么报错，没有中间态。
     *
     * <p>日志刻意只记 MIME 与字节数，不记 prompt 正文——那里面是用户上传的图片内容与标题。
     */
    private String summarize(String contentType, byte[] image, String title, String teacher) {
        MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
        try {
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(MAX_SUMMARY_LENGTH))
                    .user(spec -> spec.text(contextText(title, teacher))
                            .media(mimeType, new ByteArrayResource(image)))
                    .call()
                    .content();
            return truncate(raw);
        } catch (RuntimeException e) {
            log.error("调用摘要模型失败，contentType={}，bytes={}", contentType, image.length, e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /**
     * 截断到 {@code note.summary} 的列宽。
     *
     * <p>按 <b>code point</b> 而不是 {@code String#length} 截：摘要里可能有 emoji，
     * 按 UTF-16 单元截会把一个代理对劈成两半，留下孤立的高位代理，前端渲染出来是乱码。
     */
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

    /** 用户可控的上下文，拼进 user 段 */
    private static String contextText(String title, String teacher) {
        return """
                这份笔记的用户已填信息如下（可能不全）：
                标题：%s
                教师：%s

                请阅读随附的图片，给出简介。
                """.formatted(sanitize(title, MAX_TITLE_LENGTH), sanitize(teacher, MAX_TEACHER_LENGTH));
    }

    /**
     * 清洗一段用户可控的文本。
     *
     * <p><b>压掉换行与控制字符再截断。</b>换行是这类输入里最容易被用来「伪造出下一行指令」的东西，
     * 折成单行之后它就只是一段普通文字。长度上限与 {@code @Size} 重复了一次，是有意的：
     * 编辑场景那两个参数走的是 {@code @RequestParam}，没有 Bean Validation 兜着。
     *
     * <p>这不是完备的提示注入防御，也不打算是——真正的隔离靠「指令在 system 段、用户输入在
     * user 段」这层结构。而且产物只是一段建议文本，用户会看到它、编辑它，落库前必须经他确认
     * （§5.8），所以「让模型跑偏」的最坏结果只是浪费一次调用。
     */
    private static String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return BLANK_CONTEXT;
        }
        String flattened = value.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return flattened.length() > maxLength ? flattened.substring(0, maxLength) : flattened;
    }

    /** 上传场景的准入：读文件头之前先过策略，读完即关流 */
    private String inspectUpload(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return imagePolicy.inspect(file.getOriginalFilename(), file.getSize(), content);
        } catch (IOException e) {
            log.error("读取待摘要图片失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取图片失败");
        }
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("读取待摘要图片失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取图片失败");
        }
    }

    /**
     * 编辑场景：从 OSS 读回对象字节。
     *
     * <p><b>大小判断放在 {@link StorageService#open} 之前</b>，用库里那一列 {@code size}——
     * 存储层只有「存 / 读 / 签 / 删」四件原语、没有 stat，读回来再判大小就等于把一个
     * 可能上百 MB 的对象先整个拉进堆里。这正是不用 multipart 的场景仍然要提前拦住的那一步。
     *
     * <p>复用 {@code StorageService#open} 而不新增存储层方法：它原先只服务 §6.2 的
     * {@code /raw} 文本代理，而 AI 与它要的是同一个东西——后端拿到对象字节（§6.8）。
     */
    private byte[] readObject(NoteFileOwnershipRow file) {
        if (file.getSize() != null && file.getSize() > AiSummaryImagePolicy.MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "图片超过 " + (AiSummaryImagePolicy.MAX_SIZE_BYTES >> 20) + "MB，无法生成摘要");
        }
        try (InputStream content = storageService.open(Bucket.NOTE, file.getStorageKey())) {
            return content.readAllBytes();
        } catch (IOException e) {
            log.error("读取笔记附件失败，key={}", file.getStorageKey(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取笔记文件失败");
        }
    }
}
