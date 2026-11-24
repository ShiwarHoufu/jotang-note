package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.LoginCommand;
import com.wlf.user.application.command.RegisterCommand;
import com.wlf.user.application.query.CurrentUserQuery;
import com.wlf.user.application.result.LoginResult;
import com.wlf.user.application.result.UserResult;
import com.wlf.user.interfaces.web.dto.LoginRequest;
import com.wlf.user.interfaces.web.dto.LoginResponse;
import com.wlf.user.interfaces.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAssemblerTest {

    private final AuthAssembler assembler = new AuthAssembler(new UserAssembler());

    @Test
    void mapsRegisterRequestToCommand() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");
        request.setCollegeId(7L);

        RegisterCommand command = assembler.toCommand(request);

        assertThat(command.username()).isEqualTo("alice");
        assertThat(command.email()).isEqualTo("alice@example.com");
        assertThat(command.password()).isEqualTo("password123");
        assertThat(command.collegeId()).isEqualTo(7L);
    }

    @Test
    void mapsLoginRequestToCommand() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        LoginCommand command = assembler.toCommand(request);

        assertThat(command.username()).isEqualTo("alice");
        assertThat(command.password()).isEqualTo("password123");
    }

    @Test
    void mapsUserIdToCurrentUserQuery() {
        CurrentUserQuery query = assembler.toQuery(42L);

        assertThat(query.userId()).isEqualTo(42L);
    }

    @Test
    void mapsLoginResultToResponse() {
        UserResult user = new UserResult(1L, "alice", "Alice", 7L, null, "USER");
        LoginResult result = new LoginResult("jwt-token", user);

        LoginResponse response = assembler.toLoginResponse(result);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.user().username()).isEqualTo("alice");
    }
}
