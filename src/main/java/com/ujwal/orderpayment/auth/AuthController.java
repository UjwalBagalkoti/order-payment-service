package com.ujwal.orderpayment.auth;

import com.ujwal.orderpayment.auth.AuthDtos.AuthResponse;
import com.ujwal.orderpayment.auth.AuthDtos.LoginRequest;
import com.ujwal.orderpayment.auth.AuthDtos.RegisterRequest;
import com.ujwal.orderpayment.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check(http.getRemoteAddr());
        return authService.login(request);
    }
}
