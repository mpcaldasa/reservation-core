package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class UserProvisioningService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserProvisioningService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public User create(NewUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailAndDeletedAtIsNull(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.USER_ALREADY_EXISTS,
                "User already exists; link the existing user instead");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.PASSWORD_BYTE_LIMIT_EXCEEDED,
                "Password must not exceed 72 UTF-8 bytes");
        }
        return users.saveAndFlush(User.create(email, passwords.encode(request.password()), request.fullName().trim()));
    }
}
