package com.tripdiary.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String nickname,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getStatus(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
