package com.ujwal.orderpayment.payment;

import com.ujwal.orderpayment.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService payments;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService payments, CurrentUser currentUser) {
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/orders/{orderId}/pay")
    public PaymentResponse pay(@PathVariable Long orderId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                               Authentication auth) {
        return payments.pay(currentUser.id(auth), orderId, idempotencyKey);
    }
}
