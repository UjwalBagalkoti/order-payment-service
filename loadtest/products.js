// Read-heavy load test for GET /api/products/{id}, the endpoint the Redis cache protects.
//
//   k6 run loadtest/products.js                       # app started with CACHE_ENABLED=true
//   k6 run loadtest/products.js                       # restart app with CACHE_ENABLED=false, run again
//
// Compare p(95) latency and request rate between the two runs, and record YOUR numbers in the README.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@example.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'ChangeMe123!';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  stages: [
    { duration: '15s', target: 50 },
    { duration: '45s', target: 50 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export function setup() {
  const login = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: JSON_HEADERS },
  );
  check(login, { 'admin login ok': (r) => r.status === 200 });
  const token = login.json('token');

  const created = http.post(
    `${BASE}/api/admin/products`,
    JSON.stringify({ name: `k6-${Date.now()}`, description: 'load test', price: 99.0, stock: 1000000 }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } },
  );
  check(created, { 'product created': (r) => r.status === 201 });
  return { productId: created.json('id') };
}

export default function (data) {
  const res = http.get(`${BASE}/api/products/${data.productId}`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
