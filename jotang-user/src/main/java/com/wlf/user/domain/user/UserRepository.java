package com.wlf.user.domain.user;

import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.Username;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户聚合的持久化端口。
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(Email email);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);

    List<User> findByIds(Collection<Long> ids);

    void insert(User user);

    void updateProfile(User user);

    void updateAvatar(User user);

    void updatePassword(User user);
}
