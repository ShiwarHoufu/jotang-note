package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.ForgotPasswordCommand;
import com.wlf.user.application.command.ResetPasswordCommand;
import com.wlf.user.interfaces.web.dto.ForgotPasswordRequest;
import com.wlf.user.interfaces.web.dto.ResetPasswordRequest;
import org.springframework.stereotype.Component;

/** 密码找回接口的 Request 与 Command 映射。 */
@Component
public class PasswordResetAssembler {

    public ForgotPasswordCommand toCommand(ForgotPasswordRequest request) {
        return new ForgotPasswordCommand(request.getEmail());
    }

    public ResetPasswordCommand toCommand(ResetPasswordRequest request) {
        return new ResetPasswordCommand(request.getToken(), request.getNewPassword());
    }
}
