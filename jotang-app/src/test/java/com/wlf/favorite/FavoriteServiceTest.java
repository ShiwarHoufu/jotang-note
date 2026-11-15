package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.catalog.CourseMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FavoriteService} 的用例，分两半。
 *
 * <p><b>读一半（{@link FavoriteService#isFavorited}）</b>只有一件事要验透：
 * <b>它回答的是「收藏关系在不在」，而不是「现在还能不能收藏」</b>。
 * 这条区别的来源是 §6.5 那句「占位项不可预览/下载，但<b>不解除收藏关系</b>」。
 * 实现上很容易「顺手」加一个 {@code note.status == ONLINE} 的条件——那样更符合直觉，
 * 却会让笔记下架时收藏态凭空消失、恢复上架时又凭空回来。所以 OFFLINE / DELETED
 * 两种状态各有一条用例钉住。
 *
 * <p><b>写一半（{@code favorite} / {@code unfavorite}）与读一半的规则正好相反</b>：
 * 收藏<b>只允许</b> ONLINE（40301），取消收藏<b>不限制</b>状态。
 * 这个不对称是刻意的——占位项清不掉就会在收藏列表里越积越多——
 * 所以两边各有用例，免得后来者「顺手」把它对称化。
 * 计数的三处易错点也各钉一条：重复收藏不多加、取消两次不多减、计数漂移成 0 时不减成负数。
 *
 * <p>与 {@code NoteServiceTest} 同样起真库：这里验的是「等值查询有没有命中
 * {@code uk_user_note}」与「计数是否原子增减」，换成 mock 的 Mapper 就只剩「调没调这个方法」，
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
        insertFavoriteRow(readerId, noteId);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
    }

    @Test
    void unfavoritedNoteIsReportedAsFalse() {
        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /** 谓词必须两个条件都匹配：只按 noteId 查会把「别人收藏了」也算成自己收藏了 */
    @Test
    void anotherUsersFavoriteIsNotCounted() {
        insertFavoriteRow(insertUser("someone-else"), noteId);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /**
     * §6.5：下架不解除收藏关系。这里刻意把 status 改掉再问一次，
     * 若实现里混进了 {@code status == ONLINE} 的判断，这条会红。
     */
    @Test
    void offlineNoteStillReportsTheRelationship() {
        insertFavoriteRow(readerId, noteId);
        setNoteStatus(NoteStatus.OFFLINE);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
    }

    /** 软删除同理：行还在，关系就还在，收藏列表靠它渲染「笔记已删除」占位 */
    @Test
    void deletedNoteStillReportsTheRelationship() {
        insertFavoriteRow(readerId, noteId);
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

    // ==================== 写入侧：收藏 ====================

    /** 关系落库、计数自增、返回值就是操作后的最新值，三者必须一起对 */
    @Test
    void favoritingStoresTheRelationshipAndReturnsTheNewCount() {
        assertThat(favoriteService.favorite(readerId, noteId)).isEqualTo(1L);

        assertThat(favoriteService.isFavorited(readerId, noteId)).isTrue();
        assertThat(favoriteCountInDb()).isEqualTo(1L);
    }

    /**
     * 重复收藏报 40902，<b>且计数一次都不能多加</b>。
     *
     * <p>后半句是重点：实现若把自增放在插入之前，撞唯一键回滚之后计数虽然会跟着回滚，
     * 但只要有任何一条路径让它提交了（比如把 {@code DuplicateKeyException} 吞掉），
     * 计数就会凭空多 1，而且再也不会有人发现。这条用例把它钉死。
     */
    @Test
    void duplicateFavoriteIsRejectedAndLeavesTheCountAlone() {
        favoriteService.favorite(readerId, noteId);

        assertThatThrownBy(() -> favoriteService.favorite(readerId, noteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_FAVORITED));

        assertThat(favoriteCountInDb()).isEqualTo(1L);
    }

    /** §6.5：只有 ONLINE 能收藏。与读一半「非 ONLINE 也报 true」正好相反，两条都要有 */
    @Test
    void favoritingOfflineNoteIsRejected() {
        setNoteStatus(NoteStatus.OFFLINE);

        assertThatThrownBy(() -> favoriteService.favorite(readerId, noteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));

        assertThat(favoriteCountInDb()).isZero();
    }

    @Test
    void favoritingDeletedNoteIsRejected() {
        setNoteStatus(NoteStatus.DELETED);

        assertThatThrownBy(() -> favoriteService.favorite(readerId, noteId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNAVAILABLE));
    }

    /**
     * 不存在的笔记报 40400，而不是掉进兜底变 50000。
     *
     * <p>这正是「先读状态取锁」这一步顺带挡下的：若直接 {@code INSERT favorite}，
     * 撞的是外键 {@code fk_fav_note}，抛出的 {@code DataIntegrityViolationException}
     * 不是 {@code DuplicateKeyException}，捕获不到，最终就是 50000——
     * 把「笔记不存在」伪装成「服务器炸了」。
     */
    @Test
    void favoritingUnknownNoteIsReportedAsNotFound() {
        assertThatThrownBy(() -> favoriteService.favorite(readerId, 999_999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** 收藏的是「谁」由 JWT 决定，所以两个人收藏同一篇要各记一行、计数为 2 */
    @Test
    void favoritesByDifferentUsersEachCount() {
        favoriteService.favorite(readerId, noteId);
        favoriteService.favorite(insertUser("another-reader"), noteId);

        assertThat(favoriteCountInDb()).isEqualTo(2L);
    }

    // ==================== 写入侧：取消收藏 ====================

    @Test
    void unfavoritingRemovesTheRelationshipAndDecrementsTheCount() {
        favoriteService.favorite(readerId, noteId);

        assertThat(favoriteService.unfavorite(readerId, noteId)).isZero();

        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
        assertThat(favoriteCountInDb()).isZero();
    }

    /**
     * 幂等：本来就没收藏过也回 200（返回当前计数），不是 40400。
     * DELETE 的应有语义——前端不必「先查再删」，连点两次不该有一次报错。
     */
    @Test
    void unfavoritingWhatWasNeverFavoritedIsAnIdempotentNoOp() {
        assertThat(favoriteService.unfavorite(readerId, noteId)).isZero();
        assertThat(favoriteCountInDb()).isZero();
    }

    /**
     * 与收藏的 40301 不对称：已下架 / 已删除的笔记<b>允许</b>取消收藏。
     * 若这里也拦，收藏列表里的占位项就永远清不掉，只能越积越多。
     */
    @Test
    void unfavoritingWorksOnAnOfflineNote() {
        insertFavoriteRow(readerId, noteId);
        // 计数要手动摆对：本用例的前置是「已收藏且计数为 1」，绕过了 Service 就得自己补齐
        jdbcTemplate.update("UPDATE note SET favorite_count = 1 WHERE id = ?", noteId);
        setNoteStatus(NoteStatus.OFFLINE);

        assertThat(favoriteService.unfavorite(readerId, noteId)).isZero();

        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
        assertThat(favoriteCountInDb()).isZero();
    }

    @Test
    void unfavoritingWorksOnADeletedNote() {
        insertFavoriteRow(readerId, noteId);
        jdbcTemplate.update("UPDATE note SET favorite_count = 1 WHERE id = ?", noteId);
        setNoteStatus(NoteStatus.DELETED);

        assertThat(favoriteService.unfavorite(readerId, noteId)).isZero();

        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /** 连点两次取消：第二次不能再减，否则计数比真实关系数还少 */
    @Test
    void unfavoritingTwiceDecrementsTheCountOnlyOnce() {
        favoriteService.favorite(readerId, noteId);

        favoriteService.unfavorite(readerId, noteId);
        favoriteService.unfavorite(readerId, noteId);

        assertThat(favoriteCountInDb()).isZero();
    }

    /**
     * 计数漂移时不减成负数。§3.3 说 {@code favorite_count} 是派生值，靠一条离线校准 SQL 对账，
     * 所以「关系在、计数却是 0」是可能的中间状态。真减成 -1 的话，
     * 前端会渲染出「-1 人收藏」，而这个观感问题比少减一次严重得多——
     * 校准 SQL 迟早会把真实值算回来。
     */
    @Test
    void aDriftedCountDoesNotGoNegative() {
        insertFavoriteRow(readerId, noteId);
        jdbcTemplate.update("UPDATE note SET favorite_count = 0 WHERE id = ?", noteId);

        assertThat(favoriteService.unfavorite(readerId, noteId)).isZero();

        assertThat(favoriteCountInDb()).isZero();
        assertThat(favoriteService.isFavorited(readerId, noteId)).isFalse();
    }

    /**
     * 不能取消别人收藏的。{@code favorite} 的删除谓词必须同时匹配 {@code user_id} 与
     * {@code note_id}——只按 {@code note_id} 删就会把别人的收藏一起删掉，
     * 而计数只减 1，于是表与计数从此对不上。
     */
    @Test
    void unfavoritingDoesNotRemoveAnotherUsersFavorite() {
        Long otherReaderId = insertUser("another-reader");
        insertFavoriteRow(otherReaderId, noteId);
        jdbcTemplate.update("UPDATE note SET favorite_count = 1 WHERE id = ?", noteId);

        favoriteService.unfavorite(readerId, noteId);

        assertThat(favoriteService.isFavorited(otherReaderId, noteId)).isTrue();
        assertThat(favoriteCountInDb()).isEqualTo(1L);
    }

    @Test
    void unfavoritingAnUnknownNoteIsReportedAsNotFound() {
        assertThatThrownBy(() -> favoriteService.unfavorite(readerId, 999_999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /**
     * 绕过 Service 直接铺一条收藏关系。
     *
     * <p>名字里带 {@code Row} 是为了与 {@link FavoriteService#favorite} 分清：
     * 读一半的用例要的是「表里已经有这条关系」这个前置状态，与写入侧的行为无关，
     * 走 Service 反而要先把状态、计数都摆对。
     */
    private void insertFavoriteRow(Long userId, Long targetNoteId) {
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setNoteId(targetNoteId);
        favoriteMapper.insert(favorite);
    }

    /** 直接改库而不是走状态机：本用例要的是「表里已经是那个状态」，与迁移是否合法无关 */
    private void setNoteStatus(NoteStatus status) {
        jdbcTemplate.update("UPDATE note SET status = ? WHERE id = ?", status.name(), noteId);
    }

    /** 读库里的收藏量，而不是信 Service 的返回值——两者要一起断言才说明问题 */
    private long favoriteCountInDb() {
        return noteMapper.selectById(noteId).getFavoriteCount();
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
