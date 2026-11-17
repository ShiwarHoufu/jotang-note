package com.wlf.common.feign;

import com.wlf.common.AuthenticatedUser;
import com.wlf.common.IdentityHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 服务间调用时透传当前用户身份。
 *
 * <p>只读 SecurityContext；异步 / 定时任务线程没有身份时不加头，
 * 这类调用应由方法参数显式传 userId。
 */
public class UserContextFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return;
        }
        if (user.userId() == null || user.role() == null) {
            return;
        }
        template.header(IdentityHeaders.USER_ID, String.valueOf(user.userId()));
        template.header(IdentityHeaders.USER_ROLE, user.role());
    }
}
