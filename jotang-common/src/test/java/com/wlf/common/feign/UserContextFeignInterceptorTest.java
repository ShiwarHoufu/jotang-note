package com.wlf.common.feign;

import com.wlf.common.AuthenticatedUser;
import com.wlf.common.IdentityHeaders;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextFeignInterceptorTest {

    private final UserContextFeignInterceptor interceptor = new UserContextFeignInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addsUserHeadersWhenSecurityContextHasIdentity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(42L, "zhangsan", "ADMIN"), null, List.of()));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get(IdentityHeaders.USER_ID)).containsExactly("42");
        assertThat(template.headers().get(IdentityHeaders.USER_ROLE)).containsExactly("ADMIN");
    }

    @Test
    void addsNothingWithoutIdentity() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers())
                .doesNotContainKeys(IdentityHeaders.USER_ID, IdentityHeaders.USER_ROLE);
    }
}
