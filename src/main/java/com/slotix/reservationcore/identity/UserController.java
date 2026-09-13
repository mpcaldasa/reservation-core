package com.slotix.reservationcore.identity;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());

        User user = User.create(
            request.companyId(),
            request.email(),
            hashedPassword,
            request.fullName(),
            request.role()
        );

        User saved = userRepository.save(user);
        return ResponseEntity.ok(UserResponse.from(saved));
    }
}
