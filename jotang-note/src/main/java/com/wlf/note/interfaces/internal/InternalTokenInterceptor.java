package com.wlf.note.interfaces.internal;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部接口的服务令牌校验。
 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Internal-Token";

    private final String token;

    public InternalTokenInterceptor(String token) {
        this.token = token;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!token.equals(request.getHeader(HEADER))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "内部接口鉴权失败");
        }
        return true;
    }
}
