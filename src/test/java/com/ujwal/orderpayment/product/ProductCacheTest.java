package com.ujwal.orderpayment.product;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ProductCacheTest extends AbstractIntegrationTest {

    @Autowired ProductRepository products;

    @Test
    void readsComeFromRedisUntilTheProductIsUpdated() throws Exception {
        String admin = adminToken();
        long id = createProduct(admin, "Cached-" + UUID.randomUUID(), "10.00", 5);

        // First read populates the cache.
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.stock").value(5));

        // Change the row behind the service's back: a cached read must NOT see it.
        Product row = products.findById(id).orElseThrow();
        row.setStock(99);
        products.save(row);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.stock").value(5));

        // Updating through the API evicts the entry, so the next read hits the database.
        Map<String, Object> update = Map.of("name", "Cached-updated", "description", "d",
                "price", new BigDecimal("10.00"), "stock", 7);
        mvc.perform(put("/api/admin/products/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void placingAnOrderEvictsTheCachedStock() throws Exception {
        long id = createProduct(adminToken(), "Evict-" + UUID.randomUUID(), "10.00", 5);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.stock").value(5)); // cached

        mvc.perform(post("/api/orders").header("Authorization", bearer(registerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(id, 2)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.stock").value(3));
    }
}
