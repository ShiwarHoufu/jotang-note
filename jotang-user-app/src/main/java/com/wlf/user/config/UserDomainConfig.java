package com.wlf.user.config;

import com.wlf.user.domain.user.AvatarFilePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 领域对象的装配。 */
@Configuration
public class UserDomainConfig {

    @Bean
    public AvatarFilePolicy avatarFilePolicy() {
        return new AvatarFilePolicy();
    }
}
