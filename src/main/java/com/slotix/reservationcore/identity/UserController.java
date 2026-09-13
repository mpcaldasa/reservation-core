package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final CompanyMembershipRepository companyMembershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserController(
        UserRepository userRepository,
        CompanyMembershipRepository companyMembershipRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.companyMembershipRepository = companyMembershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
            .orElseThrow(InvalidCredentialsException::new);

        if (!"ACTIVE".equals(user.getStatus())
            || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        CompanyMembership membership = companyMembershipRepository
            .findByTenantIdAndUserIdAndDeletedAtIsNull(request.companyId(), user.getId())
            .filter(CompanyMembership::isActive)
            .orElseThrow(InvalidCredentialsException::new);

        List<String> roles = membership.getRoles()
            .stream()
            .map(Enum::name)
            .sorted()
            .toList();

        String token = jwtService.generateToken(user.getId(), membership.getTenantId(), roles);
        return ResponseEntity.ok(LoginResponse.of(token));
    }
}
