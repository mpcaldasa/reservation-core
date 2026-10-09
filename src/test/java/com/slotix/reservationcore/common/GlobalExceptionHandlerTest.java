package com.slotix.reservationcore.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionReturnsStableCodeAndStatus() {
        var response = handler.handleApiException(new ApiException(HttpStatus.CONFLICT,
            ApiErrorCode.POLICY_ASSIGNMENT_OVERLAP,
            "The requested period overlaps an existing policy assignment"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().status());
        assertEquals("POLICY_ASSIGNMENT_OVERLAP", response.getBody().code());
        assertTrue(response.getBody().fields().isEmpty());
    }

    @Test
    void legacyStatusExceptionUsesStableStatusFallbackCode() {
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND,
            "The resource does not exist"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().code());
        assertEquals("The resource does not exist", response.getBody().error());
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() {
        var response = handler.handleUnexpected(new IllegalStateException("private implementation detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertFalse(response.getBody().error().contains("private implementation detail"));
    }
}
