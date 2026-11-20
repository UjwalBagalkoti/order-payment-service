# Order & Payment Processing Service

[![CI](https://github.com/UjwalBagalkoti/order-payment-service/actions/workflows/ci.yml/badge.svg)](https://github.com/UjwalBagalkoti/order-payment-service/actions/workflows/ci.yml)

A Spring Boot backend for placing orders and paying for them safely under concurrency:
no overselling, no double charges, cached reads, and asynchronous order events.

```
                  ┌────────────┐   JWT    ┌───────────────────────────────┐
   client ───────▶│ Spring     │─────────▶│ Orders / Payments / Catalog   │
                  │ Security   │          └──────┬───────────┬────────────┘
                  └────────────┘                 │           │
                                   PostgreSQL ◀──┘           └──▶ Redis (cache, rate limit)
                                   (Flyway)                       
                                       │ payment succeeded
                                       ▼
                                 Kafka  orders.paid ──▶ notification consumer
```

## Live demo
| | |
|---|---|
| Demo UI (Vercel) | https://order-payment-service-rho.vercel.app |
| API (Render) | https://order-payment-service.onrender.com |
| Swagger UI | https://order-payment-service.onrender.com/swagger-ui.html |

The API runs on a free Render instance that sleeps when idle, so the first request can take about a minute.
In the demo UI you can place an order, pay it, retry the same payment key (no second charge), and fire
10 simultaneous payments to see the database accept exactly one.

## Stack
Java 17 · Spring Boot 3.3 · Spring Security + JWT · PostgreSQL + Spring Data JPA · Flyway ·
Redis · Kafka · Docker · GitHub Actions · JUnit 5 + Testcontainers · OpenAPI (Swagger UI)

## Run it
```bash
docker compose up --build
# API:        http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Admin user: admin@example.com / ChangeMe123!   (override with ADMIN_EMAIL / ADMIN_PASSWORD)

mvn verify    # unit + integration tests; integration tests need Docker (Testcontainers)
```

## API
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | public | creates a `USER`, returns a JWT |
| POST | `/api/auth/login` | public | rate limited: 10 attempts / minute / IP |
| GET | `/api/users/me` | JWT | |
| GET | `/api/products?q=&page=&size=` | public | search + pagination |
| GET | `/api/products/{id}` | public | cached in Redis |
| POST / PUT / DELETE | `/api/admin/products[/{id}]` | `ADMIN` | |
| POST | `/api/orders` | JWT | decrements stock atomically |
| GET | `/api/orders`, `/api/orders/{id}` | JWT | own orders only |
| POST | `/api/orders/{id}/pay` | JWT | requires `Idempotency-Key` header |

## Design decisions
**No overselling: optimistic locking + retry.** `Product` has a `@Version` column. Placing an order
decrements stock inside one transaction; if another order changed the row first, the UPDATE matches
zero rows and the commit fails. `OrderService` retries with jittered backoff (each attempt in a fresh
transaction, which is why the transactional part lives in a separate bean, `OrderTx`). Optimistic
locking fits here because conflicts are occasional, and it never holds row locks while waiting.
`OrderConcurrencyTest` fires 100 threads at a product with 10 units and asserts stock never goes
negative and matches the number of persisted orders.

**No double charges: idempotency keys.** `POST /pay` requires an `Idempotency-Key`. The same key always
returns the same payment (replay). Concurrent duplicates are settled by the database, not application
code: `idempotency_key` is UNIQUE, and a partial unique index allows only one `INITIATED`/`SUCCESS`
payment per order (failed attempts can be retried with a new key). The gateway call happens outside any
DB transaction so a slow provider never pins a connection.

**Caching.** Single-product reads are cached in Redis (10 min TTL) and evicted on update, delete and
order placement. Cache errors are logged and fall through to the database, so Redis going down degrades
performance instead of availability. `CACHE_ENABLED=false` swaps in a no-op cache for before/after tests.

**Rate limiting.** Fixed-window counter in Redis (`INCR` + `EXPIRE`) so the limit is shared across app
instances. It fails open if Redis is unavailable.

**Events.** A successful payment publishes `OrderPaid` to Kafka (`orders.paid`), keyed by order id;
a consumer simulates sending a confirmation. `EVENTS_TYPE=log` replaces Kafka with a logging publisher.

## Load testing
```bash
k6 run loadtest/products.js     # run once with CACHE_ENABLED=true and once with CACHE_ENABLED=false
```
Results from my machine (fill in after running):

| Cache | p50 | p95 | requests/s |
|---|---|---|---|
| off | | | |
| on | | | |

## Demo frontend (`frontend/`)
A dependency-free static page (HTML/CSS/JS, no build step) that talks to the API. It is deployed on Vercel
(the root `vercel.json` points Vercel at the `frontend` folder, so no Root Directory setting is needed). The API base URL lives in `frontend/config.js` and can be overridden
per visit with `?api=https://your-api.example.com`.

## Deployment
| Part | Where | Notes |
|---|---|---|
| API | Render (Docker, free) | built from the `Dockerfile`; `EVENTS_TYPE=log` because Render has no managed Kafka |
| Cache | Render Key Value (free) | `REDIS_HOST`, `REDIS_PORT` |
| Database | Postgres (Neon free tier) | `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`, plus `DB_USER`, `DB_PASSWORD` |
| Frontend | Vercel | static site from `frontend/` |

Environment variables for the API: `JWT_SECRET` (32+ chars), `ADMIN_EMAIL` / `ADMIN_PASSWORD` (bootstraps the
first admin), `CORS_ORIGINS` (comma separated; the Vercel domain), `JAVA_TOOL_OPTIONS=-Xmx320m` for the 512 MB free tier.
`render.yaml` is an alternative Blueprint that provisions Postgres and Redis on Render as well; note that Render
allows only one free Postgres per account.

## Known limitations (and what I would do next)
- **Event publishing is a dual write** (commit, then publish): a crash in between loses the event.
  The fix is the transactional outbox pattern.
- **Fixed-window rate limiting** allows bursts at window edges; a sliding window or token bucket is smoother.
- **Payments use a mock gateway** (`app.payment.mock-failure-rate` simulates declines).
- **Cache eviction can race with an in-flight read** and briefly re-cache stale stock; the TTL bounds it.
- Stock is not returned if an order is abandoned; a scheduled job to cancel stale `PENDING` orders is the next step.
- Behind a reverse proxy, the rate limiter should use the forwarded client IP, not the socket address.
