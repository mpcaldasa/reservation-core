package com.slotix.reservationcore.common;

import java.util.Map;

public record ApiErrorResponse(int status, String code, String error, Map<String, String> fields) {
    public ApiErrorResponse {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static ApiErrorResponse of(int status, ApiErrorCode code, String message) {
        return new ApiErrorResponse(status, code.name(), message, Map.of());
    }
}
