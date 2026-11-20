package com.ujwal.orderpayment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujwal.orderpayment.user.Role;
import com.ujwal.orderpayment.user.User;
import com.ujwal.orderpayment.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base class for integration tests. Keep every subclass free of extra @MockBean/@TestPropertySource
 * so they all reuse the same cached context and containers.
 */
@SpringBootTest(properties = "app.events.type=log") // no Kafka broker in tests
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "S3cretPass!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;

    protected String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String orderBody(long productId, int quantity) throws Exception {
        return json(Map.of("items", List.of(Map.of("productId", productId, "quantity", quantity))));
    }

    /** Gives a request its own client IP so the per-IP login rate limiter never couples tests together. */
    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(1, 255) + "." + r.nextInt(1, 255) + "." + r.nextInt(1, 255);
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers a fresh USER and returns its JWT. */
    protected String registerUser() throws Exception {
        Map<String, String> body = Map.of("email", uniqueEmail("user"), "password", PASSWORD);
        String res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }

    /** Inserts a fresh ADMIN directly (registration only creates USERs) and returns its JWT. */
    protected String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        users.save(admin);

        Map<String, String> body = Map.of("email", email, "password", PASSWORD);
        String res = mvc.perform(post("/api/auth/login").with(fromIp(randomIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }

    protected long createProduct(String adminToken, String name, String price, int stock) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name, "description", "test product",
                "price", new BigDecimal(price), "stock", stock);
        String res = mvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asLong();
    }
}
