package com.tripdiary.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.mock.env.MockEnvironment;

class DeploymentEnvironmentValidatorTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DeploymentEnvironmentValidator.class);

    @Test
    void acceptsCompleteStagingConfiguration() {
        assertDoesNotThrow(() -> DeploymentEnvironmentValidator.validate(validEnvironment()));
    }

    @Test
    void rejectsMissingRequiredConfiguration() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("S3_BUCKET", "");
        assertThatThrownBy(() -> DeploymentEnvironmentValidator.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_BUCKET");
    }

    @Test
    void rejectsLocalSecretsAndOpenOrLocalCors() {
        MockEnvironment localSecret = validEnvironment();
        localSecret.setProperty("JWT_ACCESS_SECRET", "trip-diary-local-access-secret-change-me");
        assertThatThrownBy(() -> DeploymentEnvironmentValidator.validate(localSecret))
                .hasMessageContaining("JWT_ACCESS_SECRET");

        MockEnvironment openCors = validEnvironment();
        openCors.setProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "*");
        assertThatThrownBy(() -> DeploymentEnvironmentValidator.validate(openCors))
                .hasMessageContaining("CORS");

        MockEnvironment anyInterfaceCors = validEnvironment();
        anyInterfaceCors.setProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "http://0.0.0.0:8081");
        assertThatThrownBy(() -> DeploymentEnvironmentValidator.validate(anyInterfaceCors))
                .hasMessageContaining("CORS");
    }

    @Test
    void stagingProfileLoadsValidatedDatabaseAndFlywaySettings() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=staging",
                "DATABASE_URL=jdbc:postgresql://db.internal:5432/trip_diary_staging",
                "DATABASE_USERNAME=staging_user",
                "DATABASE_PASSWORD=staging-database-password",
                "AWS_ACCESS_KEY_ID=staging-access-key",
                "AWS_SECRET_ACCESS_KEY=staging-secret-key",
                "AWS_REGION=ap-northeast-2",
                "S3_BUCKET=trip-diary-staging-images",
                "JWT_ACCESS_SECRET=staging-access-signing-secret-longer-than-32",
                "JWT_REFRESH_SECRET=staging-refresh-signing-secret-longer-than-32",
                "CORS_ALLOWED_ORIGIN_PATTERNS=https://staging.example.com")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://db.internal:5432/trip_diary_staging");
                    org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("validate");
                    org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getProperty("spring.flyway.enabled"))
                            .isEqualTo("true");
                });
    }

    @Test
    void localRemainsTheDefaultProfileWithDeveloperDatabaseDefaults() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getDefaultProfiles()).contains("local");
            org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/trip_diary");
        });
    }

    @Test
    void productionProfileCannotUseLocalDefaultSecret() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=production",
                "DATABASE_URL=jdbc:postgresql://db.internal:5432/trip_diary",
                "DATABASE_USERNAME=production_user",
                "DATABASE_PASSWORD=production-database-password",
                "AWS_ACCESS_KEY_ID=production-access-key",
                "AWS_SECRET_ACCESS_KEY=production-secret-key",
                "AWS_REGION=ap-northeast-2",
                "S3_BUCKET=trip-diary-production-images",
                "JWT_ACCESS_SECRET=trip-diary-local-access-secret-change-me",
                "JWT_REFRESH_SECRET=production-refresh-signing-secret-longer-than-32",
                "CORS_ALLOWED_ORIGIN_PATTERNS=https://app.example.com")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://db.internal:5432/trip_diary_staging")
                .withProperty("DATABASE_USERNAME", "staging_user")
                .withProperty("DATABASE_PASSWORD", "staging-database-password")
                .withProperty("AWS_ACCESS_KEY_ID", "staging-access-key")
                .withProperty("AWS_SECRET_ACCESS_KEY", "staging-secret-key")
                .withProperty("AWS_REGION", "ap-northeast-2")
                .withProperty("S3_BUCKET", "trip-diary-staging-images")
                .withProperty("JWT_ACCESS_SECRET", "staging-access-signing-secret-longer-than-32")
                .withProperty("JWT_REFRESH_SECRET", "staging-refresh-signing-secret-longer-than-32")
                .withProperty("CORS_ALLOWED_ORIGIN_PATTERNS", "https://staging.example.com");
    }
}
