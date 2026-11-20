package com.ujwal.orderpayment.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProductIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> productBody(String name, String price, int stock) {
        return Map.of("name", name, "description", "d", "price", new BigDecimal(price), "stock", stock);
    }

    @Test
    void adminCreatesAndAnyoneCanBrowse() throws Exception {
        String admin = adminToken();
        String unique = "Keyboard-" + UUID.randomUUID();
        long id = createProduct(admin, unique, "2499.00", 25);

        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(unique))
                .andExpect(jsonPath("$.stock").value(25));

        mvc.perform(get("/api/products").param("q", unique.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(unique));
    }

    @Test
    void searchIsPaginated() throws Exception {
        String admin = adminToken();
        String prefix = "Page-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            createProduct(admin, prefix + "-" + i, "10.00", 1);
        }
        mvc.perform(get("/api/products").param("q", prefix).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void onlyAdminsCanWrite() throws Exception {
        String payload = json(productBody("Nope", "1.00", 1));
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/products").header("Authorization", bearer(registerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateAndDelete() throws Exception {
        String admin = adminToken();
        long id = createProduct(admin, "Updatable-" + UUID.randomUUID(), "5.00", 3);

        mvc.perform(put("/api/admin/products/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("Renamed", "7.50", 9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.stock").value(9));

        mvc.perform(delete("/api/admin/products/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidProductReturns400() throws Exception {
        mvc.perform(post("/api/admin/products").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productBody("Bad", "1.00", -5))))
                .andExpect(status().isBadRequest());
    }
}
