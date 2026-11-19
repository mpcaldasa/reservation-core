package com.slotix.reservationcore.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IdentityTokenCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] CONTEXT = "slotix:identity-email:v1".getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;

    public IdentityTokenCipher(@Value("${app.identity.delivery-encryption-key:}") String encodedKey,
                                @Value("${app.notification.email.enabled:false}") boolean emailEnabled) {
        if (encodedKey.isBlank() && !emailEnabled) {
            key = null;
            return;
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Identity delivery encryption requires a Base64-encoded 32-byte key");
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Identity delivery encryption requires a Base64-encoded 32-byte key");
        }
        key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String token) {
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, nonce, token.getBytes(StandardCharsets.UTF_8));
        return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
            .put(nonce).put(encrypted).array());
    }

    public String decrypt(String envelope) {
        if (!envelope.startsWith("v1:")) throw new IllegalArgumentException("Unsupported encrypted identity token");
        byte[] value = Base64.getDecoder().decode(envelope.substring(3));
        if (value.length < 28) throw new IllegalArgumentException("Invalid encrypted identity token");
        ByteBuffer buffer = ByteBuffer.wrap(value);
        byte[] nonce = new byte[12];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return new String(crypt(Cipher.DECRYPT_MODE, nonce, encrypted), StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] value) {
        if (key == null) throw new IllegalStateException("Identity delivery encryption is not configured");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(CONTEXT);
            return cipher.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Identity token encryption operation failed");
        }
    }
}
