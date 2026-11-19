package com.slotix.reservationcore.identity;

public record PasswordResetResponse(String message) {
    public static PasswordResetResponse requested() {
        return new PasswordResetResponse("If an active account exists for this email, reset instructions will be sent.");
    }

    public static PasswordResetResponse completed() {
        return new PasswordResetResponse("The password has been reset. Sign in with the new password.");
    }
}
