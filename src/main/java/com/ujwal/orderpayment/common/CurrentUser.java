package com.ujwal.orderpayment.common;

import com.ujwal.orderpayment.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the database id of the authenticated user (the JWT subject is the email). */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"))
                .getId();
    }
}
