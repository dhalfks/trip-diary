package com.tripdiary.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import com.tripdiary.user.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse signup(String email, String password, String nickname) {
        validatePasswordBytes(password);
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User(normalizedEmail, passwordEncoder.encode(password), nickname.strip());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return UserResponse.from(user);
    }

    @Transactional
    public TokenService.TokenResponse login(String email, String password) {
        validatePasswordBytes(password);
        User user = users.findByEmail(normalizeEmail(email)).orElse(null);
        String hashToCheck = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);
        if (user == null || !passwordMatches || !user.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return tokens.issue(user);
    }

    public TokenService.TokenResponse refresh(String refreshToken) {
        return tokens.rotate(refreshToken);
    }

    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
    }
}
