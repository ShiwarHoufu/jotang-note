package com.wlf.user.domain.user;

import java.util.Optional;

/**
 * 用户聚合的持久化端口。
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    void insert(User user);

    void updateProfile(Long userId, String nickname, Long collegeId);

    void updateAvatar(Long userId, String avatar);

    void updatePassword(Long userId, String passwordHash);
}
