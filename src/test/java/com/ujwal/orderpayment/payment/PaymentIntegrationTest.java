package com.ujwal.orderpayment.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    private long placeOrderFor(String userToken, long productId) throws Exception {
        String res = mvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(productId, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asLong();
    }

    private ResultActions pay(String userToken, long orderId, String key) throws Exception {
        return mvc.perform(post("/api/orders/" + orderId + "/pay")
                .header("Authorization", bearer(userToken))
                .header("Idempotency-Key", key));
    }

    private long newProduct() throws Exception {
        return createProduct(adminToken(), "Pay-" + UUID.randomUUID(), "50.00", 20);
    }

    @Test
    void retryWithSameKeyReturnsSamePaymentAndOrderIsPaidOnce() throws Exception {
        String user = registerUser();
        long orderId = placeOrderFor(user, newProduct());
        String key = UUID.randomUUID().toString();

        String first = pay(user, orderId, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        String second = pay(user, orderId, key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long firstId = mapper.readTree(first).get("id").asLong();
        long secondId = mapper.readTree(second).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId, "retry must replay the original payment");

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void paidOrderCannotBePaidAgainWithAnotherKey() throws Exception {
        String user = registerUser();
        long orderId = placeOrderFor(user, newProduct());

        pay(user, orderId, UUID.randomUUID().toString()).andExpect(status().isOk());
        pay(user, orderId, UUID.randomUUID().toString()).andExpect(status().isConflict());
    }

    @Test
    void sameKeyCannotBeReusedForADifferentOrder() throws Exception {
        String user = registerUser();
        long productId = newProduct();
        long order1 = placeOrderFor(user, productId);
        long order2 = placeOrderFor(user, productId);
        String key = UUID.randomUUID().toString();

        pay(user, order1, key).andExpect(status().isOk());
        pay(user, order2, key).andExpect(status().isConflict());
    }

    @Test
    void cannotPayAnotherUsersOrder() throws Exception {
        String owner = registerUser();
        long orderId = placeOrderFor(owner, newProduct());

        pay(registerUser(), orderId, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        String user = registerUser();
        long orderId = placeOrderFor(user, newProduct());

        mvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest());
    }
}
