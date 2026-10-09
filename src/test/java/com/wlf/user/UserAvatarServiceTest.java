package com.wlf.user;

import com.wlf.auth.dto.UserInfo;
import com.wlf.catalog.CollegeMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link UserService#uploadAvatar} 的用例：对象键、落库列、缓存头与拒绝路径。见《概要设计》§6.7。
 *
 * <p><b>为何把 {@link StorageService} 换成 mock</b>：§1.1 把存储抽象成只含四件原语的接口，
 * 正是为了让业务测试不必碰 OSS SDK。真打 OSS 的验证在 {@code OssStorageServiceTest}
 * （{@code @Tag("oss")}，默认排除），那边验「对象与元数据存得对不对」，这边验
 * 「键怎么拼、库里写了哪一列、失败时怎么收场」。
 *
 * <p>用真实数据库（{@code @Transactional} 回滚）而不是纯 mock：本类有几条断言只在真实
 * UPDATE 下才有意义——「只写 avatar 一列」与「{@code updated_at} 被 ON UPDATE 推进」，
 * 对着 mock 的 mapper 断言毫无价值。
 *
 * <p>「更新失败要删掉刚传的对象」那条不在这里：它需要让 {@code UserMapper#update} 抛异常，
 * 与「用真实数据库」冲突，单独放在 {@link UserAvatarCompensationTest}。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserAvatarServiceTest {

    private static final String MARKER = "ZZTEST-AVATAR-SVC-";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] SVG = "<svg onload=alert(1)>".getBytes();

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        // 让上传原语原样回传对象键与类型，便于断言「存下来的 key 就是落库的那个」
        when(storageService.upload(any(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> new StoredObject(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(4, String.class),
                        invocation.getArgument(3, Long.class)));
        // 公网 URL 由桶名 + endpoint 拼成，测试里不必知道真实值，只要可辨认即可
        when(storageService.publicUrl(eq(Bucket.AVATAR), anyString()))
                .thenAnswer(invocation -> "https://avatar.example/" + invocation.getArgument(1, String.class));
    }

    // ==================== 正常路径 ====================

    /** 对象键按 {@code avatars/{userId}/{uuid}.{ext}} 落库，响应里的 {@code avatarUrl} 由该键拼出 */
    @Test
    void uploadingAvatarStoresVersionedKeyAndReturnsPublicUrl() {
        Long userId = insertUser("原昵称");

        UserInfo updated = userService.uploadAvatar(userId, png());

        User row = userMapper.selectById(userId);
        assertThat(row.getAvatar()).matches("avatars/" + userId + "/[0-9a-f-]{36}\\.png");
        assertThat(updated.avatarUrl()).isEqualTo("https://avatar.example/" + row.getAvatar());
        // 其余字段原样回显
        assertThat(updated.id()).isEqualTo(userId);
        assertThat(updated.nickname()).isEqualTo("原昵称");
    }

    /**
     * 再传一次就是换头像：同一个接口既设首次、也换后续，<b>没有第二条路径</b>。
     *
     * <p>这里验证的是「键版本化」这个前提本身——两次上传得到两个不同的键，库里只剩后一个。
     * 旧对象既不删也不覆盖（§7.3 的清理尚未落地），它只是不再被 {@code user.avatar} 引用；
     * 旧 URL 在对方缓存过期前仍可访问（§8.4 第 8 条）。
     */
    @Test
    void uploadingAgainReplacesTheAvatarKey() {
        Long userId = insertUser("昵称");

        userService.uploadAvatar(userId, png());
        String first = userMapper.selectById(userId).getAvatar();

        userService.uploadAvatar(userId, png());
        String second = userMapper.selectById(userId).getAvatar();

        assertThat(second).matches("avatars/" + userId + "/[0-9a-f-]{36}\\.png");
        assertThat(second).isNotEqualTo(first);
        // 换头像不删旧对象——这条断言就是 §5.2 里那句「旧头像对象不删」的守卫
        verify(storageService, never()).delete(any(), anyString());
    }

    /**
     * 上传用头像桶，且带上 §6.7 要求的长强缓存头。缓存头是「版本化键」能成立的前提，
     * 写错了不会当场暴露（头像照常显示），故在这里钉住。
     */
    @Test
    void avatarIsStoredInAvatarBucketWithImmutableCacheControl() {
        Long userId = insertUser("昵称");

        userService.uploadAvatar(userId, png());

        String key = userMapper.selectById(userId).getAvatar();
        verify(storageService).upload(eq(Bucket.AVATAR), eq(key), any(),
                eq((long) PNG.length), eq("image/png"), eq("public, max-age=31536000, immutable"));
    }

    /**
     * 换头像只动 {@code avatar} 一列。把它交给 {@code updateById} 会生成全列 SET，
     * 覆盖并发改动、平白重写密码哈希，还抑制 {@code updated_at} 的 ON UPDATE——
     * 与 {@code UserService#updateProfile} 是同一条规矩。
     */
    @Test
    void onlyAvatarColumnIsWritten() {
        Long userId = insertUser("昵称");
        LocalDateTime registeredAt = LocalDateTime.of(2021, 3, 4, 5, 6, 7);
        jdbcTemplate.update("UPDATE `user` SET role = ?, status = ?, created_at = ? WHERE id = ?",
                "ADMIN", 0, registeredAt, userId);
        User before = userMapper.selectById(userId);

        userService.uploadAvatar(userId, png());

        User after = userMapper.selectById(userId);
        assertThat(after.getAvatar()).isNotNull();
        assertThat(after.getNickname()).isEqualTo("昵称");
        assertThat(after.getCollegeId()).isEqualTo(before.getCollegeId());
        assertThat(after.getRole()).isEqualTo("ADMIN");
        assertThat(after.getStatus()).isZero();
        assertThat(after.getUsername()).isEqualTo(before.getUsername());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getCreatedAt()).isEqualTo(registeredAt);
    }

    /** 换头像是一次真实修改，{@code updated_at} 由 MySQL 的 ON UPDATE 推进（不手写） */
    @Test
    void updatedAtAdvancesWhenAvatarChanges() {
        Long userId = insertUser("昵称");
        LocalDateTime stale = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE `user` SET updated_at = ? WHERE id = ?", stale, userId);

        userService.uploadAvatar(userId, png());

        assertThat(userMapper.selectById(userId).getUpdatedAt()).isAfter(stale);
    }

    // ==================== 拒绝路径 ====================

    /** 用户不存在：40400，且一个对象都不该产生 */
    @Test
    void missingUserThrowsNotFoundWithoutTouchingStorage() {
        assertThatThrownBy(() -> userService.uploadAvatar(999999999L, png()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(storageService);
    }

    /** 文件不合格要在碰 OSS 之前拦下，否则只能靠补偿删除收场（与 NoteService 同一条规矩） */
    @Test
    void unacceptableFileIsRejectedBeforeTouchingStorage() {
        Long userId = insertUser("昵称");

        assertThatThrownBy(() -> userService.uploadAvatar(userId, file("evil.svg", "image/svg+xml", SVG)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FILE_INVALID));

        verifyNoInteractions(storageService);
        assertThat(userMapper.selectById(userId).getAvatar()).isNull();
    }

    // ==================== 辅助 ====================

    private static MultipartFile png() {
        return file("头像.png", "image/png", PNG);
    }

    private static MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private College insertCollege() {
        College college = new College();
        college.setName(MARKER + UUID.randomUUID().toString().substring(0, 8));
        collegeMapper.insert(college);
        return college;
    }

    /** 后缀取 8 位：{@code user.username} 列宽 32，整串 UUID 会超长 */
    private Long insertUser(String nickname) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        College college = insertCollege();

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(MARKER + suffix + "@example.com");
        user.setPasswordHash("$2a$10$test-only-not-a-real-bcrypt-hash");
        user.setNickname(nickname);
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user.getId();
    }
}
