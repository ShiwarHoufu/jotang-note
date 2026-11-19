package com.wlf.ai.summary;

import com.wlf.ai.infrastructure.llm.LlmClient;
import com.wlf.ai.infrastructure.note.NoteFile;
import com.wlf.ai.infrastructure.note.NoteGateway;
import com.wlf.ai.infrastructure.quota.AiQuota;
import com.wlf.ai.infrastructure.quota.FixedWindowRateLimiter;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AI 摘要：上传 / 编辑两条链路。
 *
 * <p>摘要是纯读、不落任何库表；生成结果交给前端，由用户决定是否保存。
 */
@Slf4j
@Service
public class SummaryService {

    private final LlmClient llmClient;
    private final FixedWindowRateLimiter rateLimiter;
    private final NoteGateway noteGateway;

    public SummaryService(LlmClient llmClient,
                          FixedWindowRateLimiter rateLimiter,
                          NoteGateway noteGateway) {
        this.llmClient = llmClient;
        this.rateLimiter = rateLimiter;
        this.noteGateway = noteGateway;
    }

    /** 上传场景：文件来自请求体。 */
    public String summarizeUpload(Long userId, MultipartFile file, String title, String teacher) {
        Input input = readUpload(file);
        rateLimiter.acquire(userId, AiQuota.SUMMARY);
        return generate(input, title, teacher);
    }

    /**
     * 编辑场景：附件已在 OSS，服务端自取。
     *
     * <p>{@code title} / {@code teacher} 由前端按表单当前值传来，而不是取库里存的——
     * 用户很可能刚改过标题、还没保存就点了生成。
     */
    public String summarizeNote(Long userId, Long noteId, String title, String teacher) {
        NoteFile noteFile = noteGateway.requireOwned(noteId, userId);

        String contentType = noteFile.contentType();
        SummaryFileValidator.requireSummarizable(contentType);
        Input input = readNoteFile(noteFile, contentType, noteId);

        rateLimiter.acquire(userId, AiQuota.SUMMARY);
        return generate(input, title, teacher);
    }

    /** 上传场景：先读文件头做准入，再读全文构造输入。 */
    private Input readUpload(MultipartFile file) {
        String contentType;
        try (InputStream content = file.getInputStream()) {
            contentType = SummaryFileValidator.inspect(file.getOriginalFilename(), file.getSize(),
                    content.readNBytes(SummaryFileValidator.HEAD_BYTES));
            return toInput(contentType, file.getBytes());
        } catch (IOException e) {
            log.error("读取待摘要文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
    }

    /** 读回附件字节。大小判断放在打开对象之前。 */
    private Input readNoteFile(NoteFile file, String contentType, Long noteId) {
        SummaryFileValidator.requireWithinLimit(contentType, file.size());
        byte[] bytes;
        try (InputStream content = file.open()) {
            bytes = SummaryFileValidator.isText(contentType)
                    ? content.readNBytes((int) SummaryFileValidator.MAX_TEXT_BYTES + 1)
                    : content.readAllBytes();
        } catch (IOException e) {
            log.error("读取笔记附件失败，noteId={}", noteId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取笔记文件失败");
        }
        // 库里那一列可能与对象实际大小不符；同一道闸按真实字节再算一次
        SummaryFileValidator.requireWithinLimit(contentType, (long) bytes.length);
        return toInput(contentType, bytes);
    }

    private static Input toInput(String contentType, byte[] bytes) {
        if (!SummaryFileValidator.isText(contentType)) {
            return new Input(contentType, bytes, null);
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        SummaryFileValidator.requireTextWithinLimit(body);
        return new Input(contentType, bytes, body);
    }

    private String generate(Input input, String title, String teacher) {
        try {
            String raw = input.isText()
                    ? llmClient.chat(SummaryPrompt.textSystem(),
                            SummaryPrompt.textUser(title, teacher, input.body()))
                    : llmClient.chatWithImage(SummaryPrompt.imageSystem(),
                            SummaryPrompt.imageUser(title, teacher), input.contentType(), input.bytes());
            return SummaryTextRules.truncateSummary(raw);
        } catch (RuntimeException e) {
            log.error("调用摘要模型失败，contentType={}", input.contentType(), e);
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /** 已经过准入的摘要输入。 */
    private record Input(String contentType, byte[] bytes, String body) {

        boolean isText() {
            return body != null;
        }
    }
}
