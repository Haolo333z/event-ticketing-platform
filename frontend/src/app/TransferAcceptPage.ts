import { createElement as h, useState, useEffect } from 'react';
import type { Api, User, TransferDetail } from '../api/client.ts';

export function TransferAcceptPage({
  api,
  user,
  onOpenAuth,
  onNavigate
}: {
  api: Api;
  user: User | null;
  onOpenAuth: () => void;
  onNavigate: (path: string) => void;
}) {
  const [tokenInput, setTokenInput] = useState(() => {
    if (typeof window !== 'undefined') {
      const p = new URLSearchParams(window.location.search).get('token');
      return p ? p.trim() : '';
    }
    return '';
  });
  const [transferInfo, setTransferInfo] = useState<TransferDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);
  const [acceptSuccess, setAcceptSuccess] = useState<{ message: string; newTicketCode: string } | null>(null);

  useEffect(() => {
    if (tokenInput && api.transfers?.lookup) {
      loadTransfer(tokenInput);
    }
  }, []);

  async function loadTransfer(tokenToLookup: string) {
    if (!tokenToLookup.trim() || !api.transfers?.lookup) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.transfers.lookup(tokenToLookup.trim());
      setTransferInfo(res);
    } catch (err: any) {
      setError(err?.message || 'Không tìm thấy thông tin chuyển vé hoặc mã liên kết không đúng.');
      setTransferInfo(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleAccept() {
    if (!transferInfo || !api.transfers?.accept) return;
    if (!user) {
      onOpenAuth();
      return;
    }
    setAccepting(true);
    setError(null);
    try {
      const res = await api.transfers.accept(transferInfo.transferToken);
      setAcceptSuccess({
        message: res.message,
        newTicketCode: res.newTicketCode
      });
    } catch (err: any) {
      setError(err?.message || 'Không thể chấp nhận vé tặng.');
    } finally {
      setAccepting(false);
    }
  }

  return h('div', { className: 'dedicated-page transfer-accept-page' },
    h('section', { className: 'page-hero my-bookings-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Nhận vé tặng')
        ),
        h('div', { className: 'hero-badge' }, '🎁 CHUYỂN NHƯỢNG VÉ AN TOÀN & MINH BẠCH'),
        h('h1', { className: 'page-title' }, 'Nhận Vé Tặng Từ Bạn Bè'),
        h('p', { className: 'page-subtitle' },
          'Sau khi bạn chấp nhận, hệ thống sẽ tự động vô hiệu hóa mã vé cũ và phát hành vé mới kèm chữ ký số bảo mật vào tài khoản của bạn.'
        )
      )
    ),

    h('section', { className: 'transfer-main shell', style: { maxWidth: '640px', margin: '2rem auto', padding: '0 1rem' } },
      acceptSuccess ? h('div', {
        className: 'transfer-success-box',
        style: {
          background: '#ecfdf5',
          border: '1px solid #10b981',
          borderRadius: '16px',
          padding: '2rem',
          textAlign: 'center',
          boxShadow: '0 10px 15px -3px rgba(16, 185, 129, 0.1)'
        }
      },
        h('div', { style: { fontSize: '3rem', marginBottom: '1rem' } }, '🎉'),
        h('h2', { style: { color: '#065f46', fontSize: '1.5rem', margin: '0 0 0.5rem' } }, 'Nhận Vé Thành Công!'),
        h('p', { style: { color: '#047857', fontSize: '1rem', margin: '0 0 1.5rem' } }, acceptSuccess.message),
        h('div', {
          style: {
            background: '#ffffff',
            border: '1px dashed #059669',
            borderRadius: '12px',
            padding: '1rem',
            display: 'inline-block',
            marginBottom: '1.5rem'
          }
        },
          h('span', { style: { fontSize: '0.82rem', color: '#6b7280', display: 'block', marginBottom: '4px' } }, 'MÃ VÉ MỚI CỦA BẠN:'),
          h('strong', { style: { fontSize: '1.25rem', color: '#047857', letterSpacing: '1px' } }, acceptSuccess.newTicketCode)
        ),
        h('div', { style: { display: 'flex', gap: '12px', justifyContent: 'center' } },
          h('button', {
            type: 'button',
            className: 'hero-cta-coral',
            onClick: () => onNavigate('/ve-cua-toi')
          }, '🎟️ Xem vé trong Ví điện tử →')
        )
      ) : h('div', {
        className: 'transfer-card',
        style: {
          background: '#ffffff',
          borderRadius: '16px',
          padding: '2rem',
          border: '1px solid #e2e8f0',
          boxShadow: '0 10px 25px -5px rgba(0,0,0,0.05)'
        }
      },
        !transferInfo ? h('form', {
          onSubmit: (e) => {
            e.preventDefault();
            loadTransfer(tokenInput);
          },
          style: { display: 'flex', flexDirection: 'column', gap: '1rem' }
        },
          h('h3', { style: { margin: 0, fontSize: '1.2rem', color: '#0f172a' } }, 'Nhập mã token nhận vé'),
          h('p', { className: 'muted', style: { margin: 0, fontSize: '0.875rem' } }, 'Nếu bạn nhận được liên kết hoặc mã chuyển vé dạng TRF-..., hãy dán vào đây để kiểm tra:'),
          h('div', { style: { display: 'flex', gap: '8px' } },
            h('input', {
              type: 'text',
              placeholder: 'VD: TRF-A1B2C3D4E5F67890',
              value: tokenInput,
              onChange: (e) => setTokenInput(e.target.value),
              style: { flex: 1, padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.95rem' }
            }),
            h('button', {
              type: 'submit',
              className: 'btn-primary',
              disabled: loading || !tokenInput.trim()
            }, loading ? 'Đang tra cứu…' : 'Kiểm tra vé')
          ),
          error && h('div', { className: 'alert error', style: { marginTop: '0.5rem' } }, error)
        ) : h('div', { style: { display: 'flex', flexDirection: 'column', gap: '1.5rem' } },
          h('div', { style: { borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' } },
            h('span', {
              style: {
                display: 'inline-block',
                background: transferInfo.status === 'PENDING' ? '#e0e7ff' : '#f1f5f9',
                color: transferInfo.status === 'PENDING' ? '#4338ca' : '#64748b',
                padding: '4px 10px',
                borderRadius: '999px',
                fontSize: '0.75rem',
                fontWeight: 700,
                marginBottom: '8px'
              }
            }, transferInfo.status === 'PENDING' ? '⏳ SẴN SÀNG NHẬN VÉ' : `TRẠNG THÁI: ${transferInfo.status}`),
            h('h2', { style: { margin: '0 0 6px', fontSize: '1.35rem', color: '#0f172a' } }, transferInfo.eventTitle),
            h('p', { className: 'muted', style: { margin: 0, fontSize: '0.875rem' } },
              `Mã vé: ${transferInfo.ticketCode}${transferInfo.seatLabel ? ' • Ghế: ' + transferInfo.seatLabel : ' • Vé GA'}`
            )
          ),

          transferInfo.note && h('div', {
            style: {
              background: '#f8fafc',
              borderLeft: '4px solid #6366f1',
              padding: '12px 16px',
              borderRadius: '0 8px 8px 0',
              fontStyle: 'italic',
              color: '#334155',
              fontSize: '0.9rem'
            }
          }, `“${transferInfo.note}”`),

          h('div', {
            style: {
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '12px',
              background: '#f8fafc',
              padding: '1rem',
              borderRadius: '12px',
              fontSize: '0.85rem'
            }
          },
            h('div', null,
              h('span', { style: { color: '#64748b', display: 'block' } }, 'Gửi tặng đến:'),
              h('strong', null, transferInfo.toEmail)
            ),
            h('div', null,
              h('span', { style: { color: '#64748b', display: 'block' } }, 'Hạn nhận vé:'),
              h('strong', null, new Date(transferInfo.expiresAt).toLocaleString('vi-VN'))
            )
          ),

          error && h('div', { className: 'alert error' }, error),

          !user ? h('div', {
            style: {
              background: '#fffbeb',
              border: '1px solid #fde68a',
              borderRadius: '12px',
              padding: '1rem',
              textAlign: 'center'
            }
          },
            h('p', { style: { margin: '0 0 0.75rem', color: '#92400e', fontSize: '0.9rem' } },
              'Vui lòng đăng nhập để hệ thống chuyển vé vào tài khoản của bạn.'
            ),
            h('button', {
              type: 'button',
              className: 'hero-cta-coral',
              onClick: onOpenAuth
            }, 'Đăng nhập để nhận vé →')
          ) : transferInfo.status !== 'PENDING' ? h('div', {
            style: {
              background: '#f1f5f9',
              borderRadius: '12px',
              padding: '1rem',
              textAlign: 'center',
              color: '#64748b'
            }
          },
            `Liên kết này không còn hiệu lực (Trạng thái: ${transferInfo.status}).`
          ) : h('div', { style: { display: 'flex', gap: '12px', justifyContent: 'flex-end' } },
            h('button', {
              type: 'button',
              className: 'quiet',
              disabled: accepting,
              onClick: () => { setTransferInfo(null); setTokenInput(''); }
            }, 'Tra cứu vé khác'),
            h('button', {
              type: 'button',
              className: 'hero-cta-coral',
              disabled: accepting,
              onClick: handleAccept
            }, accepting ? 'Đang nhận vé…' : '🎁 Xác nhận nhận vé ngay')
          )
        )
      )
    )
  );
}
