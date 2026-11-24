package com.wlf.user.domain.user;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.user.domain.user.model.AvatarKey;
import com.wlf.user.domain.user.model.CollegeId;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.Nickname;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;
import com.wlf.user.domain.user.model.Role;
import com.wlf.user.domain.user.model.UserStatus;
import com.wlf.user.domain.user.model.Username;

import java.util.List;
import java.util.Objects;

/**
 * 用户聚合根：账号、凭证、资料与授权状态的唯一变更入口。
 */
public final class User {

    private Long id;
    private final Username username;
    private final Email email;
    private PasswordHash passwordHash;
    private Nickname nickname;
    private CollegeId collegeId;
    private AvatarKey avatar;
    private final Role role;
    private UserStatus status;

    private User(Long id, Username username, Email email, PasswordHash passwordHash,
                 Nickname nickname, CollegeId collegeId, AvatarKey avatar,
                 Role role, UserStatus status) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.collegeId = collegeId;
        this.avatar = avatar;
        this.role = role;
        this.status = status;
    }

    /** 注册：角色、状态与默认昵称由聚合决定。 */
    public static User register(Username username, Email email,
                                PasswordHash passwordHash, CollegeId collegeId) {
        return new User(null, username, email, passwordHash,
                Nickname.fromUsername(username), collegeId, null,
                Role.USER, UserStatus.ACTIVE);
    }

    /** 仅供仓储重建。 */
    public static User reconstitute(Long id, Username username, Email email,
                                    PasswordHash passwordHash, Nickname nickname,
                                    CollegeId collegeId, AvatarKey avatar,
                                    Role role, UserStatus status) {
        return new User(id, username, email, passwordHash, nickname, collegeId, avatar, role, status);
    }

    /** 前置条件：学院是否存在由应用层先校验。 */
    public void changeProfile(Nickname nickname, CollegeId collegeId) {
        this.nickname = Objects.requireNonNull(nickname);
        this.collegeId = Objects.requireNonNull(collegeId);
    }

    /** 头像键必须属于当前用户。 */
    public void changeAvatar(AvatarKey avatarKey) {
        if (avatarKey != null && !avatarKey.belongsTo(id)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("avatar", "头像对象键不属于当前用户")));
        }
        this.avatar = avatarKey;
    }

    public void changePassword(PasswordHash newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    public boolean verifyPassword(RawPassword rawPassword, PasswordHasher hasher) {
        return hasher.matches(rawPassword, passwordHash);
    }

    public void ensureCanLogin() {
        if (status == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void enable() {
        this.status = UserStatus.ACTIVE;
    }

    /** 仓储插入后回填自增主键，只允许一次。 */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("id 已分配");
        }
        this.id = Objects.requireNonNull(id);
    }

    public Long id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Nickname nickname() {
        return nickname;
    }

    public CollegeId collegeId() {
        return collegeId;
    }

    public AvatarKey avatar() {
        return avatar;
    }

    public Role role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public boolean isAdmin() {
        return role.isAdmin();
    }
}
