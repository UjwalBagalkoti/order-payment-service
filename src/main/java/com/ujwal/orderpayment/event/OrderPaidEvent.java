package com.ujwal.orderpayment.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPaidEvent(Long orderId, Long userId, BigDecimal amount, Instant paidAt) {
}
