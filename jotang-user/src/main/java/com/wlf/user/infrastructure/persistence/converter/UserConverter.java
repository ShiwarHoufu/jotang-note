package com.wlf.user.infrastructure.persistence.converter;

import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.model.AvatarKey;
import com.wlf.user.domain.user.model.CollegeId;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.Nickname;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.Role;
import com.wlf.user.domain.user.model.UserStatus;
import com.wlf.user.domain.user.model.Username;
import com.wlf.user.infrastructure.persistence.po.UserPO;
import org.springframework.stereotype.Component;

/** UserPO 与 User 聚合之间的转换。 */
@Component
public class UserConverter {

    public User toDomain(UserPO po) {
        return User.reconstitute(
                po.getId(),
                new Username(po.getUsername()),
                new Email(po.getEmail()),
                new PasswordHash(po.getPasswordHash()),
                new Nickname(po.getNickname()),
                new CollegeId(po.getCollegeId()),
                po.getAvatar() == null ? null : new AvatarKey(po.getAvatar()),
                Role.fromName(po.getRole()),
                UserStatus.fromCode(po.getStatus())
        );
    }

    public UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.setId(user.id());
        po.setUsername(user.username().value());
        po.setEmail(user.email().value());
        po.setPasswordHash(user.passwordHash().value());
        po.setNickname(user.nickname().value());
        po.setCollegeId(user.collegeId().value());
        po.setAvatar(user.avatar() == null ? null : user.avatar().value());
        po.setRole(user.role().name());
        po.setStatus(user.status().code());
        return po;
    }
}
