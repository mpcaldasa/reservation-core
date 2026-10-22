package com.slotix.reservationcore.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityTokenCipherTest {
    private final IdentityTokenCipher cipher = new IdentityTokenCipher(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", true);

    @Test
    void usesRandomNoncesAndRejectsTamperingAndWrongKeys() {
        String encrypted = cipher.encrypt("private-token");
        assertFalse(encrypted.contains("private-token"));
        assertNotEquals(encrypted, cipher.encrypt("private-token"));
        assertEquals("private-token", cipher.decrypt(encrypted));
        assertThrows(RuntimeException.class, () -> cipher.decrypt(encrypted.substring(0, 12) + "AAAA"));
        var other = new IdentityTokenCipher("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=", true);
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
        assertThrows(IllegalArgumentException.class, () -> new IdentityTokenCipher("", true));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("legacy-plaintext"));
    }
}
