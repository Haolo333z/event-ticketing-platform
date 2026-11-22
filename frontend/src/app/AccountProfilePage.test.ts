import { describe, it } from 'node:test';
import assert from 'node:assert';
import { createElement as h } from 'react';
import { renderToString } from 'react-dom/server';
import { AccountProfilePage } from './AccountProfilePage.ts';
import type { Api, User } from '../api/client.ts';

describe('AccountProfilePage Component', () => {
  const mockUser: User = {
    id: '10000000-0000-4000-8000-000000000001',
    displayName: 'Nguyễn Văn Khách',
    email: 'khach@eventflow.vn',
    phone: '0901234567',
    role: 'CUSTOMER',
    roles: ['CUSTOMER']
  };

  const mockApi: Api = {
    events: async () => ({ items: [] }),
    detail: async () => ({ id: 'e1', title: 'Concert', description: '', venue: '', startsAt: '', ticketTypes: [] }),
    availability: async () => ({ eventId: 'e1', asOf: '', ticketTypes: [] }),
    create: async () => ({ bookingId: 'b1', status: 'PENDING_PAYMENT', expiresAt: '', totalAmountVnd: 500000 }),
    booking: async () => ({ bookingId: 'b1', status: 'CONFIRMED', expiresAt: '', totalAmountVnd: 500000 }),
    mine: async () => ({ items: [] }),
    simulate: async () => ({ paymentIntentId: 'p1', status: 'ACCEPTED' }),
    cancel: async () => ({ bookingId: 'b1', status: 'CANCELLED', expiresAt: '', totalAmountVnd: 500000 }),
    tickets: async () => [],
    profile: {
      get: async () => ({
        id: '10000000-0000-4000-8000-000000000001',
        email: 'khach@eventflow.vn',
        displayName: 'Nguyễn Văn Khách',
        phone: '0901234567',
        avatarUrl: '',
        demoRole: 'CUSTOMER',
        roles: ['CUSTOMER'],
        googleLinked: false
      }),
      update: async (d) => ({
        id: '10000000-0000-4000-8000-000000000001',
        email: 'khach@eventflow.vn',
        displayName: d.displayName,
        phone: d.phone || '',
        demoRole: 'CUSTOMER',
        roles: ['CUSTOMER'],
        googleLinked: false
      }),
      changePassword: async () => ({ success: true, message: 'Đổi mật khẩu thành công' }),
      vatDefaults: async () => ({ companyName: 'Công ty Test', taxCode: '0101234567' }),
      updateVatDefaults: async (d) => d,
      sessions: async () => ({ sessions: [], total: 0 }),
      revokeSession: async () => ({ success: true, message: 'OK' }),
      revokeOtherSessions: async () => ({ success: true, message: 'OK' })
    }
  };

  it('renders unauthenticated state when user is null', () => {
    const html = renderToString(
      h(AccountProfilePage, {
        api: mockApi,
        user: null,
        onNavigate: () => {}
      })
    );
    assert.match(html, /Yêu cầu đăng nhập/);
    assert.match(html, /Quay về Trang chủ/);
  });

  it('renders profile layout and 5 tab buttons when user is authenticated', () => {
    const html = renderToString(
      h(AccountProfilePage, {
        api: mockApi,
        user: mockUser,
        onNavigate: () => {}
      })
    );
    assert.match(html, /Thông tin cá nhân/);
    assert.match(html, /Bảo mật &amp; Phiên/);
    assert.match(html, /Hóa đơn VAT/);
    assert.match(html, /Nhắc hẹn &amp; Chờ vé/);
    assert.match(html, /Đối tác Tổ chức/);
    assert.match(html, /Nguyễn Văn Khách/);
    assert.match(html, /khach@eventflow\.vn/);
  });
});
