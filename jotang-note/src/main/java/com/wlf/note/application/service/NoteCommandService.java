package com.wlf.note.application.service;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.note.application.command.ChangeNoteStatusCommand;
import com.wlf.note.application.command.DeleteNoteCommand;
import com.wlf.note.application.command.UpdateNoteCommand;
import com.wlf.note.application.command.UploadNoteCommand;
import com.wlf.note.application.command.UploadedFile;
import com.wlf.note.application.result.NoteCreatedResult;
import com.wlf.note.domain.note.FileDecision;
import com.wlf.note.domain.note.Note;
import com.wlf.note.domain.note.NoteDetailCache;
import com.wlf.note.domain.note.NoteFile;
import com.wlf.note.domain.note.NoteFilePolicy;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.CourseId;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.NoteSummary;
import com.wlf.note.domain.note.model.NoteTitle;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import com.wlf.note.domain.note.model.TeacherName;
import com.wlf.storage.Bucket;
import com.wlf.storage.OrphanObjectPublisher;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/** 笔记写侧用例。 */
@Slf4j
@Service
public class NoteCommandService {

    private final NoteRepository noteRepository;
    private final NoteDetailCache noteDetailCache;
    private final CourseService courseService;
    private final TagService tagService;
    private final NoteFilePolicy filePolicy;
    private final StorageService storageService;
    private final OrphanObjectPublisher orphanObjectPublisher;
    private final TransactionTemplate transactionTemplate;

    public NoteCommandService(NoteRepository noteRepository,
                              NoteDetailCache noteDetailCache,
                              CourseService courseService,
                              TagService tagService,
                              NoteFilePolicy filePolicy,
                              StorageService storageService,
                              OrphanObjectPublisher orphanObjectPublisher,
                              PlatformTransactionManager transactionManager) {
        this.noteRepository = noteRepository;
        this.noteDetailCache = noteDetailCache;
        this.courseService = courseService;
        this.tagService = tagService;
        this.filePolicy = filePolicy;
        this.storageService = storageService;
        this.orphanObjectPublisher = orphanObjectPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 上传：校验 → 解析标签 → 传 OSS → 事务落库；失败补偿对象。 */
    public NoteCreatedResult upload(UploadNoteCommand command) {
        requireCourse(command.courseId());
        List<Long> tagIds = tagService.resolveIds(command.tags());

        FileDecision decision = inspect(command.file(), command.uploaderId());
        StoredObject stored = store(command.file(), decision, command.uploaderId());

        try {
            NoteCreatedResult created = transactionTemplate.execute(
                    status -> insertNote(command, decision, stored, tagIds));
            evictMeta(created.id());
            return created;
        } catch (RuntimeException e) {
            purgeQuietly(stored.key());
            throw e;
        }
    }

    public void update(UpdateNoteCommand command) {
        transactionTemplate.executeWithoutResult(status -> applyUpdate(command));
        evictMeta(command.noteId());
    }

    public void delete(DeleteNoteCommand command) {
        transactionTemplate.executeWithoutResult(status -> {
            Note note = noteRepository.findByIdForUpdate(command.noteId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            NoteStatus before = note.status();
            note.delete(command.uploaderId());
            if (note.status() != before) {
                noteRepository.saveStatus(note);
            }
        });
        evictMeta(command.noteId());
    }

    /** 管理员下架 / 恢复。 */
    public void changeStatus(ChangeNoteStatusCommand command) {
        transactionTemplate.executeWithoutResult(status -> {
            Note note = noteRepository.findByIdForUpdate(command.noteId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            NoteStatus before = note.status();
            if (command.target() == NoteStatus.OFFLINE) {
                note.offline();
            } else {
                note.restore();
            }
            if (note.status() != before) {
                noteRepository.saveStatus(note);
            }
        });
        evictMeta(command.noteId());
    }

    private void applyUpdate(UpdateNoteCommand command) {
        Note note = noteRepository.findByIdForUpdate(command.noteId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        note.ensureUploader(command.editorId());
        note.ensureEditable();

        requireCourse(command.courseId());
        List<Long> tagIds = tagService.resolveIds(command.tags());

        note.changeMetadata(command.editorId(),
                new NoteTitle(command.title()),
                new NoteSummary(command.summary()),
                new TeacherName(command.teacher()),
                new CourseId(command.courseId()),
                tagIds);
        noteRepository.saveMetadata(note);
        noteRepository.replaceTags(command.noteId(), note.tagIds());
    }

    private NoteCreatedResult insertNote(UploadNoteCommand command,
                                         FileDecision decision,
                                         StoredObject stored,
                                         List<Long> tagIds) {
        NoteFile noteFile = NoteFile.attach(
                new StorageKey(stored.key()),
                new OriginalFileName(decision.originalName()),
                new FileSize(stored.size()),
                new ContentType(stored.contentType()));
        Note note = Note.upload(command.uploaderId(),
                new CourseId(command.courseId()),
                new NoteTitle(command.title()),
                new NoteSummary(command.summary()),
                new TeacherName(command.teacher()),
                noteFile,
                Set.copyOf(tagIds));
        noteRepository.insert(note);
        return new NoteCreatedResult(note.id());
    }

    private FileDecision inspect(UploadedFile file, Long uploaderId) {
        try (InputStream in = file.open()) {
            return filePolicy.inspect(file.originalFilename(), file.size(), in);
        } catch (IOException e) {
            log.error("读取上传文件失败 uploaderId={} name={}", uploaderId, file.originalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private StoredObject store(UploadedFile file, FileDecision decision, Long uploaderId) {
        String key = filePolicy.objectKey(decision.extension());
        try (InputStream in = file.open()) {
            return storageService.upload(Bucket.NOTE, key, in, file.size(), decision.contentType(), null);
        } catch (IOException e) {
            log.error("读取上传文件失败 uploaderId={} name={}", uploaderId, file.originalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private void requireCourse(Long courseId) {
        if (!courseService.exists(courseId)) {
            String message = "课程不存在";
            throw new BusinessException(ErrorCode.PARAM_INVALID, message,
                    List.of(new FieldViolation("courseId", message)));
        }
    }

    private void evictMeta(Long noteId) {
        try {
            noteDetailCache.evict(noteId);
        } catch (RuntimeException e) {
            log.warn("失效笔记详情缓存失败，最坏情况靠 TTL 兜底 noteId={}", noteId, e);
        }
    }

    private void purgeQuietly(String key) {
        try {
            orphanObjectPublisher.publish(Bucket.NOTE, key);
        } catch (RuntimeException e) {
            log.error("补偿删除上报失败，退化为仅内联删除 key={}", key, e);
        }
        try {
            storageService.delete(Bucket.NOTE, key);
        } catch (RuntimeException e) {
            log.error("补偿删除对象失败，可能残留孤儿对象 key={}", key, e);
        }
    }
}
