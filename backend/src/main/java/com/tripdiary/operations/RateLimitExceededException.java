package com.tripdiary.operations;

import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;

public class RateLimitExceededException extends BusinessException {
    private final long retryAfterSeconds;
    public RateLimitExceededException(long retryAfterSeconds) { super(ErrorCode.RATE_LIMIT_EXCEEDED); this.retryAfterSeconds = retryAfterSeconds; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
