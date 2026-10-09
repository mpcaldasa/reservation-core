package com.slotix.reservationcore.common;

import com.slotix.reservationcore.booking.BookingTransitionException;
import com.slotix.reservationcore.identity.InvalidCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiErrorResponse.of(
            exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = exception.getReason() == null ? "The request could not be completed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(
            ApiErrorResponse.of(status, codeForStatus(status), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, ApiErrorCode.DATA_CONFLICT,
            "The request conflicts with an existing record");
    }

    @ExceptionHandler(BookingTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleBookingTransition(BookingTransitionException exception) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_STATE_INVALID,
            exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
            "You do not have permission to perform this action");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_REQUIRED,
            "Authentication is required or the token is invalid or expired");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
                ? "The value is invalid" : error.getDefaultMessage()));

        ApiErrorResponse body = new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(),
            ApiErrorCode.VALIDATION_FAILED.name(), "One or more request fields are invalid", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST,
            "The request body, path, or query parameter is invalid");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED,
            "The HTTP method is not supported for this endpoint");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "The request content type is not supported");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        logger.error("Unhandled request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
            "The request could not be completed due to an internal error");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), code, message));
    }

    private ApiErrorCode codeForStatus(int status) {
        return switch (status) {
            case 400, 422 -> ApiErrorCode.INVALID_REQUEST;
            case 401 -> ApiErrorCode.AUTHENTICATION_REQUIRED;
            case 403 -> ApiErrorCode.ACCESS_DENIED;
            case 404 -> ApiErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ApiErrorCode.REQUEST_CONFLICT;
            case 405 -> ApiErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? ApiErrorCode.INTERNAL_ERROR : ApiErrorCode.REQUEST_REJECTED;
        };
    }
}
