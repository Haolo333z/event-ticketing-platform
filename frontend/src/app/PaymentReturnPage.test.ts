import { describe, it } from 'node:test';
import assert from 'node:assert';
import { createElement as h } from 'react';
import { renderToString } from 'react-dom/server';
import { PaymentReturnPage } from './PaymentReturnPage.ts';
import type { Api } from '../api/client.ts';

describe('PaymentReturnPage Component', () => {
  const mockApi: Api = {
    events: async () => ({ items: [] }),
    detail: async () => ({} as any),
    availability: async () => ({ eventId: '', asOf: '', ticketTypes: [] }),
    create: async () => ({} as any),
    booking: async () => ({} as any),
    mine: async () => ({ items: [] }),
    simulate: async () => ({ paymentIntentId: '', status: 'ACCEPTED' }),
    cancel: async () => ({} as any),
    tickets: async () => [],
    payments: {
      checkout: async () => ({} as any),
      status: async () => ({} as any),
      vnpayReturn: async () => ({
        isValidSignature: true,
        isSuccess: true,
        providerTxRef: '14589201',
        merchantRef: 'EVF_TEST_001',
        amountVnd: 500000,
        responseCode: '00',
        message: 'Thanh toán thành công'
      })
    }
  };

  it('renders payment return component without crashing', () => {
    const html = renderToString(
      h(PaymentReturnPage, {
        api: mockApi,
        onNavigate: () => {}
      })
    );
    assert.ok(html.includes('Kết quả thanh toán'));
    assert.ok(html.includes('THÔNG BÁO GIAO DỊCH') || html.includes('KẾT QUẢ GIAO DỊCH'));
  });
});
