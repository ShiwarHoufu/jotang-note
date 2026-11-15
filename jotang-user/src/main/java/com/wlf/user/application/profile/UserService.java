package com.wlf.user.application.profile;

import com.wlf.user.application.dto.UserInfo;
import com.wlf.user.application.college.CollegeService;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.user.domain.user.AvatarDecision;
import com.wlf.user.domain.user.AvatarFilePolicy;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import com.wlf.storage.Bucket;
import com.wlf.storage.OrphanObjectPublisher;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import com.wlf.user.application.profile.dto.UpdateProfileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 资料维护、头像上传、个人主页统计。
 * 其他模块取用户信息也走此 Service，不直接引用 UserMapper。
 *
 * <p>当前已落地改资料与头像上传；个人主页随后续切片补上。
 */
@Slf4j
@Service
public class UserService {

    /**
     * 头像对象的 {@code Cache-Control}。
     *
     * <p>{@code immutable} 之所以成立，是因为每次换头像都换一个键，同一个 URL 指向的对象永不改变。
     * 若哪天改成原地覆盖，这个头必须一并去掉，否则用户会一直看到旧头像。
     */
    private static final String AVATAR_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final UserRepository userRepository;

    private final CollegeService collegeService;

    /** 头像的准入规则。与 note 的 {@code NoteFilePolicy} 各持一份 */
    private final AvatarFilePolicy avatarFilePolicy;

    private final StorageService storageService;

    /** 补偿删除的第二道通道，与 note 上传那条共用同一个队列。*/
    private final OrphanObjectPublisher orphanObjectPublisher;

    public UserService(UserRepository userRepository,
                       CollegeService collegeService,
                       AvatarFilePolicy avatarFilePolicy,
                       StorageService storageService,
                       OrphanObjectPublisher orphanObjectPublisher) {
        this.userRepository = userRepository;
        this.collegeService = collegeService;
        this.avatarFilePolicy = avatarFilePolicy;
        this.storageService = storageService;
        this.orphanObjectPublisher = orphanObjectPublisher;
    }

    /**
     * 改昵称 / 改学院。
     */
    public UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        // 先判用户、后判学院
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!collegeService.exists(request.getCollegeId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("collegeId", "学院不存在")));
        }

        String nickname = request.getNickname().trim();

        userRepository.updateProfile(userId, nickname, request.getCollegeId());

        user.setNickname(nickname);
        user.setCollegeId(request.getCollegeId());
        return UserInfo.from(user, storageService);
    }

    /**
     * 上传头像：校验 → 传 OSS → 单列更新 {@code user.avatar}。
     *
     * @param userId 换谁的头像，取自 JWT，不接受请求体传入——否则可以改任何人的头像
     * @throws BusinessException 40400 用户不存在；42200 文件类型 / 大小不合法
     */
    public UserInfo uploadAvatar(Long userId, MultipartFile file) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        AvatarDecision decision = inspectAvatar(file, userId);
        String key = avatarFilePolicy.objectKey(userId, decision.extension());
        StoredObject stored = storeAvatar(file, key, decision, userId);

        try {
            userRepository.updateAvatar(userId, stored.key());
        } catch (RuntimeException e) {
            // 更新失败则刚传的对象无人引用，删掉；删不掉也只是留下孤儿，不影响本次请求的成败
            purgeQuietly(stored.key());
            throw e;
        }

        user.setAvatar(stored.key());
        return UserInfo.from(user, storageService);
    }

    private AvatarDecision inspectAvatar(MultipartFile file, Long userId) {

        try (InputStream in = file.getInputStream()) {
            return avatarFilePolicy.inspect(file.getOriginalFilename(), file.getSize(), in);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private StoredObject storeAvatar(MultipartFile file, String key, AvatarDecision decision, Long userId) {
        try (InputStream in = file.getInputStream()) {
            return storageService.upload(Bucket.AVATAR, key, in, file.getSize(),
                    decision.contentType(), AVATAR_CACHE_CONTROL);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    /**
     * 补偿删除：更新失败时刚上传的对象已无人引用。
     *
     * <p>这类孤儿同样没有库行，定时对账扫不到。
     */
    private void purgeQuietly(String key) {
        try {
            orphanObjectPublisher.publish(Bucket.AVATAR, key);
        } catch (RuntimeException e) {
            log.error("补偿删除上报失败，退化为仅内联删除 key={}", key, e);
        }
        try {
            storageService.delete(Bucket.AVATAR, key);
        } catch (RuntimeException e) {
            log.error("补偿删除头像对象失败 key={}", key, e);
        }
    }
}
