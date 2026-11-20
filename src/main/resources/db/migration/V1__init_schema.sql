CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL,
    description TEXT,
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    stock       INTEGER        NOT NULL CHECK (stock >= 0),
    version     BIGINT         NOT NULL DEFAULT 0,  -- optimistic locking (JPA @Version)
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_name ON products (lower(name));

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users (id),
    status       VARCHAR(20)    NOT NULL,           -- PENDING, PAID, CANCELLED, FAILED
    total_amount NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user ON orders (user_id, created_at DESC);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT         NOT NULL REFERENCES orders (id),
    idempotency_key VARCHAR(64)    NOT NULL UNIQUE, -- same key => same result, never double-charge
    amount          NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,        -- INITIATED, SUCCESS, FAILED
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
