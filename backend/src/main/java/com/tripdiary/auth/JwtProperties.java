package com.tripdiary.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String accessSecret,
        String refreshSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public JwtProperties {
        requireText(issuer, "app.jwt.issuer");
        requireText(audience, "app.jwt.audience");
        requireSecret(accessSecret, "app.jwt.access-secret");
        requireSecret(refreshSecret, "app.jwt.refresh-secret");
        if (accessSecret != null && accessSecret.equals(refreshSecret)) {
            throw new IllegalStateException("Access Token과 Refresh Token 서명키는 달라야 합니다.");
        }
        requirePositive(accessTokenTtl, "app.jwt.access-token-ttl");
        requirePositive(refreshTokenTtl, "app.jwt.refresh-token-ttl");
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience
                + ", accessSecret=[REDACTED], refreshSecret=[REDACTED], accessTokenTtl="
                + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " 설정이 필요합니다.");
        }
    }

    private static void requireSecret(String value, String property) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException(property + "는 32자 이상이어야 합니다.");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + "는 양수여야 합니다.");
        }
    }
}
