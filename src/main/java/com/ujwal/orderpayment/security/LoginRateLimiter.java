package com.ujwal.orderpayment.security;

import com.ujwal.orderpayment.common.TooManyRequestsException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter: at most N login attempts per client IP per minute, counted in Redis
 * (INCR + EXPIRE) so the limit holds across multiple app instances. Fails open: if Redis is
 * down, logins still work rather than locking everyone out.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final int limit;

    public LoginRateLimiter(StringRedisTemplate redis,
                            @Value("${app.rate-limit.login-per-minute:10}") int limit) {
        this.redis = redis;
        this.limit = limit;
    }

    public void check(String clientIp) {
        Long attempts;
        try {
            String key = "rl:login:" + clientIp;
            attempts = redis.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable, allowing request", e);
            return;
        }
        if (attempts != null && attempts > limit) {
            throw new TooManyRequestsException("Too many login attempts, try again in a minute");
        }
    }
}
