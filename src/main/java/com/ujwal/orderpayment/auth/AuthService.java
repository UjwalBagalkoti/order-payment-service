package com.ujwal.orderpayment.auth;

import com.ujwal.orderpayment.auth.AuthDtos.AuthResponse;
import com.ujwal.orderpayment.auth.AuthDtos.LoginRequest;
import com.ujwal.orderpayment.auth.AuthDtos.RegisterRequest;
import com.ujwal.orderpayment.common.ConflictException;
import com.ujwal.orderpayment.security.JwtService;
import com.ujwal.orderpayment.user.Role;
import com.ujwal.orderpayment.user.User;
import com.ujwal.orderpayment.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.USER);
        users.save(user);
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        String token = jwt.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, "Bearer", jwt.getExpirationMs());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
