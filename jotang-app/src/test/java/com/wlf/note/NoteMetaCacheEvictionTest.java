package com.wlf.note;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.note.dto.NoteUpdateRequest;
import com.wlf.storage.StorageService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 写路径的详情缓存失效：编辑 / 删除 / 下架 / 恢复之后，{@code note:meta:{id}} 必须消失。
 * 见《概要设计》§5.4、§5.6、§4.1。
 *
 * <p>断言不用「key 还在不在」了事，而是<b>读出写完之后第一次详情的返回值</b>——那才是失效
 * 要保的东西，也才在实现换成别的机制时仍然成立。真正有判别力的是两处：
 *
 * <ul>
 *   <li><b>编辑后读到新标题</b>。不失效的话，这里最长 10 分钟还是旧的，而且是编辑者
 *       自己刷新就看得见的那种旧</li>
 *   <li><b>下架 / 删除后他人立刻被挡</b>。缓存里带着 {@code status}，而它是
 *       {@code requireReadable} 的判据，所以这个窗口不只是显示滞后，是越权读</li>
 * </ul>
 *
 * <p><b>本类必须真连 Redis</b>（与 {@code NoteDetailServiceTest} 里那条缓存用例同类）：
 * 读路径对 Redis 故障是降级的，Redis 不在时每个用例都会「空过」——预热没成功，后面
 * 断言的东西也就不存在了。所以 {@link #warmCache} 里先把「预热成功」钉死。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NoteMetaCacheEvictionTest {

    private static final String MARKER = "ZZTEST-EVICT-";

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
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private StorageService storageService;

    private Long courseId;
    private Long ownerId;
    private Long strangerId;
    private Long noteId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        ownerId = insertUser("owner");
        strangerId = insertUser("stranger");
    }

    /**
     * Redis 不在 {@code @Transactional} 的回滚范围内，缓存条目会跨用例残留。
     * 每个用例只灌自己那一篇的 key，按 id 精确清即可。
     */
    @AfterEach
    void cleanUpCache() {
        if (noteId != null) {
            redisTemplate.delete(cacheKey(noteId));
        }
    }

    @Test
    void editingEvictsTheCachedDetail() {
        createNote(NoteStatus.ONLINE);
        warmCache(strangerId);

        noteService.update(noteId, ownerId, request(MARKER + "改过的标题"));

        assertThat(redisTemplate.hasKey(cacheKey(noteId))).isFalse();
        assertThat(noteService.detail(noteId, strangerId, false).title())
                .isEqualTo(MARKER + "改过的标题");
    }

    /**
     * 已删除对谁都不开。失效之前，他人会拿着缓存里那份 {@code ONLINE} 一路读到 200。
     */
    @Test
    void deletingEvictsTheCachedDetail() {
        createNote(NoteStatus.ONLINE);
        warmCache(strangerId);

        noteService.delete(noteId, ownerId);

        assertThat(redisTemplate.hasKey(cacheKey(noteId))).isFalse();
        assertThatThrownBy(() -> noteService.detail(noteId, strangerId, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));
    }

    /** 下架同理：闸门读的是缓存里的 {@code status}，不失效就是一条最长 10 分钟的越权读路径 */
    @Test
    void offliningEvictsTheCachedDetail() {
        createNote(NoteStatus.ONLINE);
        warmCache(strangerId);

        noteService.adminOffline(noteId);

        assertThat(redisTemplate.hasKey(cacheKey(noteId))).isFalse();
        assertThatThrownBy(() -> noteService.detail(noteId, strangerId, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));
    }

    /** 恢复是反方向：不失效的话，他人手里那份 {@code OFFLINE} 会继续吃 40301 */
    @Test
    void restoringEvictsTheCachedDetail() {
        createNote(NoteStatus.OFFLINE);
        // 下架状态只有上传者本人与管理员读得到，预热只能用本人
        warmCache(ownerId);

        noteService.adminRestore(noteId);

        assertThat(redisTemplate.hasKey(cacheKey(noteId))).isFalse();
        assertThat(noteService.detail(noteId, strangerId, false).status()).isEqualTo(NoteStatus.ONLINE);
    }

    // ==================== 夹具 ====================

    /**
     * 先读一次把详情灌进缓存，并<b>当场断言确实灌进去了</b>——少了这一步，Redis 不在时
     * 本类全部用例都会变成空过，而它们看起来照样是绿的。
     */
    private void warmCache(Long viewerId) {
        noteService.detail(noteId, viewerId, false);
        assertThat(redisTemplate.hasKey(cacheKey(noteId)))
                .as("预热失败，缓存里没有这一条，后面的断言就无从谈起")
                .isTrue();
    }

    /** 键格式在这里另写一份：生产端改了格式，本类就该红 */
    private static String cacheKey(Long noteId) {
        return "note:meta:" + noteId;
    }

    private NoteUpdateRequest request(String title) {
        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setTitle(title);
        request.setCourseId(courseId);
        request.setTags(List.of());
        return request;
    }

    /** 直接插库造数据，不走上传流程：本类验的是失效，把 {@code NoteFilePolicy} 与 OSS 拖进来只是负担 */
    private void createNote(NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(ownerId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus(status.name());
        noteMapper.insert(note);
        noteId = note.getId();

        NoteFile file = new NoteFile();
        file.setNoteId(noteId);
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".png");
        file.setOriginalName("课件.png");
        file.setSize(1048576L);
        file.setContentType("image/png");
        noteFileMapper.insert(file);
    }

    private Long insertCollege() {
        College college = new College();
        college.setName(MARKER + "学院-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college.getId();
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + role + "-" + suffix);
        user.setEmail(MARKER + role + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + role);
        user.setCollegeId(insertCollege());
        userMapper.insert(user);
        return user.getId();
    }
}
