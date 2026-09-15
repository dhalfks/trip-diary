package com.tripdiary.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    DIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "생성된 다이어리를 찾을 수 없습니다."),
    DIARY_COVER_INVALID(HttpStatus.BAD_REQUEST, "이 여행의 업로드 완료된 사진을 대표 이미지로 선택해 주세요."),
    DIARY_LIMIT_REACHED(HttpStatus.CONFLICT, "여행당 다이어리는 최대 5개까지 보관할 수 있습니다. 기존 결과를 삭제한 후 다시 생성해 주세요."),
    DIARY_TOO_LARGE(HttpStatus.BAD_REQUEST, "생성 가능한 다이어리 페이지 수는 최대 500페이지입니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_UPLOAD_NOT_READY(HttpStatus.CONFLICT, "업로드된 이미지를 확인할 수 없습니다."),
    IMAGE_UPLOAD_MISMATCH(HttpStatus.CONFLICT, "업로드된 파일의 크기 또는 형식이 등록 정보와 다릅니다."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소를 일시적으로 사용할 수 없습니다."),
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
    TRIP_DAY_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 날짜를 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    INVALID_ITINERARY_TIME(HttpStatus.BAD_REQUEST, "일정 시간이 올바르지 않습니다."),
    INVALID_ITINERARY_ORDER(HttpStatus.BAD_REQUEST, "일정 정렬 정보가 올바르지 않습니다."),
    PLACE_TRIP_MISMATCH(HttpStatus.BAD_REQUEST, "해당 여행에 속한 장소만 일정에 연결할 수 있습니다."),
    INVALID_PLACE_COORDINATES(HttpStatus.BAD_REQUEST, "위도와 경도는 함께 입력해야 합니다."),
    PLACE_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "장소 검색 서비스를 일시적으로 사용할 수 없습니다."),
    DIARY_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 기록을 찾을 수 없습니다."),
    DIARY_ENTRY_LINK_MISMATCH(HttpStatus.BAD_REQUEST, "같은 여행 날짜의 일정과 장소만 기록에 연결할 수 있습니다."),
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
