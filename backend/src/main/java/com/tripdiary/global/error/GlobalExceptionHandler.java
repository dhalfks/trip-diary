package com.tripdiary.global.error;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.tripdiary.global.web.RequestTraceIdFilter;
import com.tripdiary.operations.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, null, request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, null, request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(ErrorCode.INVALID_REQUEST, "JSON 요청 형식을 확인해 주세요.", request, List.of());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(
            Exception exception,
            HttpServletRequest request) {
        return response(ErrorCode.INVALID_REQUEST, null, request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, null, request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, null, request, List.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        return response(exception.getErrorCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException exception, HttpServletRequest request) {
        var result = response(ErrorCode.RATE_LIMIT_EXCEEDED, null, request, List.of());
        return ResponseEntity.status(result.getStatusCode()).header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(result.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        // Exception messages/causes may include SQL values, request bodies or signed storage URLs.
        log.error("Unhandled exception type={}", exception.getClass().getSimpleName());
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null, request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldViolation> fieldErrors) {
        HttpStatus status = errorCode.status();
        request.setAttribute(RequestTraceIdFilter.ERROR_CODE_ATTRIBUTE, errorCode.name());
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode.name(),
                message == null ? errorCode.defaultMessage() : message,
                request.getRequestURI(),
                traceId(request),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String traceId(HttpServletRequest request) {
        Object requestTraceId = request.getAttribute(RequestTraceIdFilter.TRACE_ID_ATTRIBUTE);
        return requestTraceId == null ? MDC.get(RequestTraceIdFilter.MDC_KEY) : requestTraceId.toString();
    }
}
