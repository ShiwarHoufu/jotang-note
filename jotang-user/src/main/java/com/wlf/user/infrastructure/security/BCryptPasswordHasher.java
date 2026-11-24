package com.wlf.user.infrastructure.security;

import com.wlf.user.domain.user.PasswordHasher;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt 实现的密码哈希端口。 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PasswordHash hash(RawPassword rawPassword) {
        return new PasswordHash(passwordEncoder.encode(rawPassword.value()));
    }

    @Override
    public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
        return passwordEncoder.matches(rawPassword.value(), passwordHash.value());
    }
}
