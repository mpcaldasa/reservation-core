package com.slotix.reservationcore.identity;

public record LoginResponse(
    String token,
    String tokenType
) {
    public static LoginResponse of(String token) {
        return new LoginResponse(token, "Bearer");
    }
}
