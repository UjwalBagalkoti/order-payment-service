package com.ujwal.orderpayment.order;

import com.ujwal.orderpayment.common.CurrentUser;
import com.ujwal.orderpayment.order.OrderDtos.OrderResponse;
import com.ujwal.orderpayment.order.OrderDtos.PlaceOrderRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final CurrentUser currentUser;

    public OrderController(OrderService orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request, Authentication auth) {
        return orders.place(currentUser.id(auth), request);
    }

    @GetMapping
    public Page<OrderResponse> mine(Authentication auth,
                                    @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
                                    Pageable pageable) {
        return orders.mine(currentUser.id(auth), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Authentication auth) {
        return orders.get(currentUser.id(auth), id);
    }
}
