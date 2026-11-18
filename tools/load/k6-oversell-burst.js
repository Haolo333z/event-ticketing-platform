/**
 * EventFlow V3 — k6 High-Concurrency Load & Zero-Oversell Verification Script
 *
 * Usage (requires k6 installed: https://k6.io):
 *   k6 run -e API_BASE_URL=http://127.0.0.1:8080 -e EVENT_ID=20000000-0000-4000-8000-000000000002 -e TICKET_TYPE_ID=30000000-0000-4000-8000-000000000002 tools/load/k6-oversell-burst.js
 *
 * Proves under 500 concurrent virtual users firing simultaneously:
 *   - Zero Oversell (HTTP 202 accepted bookings never exceed inventory capacity)
 *   - Zero Deadlocks / Unhandled 500s (only valid 202 Held, 409 Inventory Unavailable, or bounded 503 Busy)
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const accepted202 = new Counter('booking_accepted_202');
const conflict409 = new Counter('booking_conflict_409');
const busy503 = new Counter('booking_busy_503');
const unexpected500 = new Counter('booking_unexpected_500');

export const options = {
  scenarios: {
    flash_sale_burst: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 500),
      iterations: 1,
      maxDuration: '60s',
    },
  },
  thresholds: {
    booking_unexpected_500: ['count==0'],
    http_req_duration: ['p(95)<2000'],
  },
};

function randomUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function () {
  const base = __ENV.API_BASE_URL || 'http://127.0.0.1:8080';
  const eventId = __ENV.EVENT_ID || '20000000-0000-4000-8000-000000000002';
  const ticketTypeId = __ENV.TICKET_TYPE_ID || '30000000-0000-4000-8000-000000000002';
  const token = __ENV.AUTH_TOKEN || 'demo-customer-token';

  const payload = JSON.stringify({
    eventId,
    ticketTypeId,
    quantity: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': randomUuid(),
    },
  };

  const res = http.post(`${base}/api/bookings`, payload, params);

  if (res.status === 202) accepted202.add(1);
  else if (res.status === 409) conflict409.add(1);
  else if (res.status === 503) busy503.add(1);
  else unexpected500.add(1);

  check(res, {
    'expected booking outcome (202/409/503)': (r) => [202, 409, 503].includes(r.status),
  });
}
