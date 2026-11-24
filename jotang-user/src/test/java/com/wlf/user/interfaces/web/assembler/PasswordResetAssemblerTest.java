package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.ForgotPasswordCommand;
import com.wlf.user.application.command.ResetPasswordCommand;
import com.wlf.user.interfaces.web.dto.ForgotPasswordRequest;
import com.wlf.user.interfaces.web.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetAssemblerTest {

    private final PasswordResetAssembler assembler = new PasswordResetAssembler();

    @Test
    void mapsForgotRequestToCommand() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("alice@example.com");

        ForgotPasswordCommand command = assembler.toCommand(request);

        assertThat(command.email()).isEqualTo("alice@example.com");
    }

    @Test
    void mapsResetRequestToCommand() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("raw-token");
        request.setNewPassword("newpassword");

        ResetPasswordCommand command = assembler.toCommand(request);

        assertThat(command.token()).isEqualTo("raw-token");
        assertThat(command.newPassword()).isEqualTo("newpassword");
    }
}
