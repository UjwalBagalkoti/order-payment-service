package com.ujwal.orderpayment.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserResponse(Long id, String email, Role role) {}

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        User u = users.findByEmail(authentication.getName()).orElseThrow();
        return new UserResponse(u.getId(), u.getEmail(), u.getRole());
    }
}
