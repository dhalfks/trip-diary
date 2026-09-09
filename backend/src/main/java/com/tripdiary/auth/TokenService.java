package com.tripdiary.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.user.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    public static final String TOKEN_TYPE_CLAIM = "token_type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtEncoder accessTokenEncoder;
    private final JwtEncoder refreshTokenEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenService(
            @Qualifier("accessTokenEncoder") JwtEncoder accessTokenEncoder,
            @Qualifier("refreshTokenEncoder") JwtEncoder refreshTokenEncoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            JwtProperties properties,
            RefreshTokenRepository refreshTokens,
            Clock clock) {
        this.accessTokenEncoder = accessTokenEncoder;
        this.refreshTokenEncoder = refreshTokenEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse issue(User user) {
        return createTokenPair(user);
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public TokenResponse rotate(String rawRefreshToken) {
        Jwt jwt = decodeRefreshToken(rawRefreshToken);
        UUID tokenId = parseUuid(jwt.getId());
        UUID userId = parseUuid(jwt.getSubject());
        RefreshToken stored = refreshTokens.findByIdForUpdate(tokenId)
                .orElseThrow(this::invalidRefreshToken);

        verifyStoredToken(stored, userId, rawRefreshToken);
        Instant now = clock.instant();
        if (stored.isRevoked()) {
            refreshTokens.revokeAllActiveByUserId(userId, now);
            throw new RefreshTokenReuseException();
        }
        if (stored.isExpired(now) || !stored.getUser().isActive()) {
            stored.revoke(now, null);
            throw invalidRefreshToken();
        }

        UUID replacementId = UUID.randomUUID();
        stored.revoke(now, replacementId);
        return createTokenPair(stored.getUser(), replacementId, now);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        Jwt jwt = decodeRefreshToken(rawRefreshToken);
        UUID tokenId = parseUuid(jwt.getId());
        UUID userId = parseUuid(jwt.getSubject());
        RefreshToken stored = refreshTokens.findByIdForUpdate(tokenId)
                .orElseThrow(this::invalidRefreshToken);
        verifyStoredToken(stored, userId, rawRefreshToken);
        stored.revoke(clock.instant(), null);
    }

    private TokenResponse createTokenPair(User user) {
        return createTokenPair(user, UUID.randomUUID(), clock.instant());
    }

    private TokenResponse createTokenPair(User user, UUID refreshTokenId, Instant now) {
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        String accessToken = encode(
                accessTokenEncoder,
                user.getId(),
                UUID.randomUUID(),
                ACCESS_TOKEN_TYPE,
                now,
                accessExpiresAt);
        String refreshToken = encode(
                refreshTokenEncoder,
                user.getId(),
                refreshTokenId,
                REFRESH_TOKEN_TYPE,
                now,
                refreshExpiresAt);

        refreshTokens.save(new RefreshToken(
                refreshTokenId,
                user,
                hash(refreshToken),
                refreshExpiresAt));

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                properties.refreshTokenTtl().toSeconds());
    }

    private String encode(
            JwtEncoder encoder,
            UUID userId,
            UUID tokenId,
            String tokenType,
            Instant issuedAt,
            Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.toString())
                .id(tokenId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Jwt decodeRefreshToken(String token) {
        try {
            Jwt jwt = refreshTokenDecoder.decode(token);
            if (!REFRESH_TOKEN_TYPE.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
                throw invalidRefreshToken();
            }
            return jwt;
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private void verifyStoredToken(RefreshToken stored, UUID userId, String rawToken) {
        boolean sameUser = stored.getUser().getId().equals(userId);
        boolean sameHash = MessageDigest.isEqual(
                stored.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(rawToken).getBytes(StandardCharsets.US_ASCII));
        if (!sameUser || !sameHash) {
            throw invalidRefreshToken();
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw invalidRefreshToken();
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn) {

        @Override
        public String toString() {
            return "TokenResponse[accessToken=[REDACTED], refreshToken=[REDACTED], tokenType="
                    + tokenType + ", expiresIn=" + expiresIn
                    + ", refreshExpiresIn=" + refreshExpiresIn + "]";
        }
    }
}
