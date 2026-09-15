package com.tripdiary.auth;

import com.tripdiary.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public TokenService.TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenService.TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    public record SignupRequest(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
            String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
            String password,
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(max = 40, message = "닉네임은 40자 이하여야 합니다.")
            String nickname) {

        @Override
        public String toString() {
            return "SignupRequest[email=[REDACTED], password=[REDACTED], nickname=[REDACTED]]";
        }
    }

    public record LoginRequest(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
            String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
            String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=[REDACTED], password=[REDACTED]]";
        }
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh Token은 필수입니다.")
            @Size(max = 4096, message = "Refresh Token 형식이 올바르지 않습니다.")
            String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[refreshToken=[REDACTED]]";
        }
    }
}
