package com.tripdiary.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh Token 재사용이 감지되어 모든 세션을 종료했습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_TRIP_PERIOD(HttpStatus.BAD_REQUEST, "여행 기간이 올바르지 않습니다."),
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "유효한 IANA 타임존을 입력해 주세요."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    BUSINESS_ERROR(HttpStatus.CONFLICT, "요청을 현재 상태에서 처리할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
