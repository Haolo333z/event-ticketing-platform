import { describe, it } from 'node:test';
import assert from 'node:assert';
import { createElement as h } from 'react';
import { renderToString } from 'react-dom/server';
import { MyBookingsPage } from './MyBookingsPage.ts';
import type { Api, User, Booking } from '../api/client.ts';

describe('MyBookingsPage Component', () => {
  const mockUser: User = {
    id: '10000000-0000-4000-8000-000000000001',
    displayName: 'Nguyễn Văn Khách',
    email: 'khach@eventflow.vn',
    role: 'CUSTOMER'
  };

  const mockApi: Api = {
    events: async () => ({ items: [] }),
    detail: async () => ({
      id: 'e1',
      title: 'Đêm Nhạc Giao Hưởng Mùa Thu',
      description: 'Concert giao hưởng',
      venue: 'Nhà hát Lớn Hà Nội',
      startsAt: '2026-12-01T19:30:00Z',
      ticketTypes: []
    }),
    availability: async () => ({ eventId: 'e1', asOf: '', ticketTypes: [] }),
    create: async () => ({ bookingId: 'b1', status: 'PENDING_PAYMENT', expiresAt: '', totalAmountVnd: 500000 }),
    booking: async () => ({ bookingId: 'b1', status: 'CONFIRMED', expiresAt: '', totalAmountVnd: 500000 }),
    mine: async () => ({
      items: [
        {
          bookingId: 'b1-uuid-12345678',
          status: 'CONFIRMED',
          expiresAt: '2026-12-01T20:00:00Z',
          totalAmountVnd: 500000
        } as Booking
      ]
    }),
    simulate: async () => ({ paymentIntentId: 'p1', status: 'ACCEPTED' }),
    cancel: async () => ({ bookingId: 'b1', status: 'CANCELLED', expiresAt: '', totalAmountVnd: 500000 }),
    tickets: async () => [
      {
        id: 't1-uuid',
        bookingId: 'b1-uuid-12345678',
        bookingItemId: 'item-1',
        ticketOrdinal: 1,
        code: 'EF-TEST-123456',
        seatId: 's1',
        seatLabel: 'VIP-A01'
      }
    ],
    ticketQr: async () => ({
      ticketId: 't1-uuid',
      ticketCode: 'EF-TEST-123456',
      eventId: 'e1',
      qrToken: 't1-uuid.EF-TEST-123456.e1.1763300000.signatureBase64Url',
      issuedAt: new Date().toISOString()
    })
  };

  it('renders unauthenticated state when user is null', () => {
    const html = renderToString(
      h(MyBookingsPage, {
        api: mockApi,
        user: null,
        onOpenAuth: () => {},
        onNavigate: () => {}
      })
    );
    assert.ok(html.includes('Yêu Cầu Đăng Nhập'));
    assert.ok(html.includes('Đăng nhập tài khoản ngay'));
  });

  it('renders loading or bookings layout when user is authenticated', () => {
    const html = renderToString(
      h(MyBookingsPage, {
        api: mockApi,
        user: mockUser,
        onOpenAuth: () => {},
        onNavigate: () => {}
      })
    );
    assert.ok(html.includes('Vé Điện Tử Của Tôi'));
    assert.ok(html.includes('VÍ VÉ ĐIỆN TỬ &amp; MÃ SOÁT VÉ QR CODE KÝ SỐ HMAC-SHA256'));
  });
});
