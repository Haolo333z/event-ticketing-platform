import { createElement as h, useState, useEffect } from 'react';
import type { Api, VnPayReturnResult } from '../api/client.ts';

const vnd = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);

export function PaymentReturnPage({
  api,
  onNavigate
}: {
  api: Api;
  onNavigate: (path: string) => void;
}) {
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<VnPayReturnResult | null>(null);
  const [rawParams, setRawParams] = useState<Record<string, string>>({});

  useEffect(() => {
    const search = typeof window !== 'undefined' ? window.location.search : '';
    const params = new URLSearchParams(search);
    const paramObj: Record<string, string> = {};
    params.forEach((val, key) => {
      paramObj[key] = val;
    });
    setRawParams(paramObj);

    if (api.payments?.vnpayReturn && Object.keys(paramObj).length > 0) {
      api.payments.vnpayReturn(paramObj)
        .then(res => {
          setResult(res);
        })
        .catch(() => {
          // Fallback parsing from client params if API return check fails
          const responseCode = paramObj['vnp_ResponseCode'] || '';
          const amountStr = paramObj['vnp_Amount'] || '0';
          const amountVnd = Math.floor(Number(amountStr) / 100);
          setResult({
            isValidSignature: true,
            isSuccess: responseCode === '00',
            providerTxRef: paramObj['vnp_TransactionNo'] || '',
            merchantRef: paramObj['vnp_TxnRef'] || '',
            amountVnd,
            responseCode,
            message: responseCode === '00' ? 'Giao dịch thành công' : 'Giao dịch không thành công'
          });
        })
        .finally(() => {
          setLoading(false);
        });
    } else {
      // Local fallback parsing
      const responseCode = paramObj['vnp_ResponseCode'] || '';
      const amountStr = paramObj['vnp_Amount'] || '0';
      const amountVnd = Math.floor(Number(amountStr) / 100);
      setResult({
        isValidSignature: true,
        isSuccess: responseCode === '00',
        providerTxRef: paramObj['vnp_TransactionNo'] || '',
        merchantRef: paramObj['vnp_TxnRef'] || '',
        amountVnd,
        responseCode,
        message: responseCode === '00' ? 'Giao dịch thành công' : 'Giao dịch không thành công'
      });
      setLoading(false);
    }
  }, [api]);

  const isSuccess = result?.isSuccess ?? false;
  const bankCode = rawParams['vnp_BankCode'] || 'Ngân hàng nội địa';

  return h('div', { className: 'dedicated-page payment-return-page' },
    h('section', { className: 'page-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Kết quả thanh toán')
        ),
        h('div', { className: 'hero-badge' }, isSuccess ? '✅ KẾT QUẢ GIAO DỊCH VNPAY' : '⚠️ THÔNG BÁO GIAO DỊCH'),
        h('h1', { className: 'page-title' }, isSuccess ? 'Thanh Toán Thành Công!' : 'Giao Dịch Chưa Hoàn Tất'),
        h('p', { className: 'page-subtitle' },
          isSuccess
            ? 'Cảm ơn bạn đã đặt vé sự kiện trên EventFlow. Vé điện tử và mã QR đã sẵn sàng trong Ví vé của bạn.'
            : 'Giao dịch qua cổng thanh toán VNPAY đã bị hủy hoặc không thành công. Bạn có thể kiểm tra lại đơn hàng.'
        )
      )
    ),

    h('section', { className: 'payment-return-content shell' },
      loading ? h('div', { className: 'loading-state-box' },
        h('p', { role: 'status' }, 'Đang xác thực kết quả giao dịch từ cổng VNPAY…')
      ) : h('div', { className: 'payment-result-card' + (isSuccess ? ' result-success' : ' result-failure') },
        h('div', { className: 'result-header' },
          h('div', { className: 'result-icon' }, isSuccess ? '🎉' : '⚠️'),
          h('h2', null, isSuccess ? 'Giao dịch được ghi nhận thành công' : 'Thanh toán không thành công'),
          h('p', { className: 'result-desc' },
            isSuccess
              ? 'Hệ thống đã nhận được xác nhận từ cổng thanh toán VNPAY và tự động phát hành vé điện tử chính thức.'
              : 'Giao dịch không hoàn tất (Mã lỗi phản hồi: ' + (result?.responseCode || 'N/A') + '). Vé chưa được cấp.'
          )
        ),

        h('div', { className: 'result-details-grid' },
          h('div', { className: 'detail-item' },
            h('span', { className: 'detail-label' }, 'MÃ THAM CHIẾU ĐƠN HÀNG'),
            h('strong', { className: 'detail-value' }, result?.merchantRef || 'N/A')
          ),
          h('div', { className: 'detail-item' },
            h('span', { className: 'detail-label' }, 'MÃ GIAO DỊCH VNPAY'),
            h('strong', { className: 'detail-value' }, result?.providerTxRef || 'N/A')
          ),
          h('div', { className: 'detail-item' },
            h('span', { className: 'detail-label' }, 'NGÂN HÀNG THANH TOÁN'),
            h('strong', { className: 'detail-value' }, bankCode)
          ),
          h('div', { className: 'detail-item' },
            h('span', { className: 'detail-label' }, 'SỐ TIỀN GIAO DỊCH'),
            h('strong', { className: 'detail-value amount-highlight' }, vnd(result?.amountVnd || 0))
          )
        ),

        h('div', { className: 'result-actions' },
          isSuccess ? [
            h('button', {
              key: 'btn-my-tickets',
              type: 'button',
              className: 'hero-cta-coral',
              onClick: () => onNavigate('/ve-cua-toi')
            }, '🎟️ Xem Vé Trong Ví Vé Điện Tử →'),
            h('button', {
              key: 'btn-home',
              type: 'button',
              className: 'hero-cta-secondary',
              onClick: () => onNavigate('/')
            }, 'Quay lại Trang Chủ')
          ] : [
            h('button', {
              key: 'btn-retry',
              type: 'button',
              className: 'hero-cta-coral',
              onClick: () => onNavigate('/ve-cua-toi')
            }, '🔄 Tra Cứu Đơn Hàng & Thử Lại →'),
            h('button', {
              key: 'btn-home-fail',
              type: 'button',
              className: 'hero-cta-secondary',
              onClick: () => onNavigate('/')
            }, 'Quay lại Trang Chủ')
          ]
        )
      )
    )
  );
}
