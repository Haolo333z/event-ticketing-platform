import { describe, it } from 'node:test';
import assert from 'node:assert';
import { createElement as h } from 'react';
import { renderToString } from 'react-dom/server';
import { StaffCheckInPage } from './StaffCheckInPage.ts';
import type { Api, User, AssignedEvent, CheckInResult, CheckInStats, CheckInHistoryItem } from '../api/client.ts';

describe('StaffCheckInPage Component', () => {
  const staffUser: User = {
    id: 'staff-uuid-001',
    displayName: 'Nhân Viên Soát Vé A',
    email: 'staff.a@eventflow.vn',
    role: 'STAFF',
    roles: ['STAFF']
  };

  const customerUser: User = {
    id: 'cust-uuid-001',
    displayName: 'Khách Hàng B',
    email: 'cust.b@eventflow.vn',
    role: 'CUSTOMER',
    roles: ['CUSTOMER']
  };

  const adminUser: User = {
    id: 'admin-uuid-001',
    displayName: 'Quản Trị Viên',
    email: 'admin@eventflow.vn',
    role: 'ADMIN',
    roles: ['ADMIN']
  };

  const mockEvents: AssignedEvent[] = [
    {
      eventId: 'ev-uuid-001',
      title: 'Đại Nhạc Hội Mùa Thu 2026',
      venue: 'Sân Vận Động Mỹ Đình',
      startsAt: '2026-11-20T19:00:00Z',
      endsAt: '2026-11-20T22:30:00Z',
      status: 'PUBLISHED',
      assignedAt: '2026-11-16T08:00:00Z'
    }
  ];

  const mockStats: CheckInStats = {
    totalSold: 2500,
    checkedInCount: 1850,
    remainingCount: 650,
    checkInRatePercent: 74.0
  };

  const mockHistory: CheckInHistoryItem[] = [
    {
      checkInId: 'chk-001',
      ticketId: 'tkt-001',
      ticketCode: 'TKT-VIP-01',
      attendeeName: 'Nguyễn Văn C',
      ticketType: 'VIP Khán Phòng',
      seatLabel: 'VIP-A12',
      gateName: 'GATE_A_VIP',
      checkedInAt: '2026-11-16T19:20:00Z',
      staffName: 'Nhân Viên Soát Vé A'
    }
  ];

  const mockApi: Api = {
    events: async () => ({ items: [] }),
    detail: async () => ({} as any),
    availability: async () => ({} as any),
    create: async () => ({} as any),
    booking: async () => ({} as any),
    mine: async () => ({ items: [] }),
    simulate: async () => ({} as any),
    cancel: async () => ({} as any),
    tickets: async () => [],
    staff: {
      events: async () => mockEvents,
      checkIn: async () => ({
        status: 'CHECKED_IN',
        ticketId: 'tkt-001',
        ticketCode: 'TKT-VIP-01',
        attendeeName: 'Nguyễn Văn C',
        ticketType: 'VIP Khán Phòng',
        seatLabel: 'VIP-A12',
        checkedInAt: '2026-11-16T19:20:00Z',
        gate: 'GATE_A_VIP',
        message: 'HỢP LỆ — MỜI VÀO'
      }),
      stats: async () => mockStats,
      history: async () => mockHistory
    }
  };

  it('renders unauthenticated state when user is null', () => {
    const html = renderToString(h(StaffCheckInPage, {
      api: mockApi,
      user: null,
      onNavigate: () => {},
      onOpenAuth: () => {}
    }));

    assert.ok(html.includes('CỔNG VẬN HÀNH SOÁT VÉ (STAFF PORTAL)'));
    assert.ok(html.includes('Đăng Nhập Ngay'));
  });

  it('renders unauthorized state when user is only CUSTOMER', () => {
    const html = renderToString(h(StaffCheckInPage, {
      api: mockApi,
      user: customerUser,
      onNavigate: () => {},
      onOpenAuth: () => {}
    }));

    assert.ok(html.includes('KHÔNG CÓ QUYỀN TRUY CẬP'));
    assert.ok(html.includes('Quay Lại Trang Chủ'));
  });

  it('renders staff portal terminal layout when user has STAFF role', () => {
    const html = renderToString(h(StaffCheckInPage, {
      api: mockApi,
      user: staffUser,
      onNavigate: () => {},
      onOpenAuth: () => {}
    }));

    assert.ok(html.includes('STAFF TERMINAL'));
    assert.ok(html.includes('Hệ Thống Soát Vé Cổng Điện Tử'));
    assert.ok(html.includes('Quét Mã QR'));
    assert.ok(html.includes('Nhập Mã Thủ Công'));
  });

  it('renders staff portal terminal layout when user has ADMIN role', () => {
    const html = renderToString(h(StaffCheckInPage, {
      api: mockApi,
      user: adminUser,
      onNavigate: () => {},
      onOpenAuth: () => {}
    }));

    assert.ok(html.includes('STAFF TERMINAL'));
    assert.ok(html.includes('CỔNG CHÍNH (MAIN_GATE)'));
  });
});
