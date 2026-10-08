package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Favorite;
import com.wlf.entity.Note;
import com.wlf.entity.User;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteStatus;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FavoriteService#isFavorited} 的用例，只有一件事要验透：
 * <b>它回答的是「收藏关系在不在」，而不是「现在还能不能收藏」</b>。
 *
 * <p>这条区别的来源是 §6.5 那句「占位项不可预览/下载，但<b>不解除收藏关系</b>」。
 * 实现上很容易「顺手」加一个 {@code note.status == ONLINE} 的条件——那样更符合直觉，
 * 却会让笔记下架时收藏态凭空消失、恢复上架时又凭空回来。所以 OFFLINE / DELETED
 * 两种状态各有一条用例钉住。
 *
 * <p>与 {@code NoteServiceTest} 同样起真库：这里验的是「等值查询有没有命中
 * {@code uk_user_note} 并返回正确的布尔值」，换成 mock 的 Mapper 就只剩「调没调这个方法」，
 * 而谓词写错（比如拿 {@code noteId} 去比 {@code user_id}）恰恰是要防的东西。
 * 用例随事务回滚，数据自造，不依赖开发库里已有什么。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class FavoriteServiceTest {

    private static final String MARKER = "ZZTEST-";

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long courseId;
    private Long readerId;
    private Long noteId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(Wrappers.<Course>lambdaQuery().last("LIMIT 1")).get(0).getId();
        readerId = insertUser("reader");
        noteId = insertNote(insertUser("uploader"), NoteStatus.ONLINE);
    }

    @Test
    void favoritedNoteIsReported() {
        favorite(readerId, noteId);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
    }

    @Test
    void unfavoritedNoteIsReportedAsFalse() {
        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /** 谓词必须两个条件都匹配：只按 noteId 查会把「别人收藏了」也算成自己收藏了 */
    @Test
    void anotherUsersFavoriteIsNotCounted() {
        favorite(insertUser("someone-else"), noteId);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /**
     * §6.5：下架不解除收藏关系。这里刻意把 status 改掉再问一次，
     * 若实现里混进了 {@code status == ONLINE} 的判断，这条会红。
     */
    @Test
    void offlineNoteStillReportsTheRelationship() {
        favorite(readerId, noteId);
        setNoteStatus(NoteStatus.OFFLINE);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
    }

    /** 软删除同理：行还在，关系就还在，收藏列表靠它渲染「笔记已删除」占位 */
    @Test
    void deletedNoteStillReportsTheRelationship() {
        favorite(readerId, noteId);
        setNoteStatus(NoteStatus.DELETED);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
    }

    /** 不存在的笔记与「存在但没收藏」同义——这里不负责报 40400，那是详情接口的事 */
    @Test
    void unknownNoteIsReportedAsNotFavorited() {
        assertThat(favoriteService.isFavorited(readerId, 999_999_999L)).isFalse();
    }

    @Test
    void nullArgumentsAreTreatedAsNotFavorited() {
        assertThat(favoriteService.isFavorited(null, noteId)).isFalse();
        assertThat(favoriteService.isFavorited(readerId, null)).isFalse();
        assertThat(favoriteService.isFavorited(null, null)).isFalse();
    }

    private void favorite(Long userId, Long targetNoteId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setNoteId(targetNoteId);
        favoriteMapper.insert(favorite);
    }

    /** 直接改库而不是走状态机：本用例要的是「表里已经是那个状态」，与迁移是否合法无关 */
    private void setNoteStatus(NoteStatus status) {
        jdbcTemplate.update("UPDATE note SET status = ? WHERE id = ?", status.name(), noteId);
    }

    /**
     * 造用户。{@code username} / {@code email} 上有唯一键，同一个测试里可能造多个，
     * 故拼一段随机后缀；{@code user.college_id} 非空，所以每次先造一个学院。
     */
    private Long insertUser(String role) {
        // 后缀取 8 位而不是整串 UUID：user.username 列宽只有 32，
        // MARKER(7) + role(≤12) + '-' + 36 位 UUID 会超长，严格模式下直接 DataIntegrityViolation
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + role + "-" + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + role + "-" + suffix);
        user.setEmail(MARKER + role + "-" + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(MARKER + role);
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }

    private Long insertNote(Long uploaderId, NoteStatus status) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "笔记");
        note.setStatus(status.name());
        noteMapper.insert(note);
        return note.getId();
    }
}
