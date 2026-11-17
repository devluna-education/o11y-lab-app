// Continuous synthetic traffic for the O11y Lab Shop.
// All traffic goes through the frontend (nginx) so every request crosses the full path:
// nginx -> order-service (Java) -> catalog-service (Python) -> Redis / PostgreSQL.

import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://frontend:8080';
const BROWSE_VUS = parseInt(__ENV.BROWSE_VUS || '5', 10);
const ORDERS_PER_MINUTE = parseInt(__ENV.ORDERS_PER_MINUTE || '30', 10);
const DURATION = __ENV.DURATION || '1h';
const ERROR_SCENARIOS = (__ENV.ERROR_SCENARIOS || 'true') === 'true';
const PRODUCT_COUNT = 50;

export const options = {
  discardResponseBodies: false,
  scenarios: {
    browse: {
      executor: 'constant-vus',
      vus: BROWSE_VUS,
      duration: DURATION,
      exec: 'browse',
    },
    purchase: {
      executor: 'constant-arrival-rate',
      rate: ORDERS_PER_MINUTE,
      timeUnit: '1m',
      duration: DURATION,
      preAllocatedVUs: 2,
      maxVUs: 10,
      exec: 'purchase',
    },
  },
};

const params = (name) => ({
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  tags: { name },
  timeout: '15s',
});

const randomInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

// Wait for the stack to be healthy before generating traffic.
export function setup() {
  const deadline = Date.now() + 5 * 60 * 1000;
  while (Date.now() < deadline) {
    const catalog = http.get(`${BASE_URL}/api/catalog/health/ready`, { timeout: '5s' });
    const orders = http.get(`${BASE_URL}/api/order-health`, { timeout: '5s' });
    if (catalog.status === 200 && orders.status === 200) {
      console.log('Stack is ready, starting load');
      return;
    }
    console.log(`Waiting for stack (catalog=${catalog.status}, orders=${orders.status})`);
    sleep(5);
  }
  console.warn('Stack not ready after 5 minutes, starting anyway');
}

export function browse() {
  group('browse catalog', () => {
    const skip = randomInt(0, 4) * 10;
    const list = http.get(`${BASE_URL}/api/catalog/products?skip=${skip}&limit=10`, params('list products'));
    check(list, { 'list 200': (r) => r.status === 200 });

    if (Math.random() < 0.3) {
      const terms = ['smart', 'pro', 'lamp', 'drone', 'eco'];
      const q = terms[randomInt(0, terms.length - 1)];
      const search = http.get(`${BASE_URL}/api/catalog/products?q=${q}`, params('search products'));
      check(search, { 'search 200': (r) => r.status === 200 });
    }

    const id = randomInt(1, PRODUCT_COUNT);
    const product = http.get(`${BASE_URL}/api/catalog/products/${id}`, params('get product'));
    check(product, { 'product 200': (r) => r.status === 200 });

    if (Math.random() < 0.2) {
      const recs = http.get(`${BASE_URL}/api/catalog/products/${id}/recommendations`, params('recommendations'));
      check(recs, { 'recommendations 200': (r) => r.status === 200 });
    }
  });

  group('view orders', () => {
    const orders = http.get(`${BASE_URL}/api/orders?limit=20`, params('list orders'));
    check(orders, { 'orders 200': (r) => r.status === 200 });
    if (Math.random() < 0.3) {
      http.get(`${BASE_URL}/api/orders/stats`, params('order stats'));
    }
  });

  sleep(Math.random() * 2 + 1);
}

export function purchase() {
  const customerId = `k6-customer-${randomInt(1, 200)}`;
  let productId = randomInt(1, PRODUCT_COUNT);
  let quantity = randomInt(1, 3);

  // A small share of requests are intentionally bad, producing realistic 4xx traffic.
  if (ERROR_SCENARIOS) {
    const roll = Math.random();
    if (roll < 0.03) productId = 9999;        // 404: unknown product
    else if (roll < 0.05) quantity = 0;       // 400: validation error
  }

  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ customerId, productId, quantity }),
    params('create order'),
  );
  check(res, { 'order accepted or expected error': (r) => [201, 400, 404, 409].includes(r.status) });

  if (res.status === 201) {
    const order = res.json();
    http.get(`${BASE_URL}/api/orders/${order.id}`, params('get order'));
  }
}
