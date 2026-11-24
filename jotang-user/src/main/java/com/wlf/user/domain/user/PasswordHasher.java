package com.wlf.user.domain.user;

import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;

/** 密码哈希端口，具体算法由基础设施决定。 */
public interface PasswordHasher {

    PasswordHash hash(RawPassword rawPassword);

    boolean matches(RawPassword rawPassword, PasswordHash passwordHash);
}
