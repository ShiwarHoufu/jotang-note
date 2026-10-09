package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.catalog.CollegeMapper;
import com.wlf.note.dto.NoteCreatedResponse;
import com.wlf.note.dto.NoteUploadRequest;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证上传链路的落库结果与失败时的补偿行为。见《概要设计》§6.1。
 *
 * <p><b>为何把 {@link StorageService} 换成 mock</b>：§1.1 把存储抽象成只含「存 / 读 / 签 / 删」
 * 四件原语的接口，正是为了让 note 的测试不必触碰 OSS SDK。真打 OSS 的验证在
 * {@code OssStorageServiceTest}（{@code @Tag("oss")}，默认排除），两者分工明确：
 * 那边验「对象存得对不对」，这边验「对象与库行怎么对应、失败时怎么收场」。
 *
 * <p>用例随事务回滚；上传者与课程都自造，不依赖开发库里已有什么。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteServiceTest {

    private static final String MARKER = "ZZTEST-";

    /** 文件头是 {@code %PDF-1.7}，够过魔数校验 */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};

    /** Windows 可执行文件的 MZ 头，用来冒充 PDF */
    private static final byte[] EXE = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00};

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    private Long courseId;
    private Long uploaderId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        uploaderId = insertUploader();

        // 回显入参的 key / contentType / size：让「落库的」就是「storage 真正收到并使用的」，
        // 若被测代码把某个字段传错位置（比如 key 与 contentType 调换），这里会立刻暴露
        when(storageService.upload(eq(Bucket.NOTE), anyString(), any(), anyLong(), anyString(), isNull()))
                .thenAnswer(invocation -> new StoredObject(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(4, String.class),
                        invocation.getArgument(3, Long.class)));
    }

    @Test
    void uploadPersistsNoteFileAndTags() {
        NoteCreatedResponse created = noteService.upload(uploaderId,
                request("  期中复习提纲  ", "  覆盖前三章  ", "  张老师  ",
                        List.of(MARKER + "期中", MARKER + "高数")));

        Note note = noteMapper.selectById(created.id());
        assertThat(note.getUploaderId()).isEqualTo(uploaderId);
        assertThat(note.getCourseId()).isEqualTo(courseId);
        assertThat(note.getTitle()).isEqualTo("期中复习提纲");
        assertThat(note.getSummary()).isEqualTo("覆盖前三章");
        assertThat(note.getTeacher()).isEqualTo("张老师");
        // 提交后立即上架（§4.1）
        assertThat(note.getStatus()).isEqualTo(NoteStatus.ONLINE.name());
        assertThat(note.getDeletedAt()).isNull();
        // 三个计数列由建表语句的 DEFAULT 0 兜底，Java 侧刻意不赋值
        assertThat(note.getViewCount()).isZero();
        assertThat(note.getDownloadCount()).isZero();
        assertThat(note.getFavoriteCount()).isZero();

        NoteFile file = noteFileMapper.selectOne(
                Wrappers.<NoteFile>lambdaQuery().eq(NoteFile::getNoteId, created.id()));
        assertThat(file.getOriginalName()).isEqualTo("笔记.pdf");
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        assertThat(file.getSize()).isEqualTo(PDF.length);
        assertThat(file.getIsPurged()).isZero();

        assertThat(tagNamesOf(created.id()))
                .containsExactlyInAnyOrder(MARKER + "期中", MARKER + "高数");
    }

    @Test
    void objectIsStoredUnderGeneratedKeyAndThatKeyIsPersisted() {
        NoteCreatedResponse created = noteService.upload(uploaderId, request("键", null, null, List.of()));

        NoteFile file = noteFileMapper.selectOne(
                Wrappers.<NoteFile>lambdaQuery().eq(NoteFile::getNoteId, created.id()));
        // 对象键是重命名后的 notes/yyyy/MM/{uuid}.{ext}，不含原始文件名（§6.1）
        assertThat(file.getStorageKey()).matches("notes/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");

        verify(storageService).upload(eq(Bucket.NOTE), eq(file.getStorageKey()), any(),
                eq((long) PDF.length), eq("application/pdf"), isNull());
    }

    /** 标签不存在时由上传流程当场创建，而不是拒绝请求（需求 §2「标签」决策） */
    @Test
    void unknownTagIsCreatedOnTheFly() {
        // 名字要短于 tag.name 的 32 字符上限，故只取 UUID 前 8 位做唯一后缀
        String tagName = MARKER + "新" + UUID.randomUUID().toString().substring(0, 8);

        NoteCreatedResponse created = noteService.upload(uploaderId, request("打标", null, null, List.of(tagName)));

        assertThat(tagNamesOf(created.id())).containsExactly(tagName);
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        NoteCreatedResponse created = noteService.upload(uploaderId, request("只有标题", "   ", null, null));

        Note note = noteMapper.selectById(created.id());
        assertThat(note.getSummary()).isNull();
        assertThat(note.getTeacher()).isNull();
    }

    /** 参数错要在碰 OSS 之前拦下，否则只能靠补偿删除收场 */
    @Test
    void unknownCourseIsRejectedBeforeTouchingStorage() {
        NoteUploadRequest request = request("标题", null, null, List.of());
        request.setCourseId(999_999_999L);

        assertThatThrownBy(() -> noteService.upload(uploaderId, request))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(e.getData())
                            .isEqualTo(List.of(new FieldViolation("courseId", "课程不存在")));
                });

        verify(storageService, never()).upload(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    @Test
    void unacceptableFileIsRejectedBeforeTouchingStorage() {
        NoteUploadRequest request = request("标题", null, null, List.of());
        request.setFile(new MockMultipartFile("file", "伪装.pdf", "application/pdf", EXE));

        assertThatThrownBy(() -> noteService.upload(uploaderId, request))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verify(storageService, never()).upload(any(), anyString(), any(), anyLong(), anyString(), any());
    }

    /**
     * 入库失败必须把刚传上去的对象删掉，否则它永远无人引用。
     *
     * <p>这里用一个必然失败的方式逼出异常：让 storage 两次返回<b>同一个</b> key，
     * 第二次就会撞上 {@code note_file} 的 {@code uk_storage_key}。
     * 不用「标题超长」之类的写法，是为了不依赖 MySQL 是否开启严格模式——
     * 严格模式关掉时超长字段会被静默截断，用例就悄悄失效了。
     */
    @Test
    void objectIsPurgedWhenInsertFails() {
        String key = "notes/2026/10/" + UUID.randomUUID() + ".pdf";
        when(storageService.upload(eq(Bucket.NOTE), anyString(), any(), anyLong(), anyString(), isNull()))
                .thenReturn(new StoredObject(key, "application/pdf", PDF.length));

        noteService.upload(uploaderId, request("第一篇", null, null, List.of()));

        assertThatThrownBy(() -> noteService.upload(uploaderId, request("第二篇", null, null, List.of())))
                .isInstanceOf(DuplicateKeyException.class);

        verify(storageService).delete(Bucket.NOTE, key);
    }

    private NoteUploadRequest request(String title, String summary, String teacher, List<String> tags) {
        NoteUploadRequest request = new NoteUploadRequest();
        request.setTitle(title);
        request.setSummary(summary);
        request.setTeacher(teacher);
        request.setCourseId(courseId);
        request.setTags(tags);
        request.setFile(new MockMultipartFile("file", "笔记.pdf", "application/pdf", PDF));
        return request;
    }

    private List<String> tagNamesOf(Long noteId) {
        return jdbcTemplate.queryForList(
                "SELECT t.name FROM note_tag nt JOIN tag t ON t.id = nt.tag_id WHERE nt.note_id = ?",
                String.class, noteId);
    }

    /** note.uploader_id 有外键指向 user，上传者必须是真行；user.college_id 又非空，故先造一个学院 */
    private Long insertUploader() {
        College college = new College();
        college.setName(MARKER + "学院");
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + "uploader");
        user.setEmail(MARKER + "uploader@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "上传者");
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
