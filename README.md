# Order & Payment Processing Service

Spring Boot 3 / Java 17 backend for placing orders and paying for them safely under concurrency.
Built to demonstrate the things backend interviews actually ask about: auth, transactions,
optimistic locking, idempotency, caching, async messaging, CI/CD.

## Stack
Java 17 · Spring Boot 3.3 · Spring Security + JWT · PostgreSQL · Spring Data JPA · Flyway ·
Redis (Week 4) · Kafka/RabbitMQ (Week 5) · Docker · GitHub Actions · JUnit 5 · Testcontainers · OpenAPI

## Run locally
```bash
docker compose up --build          # app on :8080, Postgres on :5432
# Swagger UI: http://localhost:8080/swagger-ui.html
mvn verify                         # unit + integration tests (needs Docker for Testcontainers)
```

## API (current)
| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| GET | `/api/users/me` | Bearer JWT |

## Roadmap (what to build next, in order)
| Week | Build | Interview topic it covers |
|---|---|---|
| 1 | **Done in this scaffold:** auth, JWT, RBAC, Flyway schema, error handling, tests, Docker, CI | Spring Security, stateless auth |
| 2 | Product catalog: CRUD (admin), search + pagination, `@Version` on `Product` | REST design, JPA, optimistic locking |
| 3 | Order placement: `@Transactional`, stock decrement, retry on `OptimisticLockException`; concurrency test with `ExecutorService` (100 threads, stock 10 → exactly 10 succeed) | Transactions, isolation, race conditions |
| 4 | Payments: `Idempotency-Key` header, unique constraint, replay returns the original result; mock payment gateway | Idempotency, exactly-once effects |
| 5 | Redis cache for product reads + rate limiting on login; Kafka/RabbitMQ event `OrderPaid` → notification consumer | Caching, async, eventual consistency |
| 6 | Deploy to Render, add Actuator metrics, load test with k6 and record real numbers, write architecture diagram in README | Deployment, observability, system design |

## Resume bullets (fill in real numbers after Week 6)
- Built a Spring Boot order and payment backend with JWT auth, role-based access and N REST endpoints documented with OpenAPI.
- Prevented overselling under concurrent orders using optimistic locking and transactional retries; verified with a 100-thread concurrency test.
- Implemented idempotent payment API so retried requests never double-charge.
- Cut product read latency from X ms to Y ms with Redis caching (k6 load test); added async order events via Kafka.
- Containerized with Docker, CI on GitHub Actions, N% test coverage with JUnit 5 and Testcontainers.

## Notes
- Set `JWT_SECRET` (32+ bytes) in any real environment; the default is for local dev only.
- New admins: insert a user row with role `ADMIN` (registration always creates `USER`).
