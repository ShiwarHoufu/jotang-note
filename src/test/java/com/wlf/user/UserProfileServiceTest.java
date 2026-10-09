package com.wlf.user;

import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.entity.College;
import com.wlf.entity.Course;
import com.wlf.entity.Note;
import com.wlf.entity.NoteFile;
import com.wlf.entity.User;
import com.wlf.note.NoteFileMapper;
import com.wlf.note.NoteMapper;
import com.wlf.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserService#updateProfile} 的用例。见《概要设计》§5.2、§3.3、决策 D8。
 *
 * <p>重点在几条<b>写错了不会当场暴露</b>的规则：
 *
 * <ul>
 *   <li><b>只许写 {@code nickname} 与 {@code college_id} 两列</b>。这是本接口最容易踩的坑：
 *       {@code selectById} 捞回来的实体每一列都非空，顺手把它交给 {@code updateById}
 *       就会生成一份全列 SET——覆盖并发改动、平白重写密码哈希，而且因为 {@code updated_at}
 *       被显式赋值，MySQL 的 {@code ON UPDATE} 会被抑制。注意抓它的是
 *       {@code updatedAtAdvancesWhenAValueChanges} 而**不是**那条列清单用例，
 *       原因两条用例的注释里都写了（已实测）</li>
 *   <li><b>{@code updated_at} 不手写</b>，交给 {@code ON UPDATE}；值没变时不推进</li>
 *   <li><b>改学院不级联写任何东西</b>，历史笔记的归属靠 JOIN 推导自然改变（D8）</li>
 *   <li><b>被拒的请求不能留下痕迹</b>——学院不存在时一个字都不该落库</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserProfileServiceTest {

    private static final String MARKER = "ZZTEST-PROF-SVC-";

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteFileMapper noteFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long courseId;

    @BeforeEach
    void setUp() {
        courseId = courseMapper.selectList(null).get(0).getId();
    }

    // ==================== 正常路径 ====================

    /** 两列都改写，返回的 {@code UserInfo} 与库里的行一致 */
    @Test
    void updatingProfileWritesNicknameAndCollege() {
        College oldCollege = insertCollege("原学院");
        College newCollege = insertCollege("新学院");
        Long userId = insertUser(oldCollege.getId(), "原昵称");

        UserInfo updated = userService.updateProfile(userId, request("新昵称", newCollege.getId()));

        assertThat(updated.nickname()).isEqualTo("新昵称");
        assertThat(updated.collegeId()).isEqualTo(newCollege.getId());
        assertThat(updated.id()).isEqualTo(userId);

        User row = userMapper.selectById(userId);
        assertThat(row.getNickname()).isEqualTo("新昵称");
        assertThat(row.getCollegeId()).isEqualTo(newCollege.getId());
    }

    /**
     * 改资料只动两列，其余一律不写。每一列都有一个「顺手改一下」的看似合理理由，但都会出事：
     * {@code avatar} / {@code role} / {@code status} 会覆盖并发改动（头像切片上线后尤其危险），
     * {@code password_hash} 被平白重写，{@code username} / {@code email} 是登录与找回凭证，
     * {@code created_at} 是注册时刻。
     *
     * <p><b>但它抓不到「把整个实体交给 update」那个坑，别指望它。</b>那种写法是把读到的值原样
     * 写回，所以本用例的每条断言都仍然成立——已实测确认（换成 {@code updateById(user)} 之后
     * 这条照过）。真正能抓住它的是下面 {@link #updatedAtAdvancesWhenAValueChanges}，
     * 原因见那条的注释。本用例守的是另一种回归：将来有人往 SET 列表里加了一列**带着不同的值**
     * （比如顺手 {@code user.setStatus(1)}）。
     */
    @Test
    void updateTouchesOnlyNicknameAndCollege() {
        College oldCollege = insertCollege("原学院");
        College newCollege = insertCollege("新学院");
        Long userId = insertUser(oldCollege.getId(), "原昵称");

        String avatarKey = "avatars/" + userId + "/" + UUID.randomUUID() + ".png";
        LocalDateTime registeredAt = LocalDateTime.of(2021, 3, 4, 5, 6, 7);
        jdbcTemplate.update("""
                        UPDATE `user` SET avatar = ?, role = ?, status = ?, created_at = ? WHERE id = ?
                        """,
                avatarKey, "ADMIN", 0, registeredAt, userId);

        User before = userMapper.selectById(userId);

        userService.updateProfile(userId, request("新昵称", newCollege.getId()));

        User after = userMapper.selectById(userId);
        assertThat(after.getNickname()).isEqualTo("新昵称");
        assertThat(after.getCollegeId()).isEqualTo(newCollege.getId());

        assertThat(after.getAvatar()).isEqualTo(avatarKey);
        assertThat(after.getRole()).isEqualTo("ADMIN");
        assertThat(after.getStatus()).isZero();
        assertThat(after.getUsername()).isEqualTo(before.getUsername());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getCreatedAt()).isEqualTo(registeredAt);
    }

    /**
     * {@code updated_at} 由 MySQL 的 {@code ON UPDATE} 推进，不由本接口手写。
     *
     * <p>先把 {@code updated_at} 设成一个过去的时间，改资料之后再断言它前进了——直接比较
     * 编辑前后在秒精度下同秒内发现不了问题（与
     * {@code NoteUpdateServiceTest#editingRewritesMetadataAndBumpsUpdatedAt} 同一手法）。
     *
     * <p><b>这条同时是本类唯一能抓住「把整个实体交给 update」那个坑的用例</b>，这不是巧合。
     * 那种写法的危害看起来是「多写了几列」，但多写的都是读回来的同一个值，行内容分毫不差，
     * 所以任何断言列值的用例都发现不了它。真正暴露它的是 {@code updated_at}：它是
     * {@code user} 表上唯一带 {@code ON UPDATE} 的列，一旦被显式写进 SET 列表，
     * MySQL 的自动推进就被抑制——于是改了资料时间戳却不动。已实测：把实现换成
     * {@code updateById(user)} 后，本类 8 条里只有这一条红。
     */
    @Test
    void updatedAtAdvancesWhenAValueChanges() {
        College college = insertCollege("学院");
        Long userId = insertUser(college.getId(), "原昵称");
        LocalDateTime stale = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE `user` SET updated_at = ? WHERE id = ?", stale, userId);

        userService.updateProfile(userId, request("新昵称", college.getId()));

        assertThat(userMapper.selectById(userId).getUpdatedAt()).isAfter(stale);
    }

    /**
     * 提交的资料与现状一字不差时，{@code updated_at} <b>不</b>推进。
     *
     * <p>这不是实现漂移，而是 {@code ON UPDATE} 的正确语义落到本场景的结果：一列都没变时
     * MySQL 不认为这行被修改过。而「提交了和现状一样的资料」本来就不算一次真实修改，
     * 所以这个性质正好合意。反过来说，如果哪天有人把 {@code updated_at} 手写进 SET 列表，
     * 这条会红——它同时也是「从未手写它」的反证。
     */
    @Test
    void updatedAtDoesNotAdvanceWhenNothingChanges() {
        College college = insertCollege("学院");
        Long userId = insertUser(college.getId(), "原昵称");
        LocalDateTime stale = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE `user` SET updated_at = ? WHERE id = ?", stale, userId);

        userService.updateProfile(userId, request("原昵称", college.getId()));

        assertThat(userMapper.selectById(userId).getUpdatedAt()).isEqualTo(stale);
    }

    /** 昵称前后空格被去掉，与注册时对用户名 / 邮箱的处理一致 */
    @Test
    void nicknameIsTrimmed() {
        College college = insertCollege("学院");
        Long userId = insertUser(college.getId(), "原昵称");

        UserInfo updated = userService.updateProfile(userId, request("  小明  ", college.getId()));

        assertThat(updated.nickname()).isEqualTo("小明");
        assertThat(userMapper.selectById(userId).getNickname()).isEqualTo("小明");
    }

    // ==================== 拒绝路径 ====================

    /** 学院不存在：40001 + 字段明细，且一个字段都不许落库 */
    @Test
    void unknownCollegeLeavesTheRowUntouched() {
        College college = insertCollege("学院");
        Long userId = insertUser(college.getId(), "原昵称");

        assertThatThrownBy(() -> userService.updateProfile(userId, request("新昵称", 999999999L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getData()).isEqualTo(List.of(new FieldViolation("collegeId", "学院不存在")));
                });

        User row = userMapper.selectById(userId);
        assertThat(row.getNickname()).isEqualTo("原昵称");
        assertThat(row.getCollegeId()).isEqualTo(college.getId());
    }

    /** 用户不存在：40400，与 {@code AuthService#currentUser} 同口径 */
    @Test
    void missingUserThrowsNotFound() {
        College college = insertCollege("学院");

        assertThatThrownBy(() -> userService.updateProfile(999999999L, request("新昵称", college.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ==================== D8 的回溯语义 ====================

    /**
     * <b>改学院会回溯改变该用户全部历史笔记的归属</b>（D8、§3.3），而且不需要任何级联写入。
     *
     * <p>走 {@code NoteMapper#selectDetail} 而不是 {@code NoteService#detail}：前者正是产出
     * {@code collegeName} 的那条 SQL，直接证明「笔记的学院由 JOIN 上传者推导」；后者会经过
     * Redis 详情缓存（{@code note:meta:{noteId}}），反而把一个要等 TTL 的现象混进用例里。
     */
    @Test
    void changingCollegeRewritesHistoricalNotesCollege() {
        College oldCollege = insertCollege("原学院");
        College newCollege = insertCollege("新学院");
        Long userId = insertUser(oldCollege.getId(), "昵称");
        Long noteId = createNote(userId);

        assertThat(noteMapper.selectDetail(noteId).getCollegeName()).isEqualTo(oldCollege.getName());

        userService.updateProfile(userId, request("昵称", newCollege.getId()));

        // 这一行 note 自始至终没被碰过，学院却变了——归属是推导出来的
        assertThat(noteMapper.selectDetail(noteId).getCollegeName()).isEqualTo(newCollege.getName());
    }

    // ==================== 辅助 ====================

    private static UpdateProfileRequest request(String nickname, Long collegeId) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname(nickname);
        request.setCollegeId(collegeId);
        return request;
    }

    private College insertCollege(String label) {
        College college = new College();
        college.setName(MARKER + label + "-" + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college;
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(Long collegeId, String nickname) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(nickname);
        user.setCollegeId(collegeId);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 直接插库造一篇笔记，不走上传流程：本用例只关心 {@code selectDetail} 那条 JOIN
     * 能不能把学院推导出来，把 {@code NoteFilePolicy} 与 OSS 拖进来没有意义。
     * {@code note_file} 必须有一行——{@code selectDetail} 全是 INNER JOIN。
     */
    private Long createNote(Long uploaderId) {
        Note note = new Note();
        note.setUploaderId(uploaderId);
        note.setCourseId(courseId);
        note.setTitle(MARKER + "标题");
        note.setStatus("ONLINE");
        noteMapper.insert(note);

        NoteFile file = new NoteFile();
        file.setNoteId(note.getId());
        file.setStorageKey("notes/2026/10/" + UUID.randomUUID() + ".pdf");
        file.setOriginalName("课件.pdf");
        file.setSize(1048576L);
        file.setContentType("application/pdf");
        noteFileMapper.insert(file);

        return note.getId();
    }
}
