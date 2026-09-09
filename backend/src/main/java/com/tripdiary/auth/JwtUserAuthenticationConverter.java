package com.tripdiary.auth;

import java.util.List;
import java.util.UUID;

import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtUserAuthenticationConverter
        implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    private final UserRepository users;

    public JwtUserAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw invalidToken();
        }

        User user = users.findById(userId).filter(User::isActive).orElseThrow(this::invalidToken);
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, userId.toString());
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
