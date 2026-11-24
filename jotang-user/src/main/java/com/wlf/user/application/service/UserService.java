package com.wlf.user.application.service;

import com.wlf.user.application.command.UpdateProfileCommand;
import com.wlf.user.application.command.UploadAvatarCommand;
import com.wlf.user.application.command.UploadedFile;
import com.wlf.user.application.query.BatchUserDisplayQuery;
import com.wlf.user.application.result.CollegeResult;
import com.wlf.user.application.result.UserDisplayResult;
import com.wlf.user.application.result.UserResult;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.user.domain.user.AvatarDecision;
import com.wlf.user.domain.user.AvatarFilePolicy;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import com.wlf.user.domain.user.model.AvatarKey;
import com.wlf.user.domain.user.model.CollegeId;
import com.wlf.user.domain.user.model.Nickname;
import com.wlf.storage.Bucket;
import com.wlf.storage.OrphanObjectPublisher;
import com.wlf.storage.StorageService;
import com.wlf.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资料维护、头像上传、个人主页统计。
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
     * 批量取用户展示信息。笔记列表与收藏列表用它填上传者昵称、头像与学院。
     */
    public List<UserDisplayResult> batchDisplay(BatchUserDisplayQuery query) {
        Collection<Long> userIds = query.ids();
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> collegeNames = collegeService.list().stream()
                .collect(Collectors.toMap(CollegeResult::id, CollegeResult::name));
        return userRepository.findByIds(userIds).stream()
                .sorted(Comparator.comparing(User::id))
                .map(user -> new UserDisplayResult(user.id(), user.nickname().value(),
                        user.avatar() == null ? null : user.avatar().value(),
                        collegeNames.get(user.collegeId().value())))
                .toList();
    }

    /**
     * 改昵称 / 改学院。
     */
    public UserResult updateProfile(UpdateProfileCommand command) {
        Long userId = command.userId();
        // 先判用户、后判学院
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!collegeService.exists(command.collegeId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("collegeId", "学院不存在")));
        }

        user.changeProfile(new Nickname(command.nickname()),
                new CollegeId(command.collegeId()));
        userRepository.updateProfile(user);
        return UserResult.from(user, storageService);
    }

    /**
     * 上传头像：校验 → 传 OSS → 单列更新 {@code user.avatar}。
     *
     * @throws BusinessException 40400 用户不存在；42200 文件类型 / 大小不合法
     */
    public UserResult uploadAvatar(UploadAvatarCommand command) {
        Long userId = command.userId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        AvatarDecision decision = inspectAvatar(command.file(), userId);
        String key = avatarFilePolicy.objectKey(userId, decision.extension());
        StoredObject stored = storeAvatar(command.file(), key, decision, userId);

        try {
            user.changeAvatar(new AvatarKey(stored.key()));
            userRepository.updateAvatar(user);
        } catch (RuntimeException e) {
            // 更新失败则刚传的对象无人引用，删掉；删不掉也只是留下孤儿，不影响本次请求的成败
            purgeQuietly(stored.key());
            throw e;
        }

        return UserResult.from(user, storageService);
    }

    private AvatarDecision inspectAvatar(UploadedFile file, Long userId) {

        try (InputStream in = file.open()) {
            return avatarFilePolicy.inspect(file.originalFilename(), file.size(), in);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.originalFilename(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取上传文件失败");
        }
    }

    private StoredObject storeAvatar(UploadedFile file, String key, AvatarDecision decision, Long userId) {
        try (InputStream in = file.open()) {
            return storageService.upload(Bucket.AVATAR, key, in, file.size(),
                    decision.contentType(), AVATAR_CACHE_CONTROL);
        } catch (IOException e) {
            log.error("读取上传头像失败 userId={} name={}", userId, file.originalFilename(), e);
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
