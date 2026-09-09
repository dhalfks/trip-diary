package com.tripdiary.user;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.me(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateRequest request) {
        return users.update(UUID.fromString(jwt.getSubject()), request.nickname());
    }

    public record UpdateRequest(@NotBlank @Size(max = 40) String nickname) {}
}
