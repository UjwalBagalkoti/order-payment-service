package com.ujwal.orderpayment.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class LoginRateLimitTest extends AbstractIntegrationTest {

    private int loginStatus(String ip) throws Exception {
        String body = json(Map.of("email", "nobody@example.com", "password", "whatever123"));
        return mvc.perform(post("/api/auth/login").with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void eleventhAttemptWithinAMinuteIsRejected() throws Exception {
        String ip = randomIp();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            statuses.add(loginStatus(ip));
        }
        assertEquals(401, statuses.get(0).intValue());
        assertEquals(401, statuses.get(9).intValue());
        assertEquals(429, statuses.get(10).intValue());
        assertEquals(429, statuses.get(11).intValue());

        // A different client is unaffected.
        mvc.perform(post("/api/auth/login").with(fromIp(randomIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.com", "password", "whatever123"))))
                .andExpect(status().isUnauthorized());
    }
}
