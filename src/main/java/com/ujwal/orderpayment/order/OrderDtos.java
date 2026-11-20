package com.ujwal.orderpayment.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(100) Integer quantity) {}

    public record PlaceOrderRequest(@NotEmpty @Valid List<OrderItemRequest> items) {}

    public record OrderItemResponse(Long productId, Integer quantity, BigDecimal unitPrice) {}

    public record OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount,
                                Instant createdAt, List<OrderItemResponse> items) {

        /** Call inside a transaction: reads the lazy items collection. */
        public static OrderResponse from(Order o) {
            List<OrderItemResponse> items = o.getItems().stream()
                    .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                    .toList();
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCreatedAt(), items);
        }
    }
}
