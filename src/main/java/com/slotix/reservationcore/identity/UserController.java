package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final CompanyMembershipRepository companyMembershipRepository;
    private final PlatformUserRoleRepository platformUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserController(
        UserRepository userRepository,
        CompanyMembershipRepository companyMembershipRepository,
        PlatformUserRoleRepository platformUserRoleRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.companyMembershipRepository = companyMembershipRepository;
        this.platformUserRoleRepository = platformUserRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String hashedPassword = passwordEncoder.encode(request.password());

        User user = User.create(normalizedEmail, hashedPassword, request.fullName());
        User savedUser = userRepository.save(user);

        CompanyMembership membership = CompanyMembership.create(
            request.companyId(),
            savedUser.getId(),
            request.roles()
        );
        CompanyMembership savedMembership = companyMembershipRepository.save(membership);

        return ResponseEntity.ok(UserResponse.from(savedUser, savedMembership));
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
            .findByCompanyIdAndUserIdAndDeletedAtIsNull(request.companyId(), user.getId())
            .filter(CompanyMembership::isActive)
            .orElseThrow(InvalidCredentialsException::new);

        List<String> roles = new ArrayList<>(
            membership.getRoles().stream().map(Enum::name).sorted().toList()
        );

        platformUserRoleRepository.findByUserId(user.getId())
            .forEach(platformRole -> roles.add(platformRole.getRole().name()));

        String token = jwtService.generateToken(user.getId(), membership.getCompanyId(), roles);
        return ResponseEntity.ok(LoginResponse.of(token));
    }
}
