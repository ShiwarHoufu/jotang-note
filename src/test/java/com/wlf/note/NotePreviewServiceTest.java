package com.wlf.note;

import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.entity.College;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link NoteService#openRaw} 把存储流<b>原样交出</b>，不在服务层读成 byte[]。
 *
 * <p>这条为什么单开一个类：{@code NoteControllerTest} 的 {@code /raw} 用例只断言响应体字节对，
 * 而「先把整份读进堆再交给 Spring 写」同样能通过那条断言——可那是错的。文本上限是 100MB
 * （{@code NoteFilePolicy}），{@code AiService} / {@code NoteTools} 里那种 {@code readAllBytes()}
 * 写法在这里会把一个上百 MB 的对象整个拉进内存。只有 {@code isSameAs} 能钉住这条。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NotePreviewServiceTest {

    private static final String MARKER = "ZZTEST-PRV-";

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

    @MockitoBean
    private StorageService storageService;

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
    }

    @Test
    void openRawHandsBackTheStorageStreamUnread() {
        Long noteId = insertNote("text/markdown", 11L);
        InputStream live = new ByteArrayInputStream("正文".getBytes(StandardCharsets.UTF_8));
        when(storageService.open(eq(Bucket.NOTE), anyString())).thenReturn(live);

        NoteRawContent raw = noteService.openRaw(noteId);

        assertThat(raw.content()).isSameAs(live);
        assertThat(raw.size()).isEqualTo(11L);
    }

    // ==================== 夹具 ====================

    private Long insertNote(String contentType, Long size) {
        Note note = new Note();
        note.setUploaderId(insertUser());
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(NoteStatus.ONLINE.name());
        noteMapper.insert(note);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID());
        file.setOriginalName("课件.md");
        file.setSize(size);
        file.setContentType(contentType);
        noteFileMapper.insert(file);

        return note.getId();
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + "上传者");
        user.setCollegeId(insertCollege());
        userMapper.insert(user);
        return user.getId();
    }
}
