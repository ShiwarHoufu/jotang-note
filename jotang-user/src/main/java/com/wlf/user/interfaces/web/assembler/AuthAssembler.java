package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.LoginCommand;
import com.wlf.user.application.command.RegisterCommand;
import com.wlf.user.application.query.CurrentUserQuery;
import com.wlf.user.application.result.LoginResult;
import com.wlf.user.interfaces.web.dto.LoginRequest;
import com.wlf.user.interfaces.web.dto.LoginResponse;
import com.wlf.user.interfaces.web.dto.RegisterRequest;
import org.springframework.stereotype.Component;

/** auth 接口的 Request/Response 与 Command/Result 映射。 */
@Component
public class AuthAssembler {

    private final UserAssembler userAssembler;

    public AuthAssembler(UserAssembler userAssembler) {
        this.userAssembler = userAssembler;
    }

    public RegisterCommand toCommand(RegisterRequest request) {
        return new RegisterCommand(request.getUsername(), request.getEmail(),
                request.getPassword(), request.getCollegeId());
    }

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.getUsername(), request.getPassword());
    }

    public CurrentUserQuery toQuery(Long userId) {
        return new CurrentUserQuery(userId);
    }

    public LoginResponse toLoginResponse(LoginResult result) {
        return new LoginResponse(result.token(), userAssembler.toResponse(result.user()));
    }
}
