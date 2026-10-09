package com.wlf.user;

import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserService#uploadAvatar} 的补偿路径：<b>更新失败要把刚传上去的对象删掉</b>，
 * 否则它永远无人引用。
 *
 * <p><b>为什么单独一个类</b>：这条要逼出 {@code UserMapper#update} 抛异常，必须把 mapper 换成替身；
 * 而 {@link UserAvatarServiceTest} 特意用真实数据库来验证「只写了一列」与 {@code updated_at}，
 * 两者对 mapper 的要求正好相反，放不到同一个上下文里。
 *
 * <p><b>为什么是 {@code @MockitoSpyBean} 而不是 {@code @MockitoBean}</b>：实现里用的
 * {@code Wrappers.#lambdaUpdate()} 依赖 MyBatis-Plus 的 TableInfo lambda 缓存，而那张缓存是
 * MyBatis <b>解析 mapper 接口</b>时建起来的。整体替换成 mock 会让 {@code UserMapper} 根本不被解析，
 * 于是 {@code User::getAvatar} 解析不了，抛 {@code MybatisPlusException}——即还没走到被 stub 的
 * {@code update} 就先炸了（已实测）。spy 保留真实 bean 的创建，缓存照常建立，我们只把
 * {@code update} 这一个方法改成抛异常。
 */
@SpringBootTest
@ActiveProfiles("local")
class UserAvatarCompensationTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final String KEY = "avatars/7/11111111-2222-3333-4444-555555555555.png";

    @Autowired
    private UserService userService;

    @MockitoSpyBean
    private UserMapper userMapper;

    @MockitoBean
    private StorageService storageService;

    @Test
    void objectIsPurgedWhenAvatarUpdateFails() {
        User user = new User();
        user.setId(7L);
        user.setUsername("someone");
        user.setNickname("昵称");
        user.setCollegeId(1L);
        user.setRole("USER");
        // selectById 用 doReturn：spy 上的 when(...) 会真去查一次库（本用例不依赖库里的行）
        doReturn(user).when(userMapper).selectById(7L);
        when(storageService.upload(any(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(new StoredObject(KEY, "image/png", PNG.length));
        doThrow(new DataIntegrityViolationException("boom"))
                .when(userMapper).update(isNull(), any());

        MultipartFile file = new MockMultipartFile("file", "头像.png", "image/png", PNG);

        // 原始异常必须原样抛出，不能被「补偿」这件事掩盖
        assertThatThrownBy(() -> userService.uploadAvatar(7L, file))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storageService).delete(Bucket.AVATAR, KEY);
    }
}
