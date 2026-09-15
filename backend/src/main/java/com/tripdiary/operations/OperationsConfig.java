package com.tripdiary.operations;

import com.tripdiary.image.ImageCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({RateLimitProperties.class, ImageCleanupProperties.class})
public class OperationsConfig implements WebMvcConfigurer {
    private final RateLimitInterceptor limiter;
    public OperationsConfig(RateLimitInterceptor limiter) { this.limiter = limiter; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(limiter); }
}
