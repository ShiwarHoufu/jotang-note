package com.wlf.ai.application.assistant;

import com.wlf.ai.application.assistant.command.SummaryNoteCommand;
import com.wlf.ai.application.assistant.command.SummaryUploadCommand;
import com.wlf.ai.application.assistant.command.UploadedFile;
import com.wlf.ai.application.assistant.result.SummaryResult;
import com.wlf.ai.application.port.out.AiQuota;
import com.wlf.ai.application.port.out.NoteAttachment;
import com.wlf.ai.application.port.out.NoteAttachmentPort;
import com.wlf.ai.application.port.out.RateLimitPort;
import com.wlf.ai.domain.summary.SummaryFilePolicy;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * AI 摘要的应用服务：上传 / 编辑两条链路。
 *
 * <p>摘要是纯读、不落任何库表；生成结果交给前端，由用户决定是否保存。
 */
@Slf4j
@Service
public class AiSummaryService {

    private final SummaryFilePolicy filePolicy;
    private final SummaryGenerator summaryGenerator;
    private final RateLimitPort rateLimit;
    private final NoteAttachmentPort noteAttachmentPort;

    public AiSummaryService(SummaryFilePolicy filePolicy,
                            SummaryGenerator summaryGenerator,
                            RateLimitPort rateLimit,
                            NoteAttachmentPort noteAttachmentPort) {
        this.filePolicy = filePolicy;
        this.summaryGenerator = summaryGenerator;
        this.rateLimit = rateLimit;
        this.noteAttachmentPort = noteAttachmentPort;
    }

    /**
     * 上传场景：文件来自请求体。
     */
    public SummaryResult summarizeUpload(SummaryUploadCommand command) {
        SummaryInput input = admitUpload(command.file());
        rateLimit.acquire(command.userId(), AiQuota.SUMMARY);
        return new SummaryResult(summaryGenerator.generate(input, command.title(), command.teacher()));
    }

    /**
     * 编辑场景：附件已在 OSS，服务端自取。
     *
     * <p>{@code title} / {@code teacher} 由前端按表单当前值传来，而不是取库里存的——
     * 用户很可能刚改过标题、还没保存就点了生成。
     */
    public SummaryResult summarizeExistingNote(SummaryNoteCommand command) {
        // 归属、状态与附件元信息一并校验
        NoteAttachment attachment = noteAttachmentPort.requireOwned(command.noteId(), command.userId());

        // 编辑场景没有字节可验，只能按库里那列 content_type 判。前端本该不给这类笔记显示按钮，
        // 走到这里说明是绕过前端直接调的
        String contentType = attachment.contentType();
        filePolicy.requireSummarizable(contentType);
        SummaryInput input = SummaryInput.of(contentType,
                readObject(contentType, attachment, command.noteId()));

        rateLimit.acquire(command.userId(), AiQuota.SUMMARY);
        return new SummaryResult(summaryGenerator.generate(input, command.title(), command.teacher()));
    }

    /** 上传场景：先读文件头做准入，再读全文构造输入。 */
    private SummaryInput admitUpload(UploadedFile file) {
        String contentType;
        try (InputStream content = file.opener().open()) {
            contentType = filePolicy.inspect(file.filename(), file.size(),
                    content.readNBytes(SummaryFilePolicy.HEAD_BYTES));
        } catch (IOException e) {
            log.error("读取待摘要文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
        return SummaryInput.of(contentType, bytesOf(file));
    }

    private static byte[] bytesOf(UploadedFile file) {
        try (InputStream content = file.opener().open()) {
            return content.readAllBytes();
        } catch (IOException e) {
            log.error("读取待摘要文件失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取文件失败");
        }
    }

    /**
     * 读回附件字节。大小判断放在打开对象之前。
     */
    private byte[] readObject(String contentType, NoteAttachment attachment, Long noteId) {
        filePolicy.requireWithinLimit(contentType, attachment.size());
        try (InputStream content = attachment.open()) {
            byte[] bytes = SummaryFilePolicy.isText(contentType)
                    ? content.readNBytes((int) SummaryFilePolicy.MAX_TEXT_BYTES + 1)
                    : content.readAllBytes();
            // 库里那一列可能与对象实际大小不符；同一道闸按真实字节再算一次
            filePolicy.requireWithinLimit(contentType, (long) bytes.length);
            return bytes;
        } catch (IOException e) {
            log.error("读取笔记附件失败，noteId={}", noteId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取笔记文件失败");
        }
    }
}
