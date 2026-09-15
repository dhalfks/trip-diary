package com.tripdiary.operations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, @DefaultValue("20000") int maxKeys,
                                  Rule signup, Rule login, Rule uploadUrl, Rule complete, Rule diary, Rule account) {
    public RateLimitProperties {
        if (maxKeys < 1 || maxKeys > 1000000) throw new IllegalArgumentException("rate-limit.max-keys must be 1..1000000");
        signup = signup == null ? new Rule(10, Duration.ofMinutes(10)) : signup;
        login = login == null ? new Rule(30, Duration.ofMinutes(1)) : login;
        uploadUrl = uploadUrl == null ? new Rule(60, Duration.ofMinutes(1)) : uploadUrl;
        complete = complete == null ? new Rule(120, Duration.ofMinutes(1)) : complete;
        diary = diary == null ? new Rule(10, Duration.ofMinutes(1)) : diary;
        account = account == null ? new Rule(5, Duration.ofMinutes(10)) : account;
    }
    public record Rule(int limit, Duration window) {
        public Rule {
            if (limit < 1 || window == null || window.compareTo(Duration.ofSeconds(1)) < 0 || window.compareTo(Duration.ofDays(1)) > 0)
                throw new IllegalArgumentException("rate-limit rules require a positive limit and a window between 1s and 1d");
        }
    }
}
