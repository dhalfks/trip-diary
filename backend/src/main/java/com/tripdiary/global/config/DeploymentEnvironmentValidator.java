package com.tripdiary.global.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Fails deployment profiles before serving traffic when required runtime configuration is unsafe. */
@Configuration
@Profile({"staging", "production"})
public class DeploymentEnvironmentValidator {
    private static final List<String> REQUIRED = List.of(
            "DATABASE_URL",
            "DATABASE_USERNAME",
            "DATABASE_PASSWORD",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_REGION",
            "S3_BUCKET",
            "JWT_ACCESS_SECRET",
            "JWT_REFRESH_SECRET",
            "CORS_ALLOWED_ORIGIN_PATTERNS");
    private static final List<String> UNSAFE_MARKERS = List.of("change-me", "replace-with", "trip-diary-local-");

    public DeploymentEnvironmentValidator(Environment environment) {
        validate(environment);
    }

    static void validate(Environment environment) {
        for (String name : REQUIRED) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required deployment environment variable is missing: " + name);
            }
            String normalized = value.toLowerCase();
            if (UNSAFE_MARKERS.stream().anyMatch(normalized::contains)) {
                throw new IllegalStateException("Placeholder or local value is not allowed for: " + name);
            }
        }
        String origins = environment.getRequiredProperty("CORS_ALLOWED_ORIGIN_PATTERNS");
        if (List.of(origins.split(",")).stream().map(String::trim).anyMatch("*"::equals)
                || origins.contains("localhost") || origins.contains("127.0.0.1") || origins.contains("0.0.0.0")) {
            throw new IllegalStateException("Deployment CORS origins must be explicit non-local origins");
        }
    }
}
