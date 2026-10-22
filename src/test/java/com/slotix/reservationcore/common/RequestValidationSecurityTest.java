package com.slotix.reservationcore.common;

import com.slotix.reservationcore.company.CreateCompanyRequest;
import com.slotix.reservationcore.booking.CancelBookingRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidationSecurityTest {
    @Test
    void adversarialSlugsAreRejectedWithoutStackExhaustion() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                for (String slug : new String[]{"a-".repeat(5000) + "a", "a-".repeat(50000) + "!"}) {
                    assertFalse(validator.validate(new CreateCompanyRequest("Legal", "Display", slug,
                        "contact@example.test")).isEmpty());
                }
            });
            assertTrue(validator.validate(new CreateCompanyRequest("Legal", "Display", "valid-slug-12",
                "contact@example.test")).isEmpty());
            assertFalse(validator.validate(new CancelBookingRequest("x".repeat(2001))).isEmpty());
        }
    }
}
