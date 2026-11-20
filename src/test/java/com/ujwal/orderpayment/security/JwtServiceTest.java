package com.ujwal.orderpayment.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void roundTripKeepsSubjectAndRole() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        Claims claims = jwt.parse(jwt.generateToken("a@b.com", "ADMIN"));
        assertEquals("a@b.com", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1_000);
        String token = jwt.generateToken("a@b.com", "USER");
        assertThrows(ExpiredJwtException.class, () -> jwt.parse(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService issuer = new JwtService("another-secret-another-secret-another-1", 60_000);
        JwtService verifier = new JwtService(SECRET, 60_000);
        String token = issuer.generateToken("a@b.com", "USER");
        assertThrows(JwtException.class, () -> verifier.parse(token));
    }
}
