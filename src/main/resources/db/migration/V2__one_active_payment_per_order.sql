-- At most one in-flight or successful payment per order, enforced by the database.
-- FAILED attempts are excluded, so a customer can retry after a failure with a new Idempotency-Key.
CREATE UNIQUE INDEX uq_payments_active_order
    ON payments (order_id)
    WHERE status IN ('INITIATED', 'SUCCESS');
