package com.tripdiary.auth;

import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;

public class RefreshTokenReuseException extends BusinessException {

    public RefreshTokenReuseException() {
        super(ErrorCode.REFRESH_TOKEN_REUSED);
    }
}
