package com.ujwal.orderpayment.order;

import com.ujwal.orderpayment.common.InsufficientStockException;
import com.ujwal.orderpayment.common.NotFoundException;
import com.ujwal.orderpayment.order.OrderDtos.OrderResponse;
import com.ujwal.orderpayment.order.OrderDtos.PlaceOrderRequest;
import com.ujwal.orderpayment.product.Product;
import com.ujwal.orderpayment.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One attempt at placing an order, in a single transaction. Kept in its own bean so that
 * OrderService can retry it: a @Transactional method called from within the same class
 * would bypass the Spring proxy.
 *
 * Stock is decremented on managed Product entities. Product has @Version, so if another
 * transaction changed the same row first, the UPDATE matches 0 rows and the commit fails
 * with an optimistic-locking exception instead of silently overselling.
 */
@Component
public class OrderTx {

    private final ProductRepository products;
    private final OrderRepository orders;

    public OrderTx(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        // Merge duplicate lines and process products in a stable order.
        Map<Long, Integer> quantities = new TreeMap<>();
        request.items().forEach(i -> quantities.merge(i.productId(), i.quantity(), Integer::sum));

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long productId = line.getKey();
            int qty = line.getValue();
            Product product = products.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
            if (product.getStock() < qty) {
                throw new InsufficientStockException("Insufficient stock for product " + productId
                        + " (requested " + qty + ", available " + product.getStock() + ")");
            }
            product.setStock(product.getStock() - qty);
            order.getItems().add(new OrderItem(order, productId, qty, product.getPrice()));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setTotalAmount(total);
        orders.save(order);
        return OrderResponse.from(order);
    }
}
