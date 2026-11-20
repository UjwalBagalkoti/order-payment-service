package com.ujwal.orderpayment.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ujwal.orderpayment.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OrderIntegrationTest extends AbstractIntegrationTest {

    private ResultActions placeOrder(String userToken, long productId, int qty) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", bearer(userToken))
                .contentType(MediaType.APPLICATION_JSON).content(orderBody(productId, qty)));
    }

    @Test
    void placingAnOrderReducesStockAndOversellIsRejected() throws Exception {
        long productId = createProduct(adminToken(), "Order-" + UUID.randomUUID(), "10.00", 5);
        String user = registerUser();

        placeOrder(user, productId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20.0))
                .andExpect(jsonPath("$.items.length()").value(1));

        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.stock").value(3));

        placeOrder(user, productId, 4).andExpect(status().isConflict());

        mvc.perform(get("/api/products/" + productId)).andExpect(jsonPath("$.stock").value(3));
    }

    @Test
    void usersOnlySeeTheirOwnOrders() throws Exception {
        long productId = createProduct(adminToken(), "Own-" + UUID.randomUUID(), "3.00", 10);
        String owner = registerUser();
        String stranger = registerUser();

        String res = placeOrder(owner, productId, 1).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = mapper.readTree(res).get("id").asLong();

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/orders").header("Authorization", bearer(stranger)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        placeOrder(registerUser(), 999_999_999L, 1).andExpect(status().isNotFound());
    }

    @Test
    void emptyOrderReturns400() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", bearer(registerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersRequireAuthentication() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(orderBody(1, 1)))
                .andExpect(status().isUnauthorized());
    }
}
