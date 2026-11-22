import { createElement as h, useState, useEffect } from 'react';
import type { Api, User, OnboardingStatusResponse } from '../api/client.ts';
import { ApiError } from '../api/client.ts';

interface OrganizerOnboardingModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: User | null;
  api: Api;
  onNavigateToOrganizer: () => void;
}

export function OrganizerOnboardingModal({
  isOpen,
  onClose,
  user,
  api,
  onNavigateToOrganizer
}: OrganizerOnboardingModalProps) {
  if (!isOpen) return null;

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [statusData, setStatusData] = useState<OnboardingStatusResponse | null>(null);

  const [organizationName, setOrganizationName] = useState('');
  const [phone, setPhone] = useState(user?.phone || '');
  const [taxCode, setTaxCode] = useState('');
  const [saasPlan, setSaasPlan] = useState<'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE'>('PROFESSIONAL');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (isOpen && user && api.onboard?.status) {
      setLoading(true);
      setError(null);
      api.onboard.status()
        .then(res => {
          setStatusData(res);
          if (res.hasSubmitted && res.organizationName) {
            setOrganizationName(res.organizationName);
          }
        })
        .catch(err => {
          console.warn('Could not load onboarding status', err);
        })
        .finally(() => setLoading(false));
    }
  }, [isOpen, user, api]);

  async function handleSubmit(e: any) {
    e.preventDefault();
    if (!organizationName.trim()) {
      setError('Vui lòng nhập tên công ty hoặc thương hiệu tổ chức');
      return;
    }
    if (!phone.trim()) {
      setError('Vui lòng nhập số điện thoại liên hệ');
      return;
    }

    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      if (api.onboard?.submit) {
        const res = await api.onboard.submit({
          organizationName: organizationName.trim(),
          phone: phone.trim(),
          taxCode: taxCode.trim() || undefined,
          saasPlan,
          message: message.trim() || undefined
        });
        setSuccess(res.message || 'Hồ sơ đã được gửi thành công! Quản trị viên sẽ phê duyệt trong vòng 24h.');
        // Refresh status
        if (api.onboard?.status) {
          const updated = await api.onboard.status();
          setStatusData(updated);
        }
      }
    } catch (err: any) {
      const msg = err instanceof ApiError ? err.message : 'Có lỗi xảy ra khi nộp hồ sơ. Vui lòng thử lại sau.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  const isPending = statusData?.hasSubmitted && (statusData.status === 'NEW' || statusData.status === 'CONTACTED');
  const isApproved = statusData?.hasSubmitted && statusData.status === 'CONVERTED';
  const isRejected = statusData?.hasSubmitted && (statusData.status === 'REJECTED' || statusData.status === 'CLOSED');

  return h('div', { className: 'auth-modal-overlay', onClick: onClose },
    h('div', {
      className: 'auth-modal-card onboarding-modal-card',
      style: { maxWidth: '560px', width: '90%' },
      onClick: (e: any) => e.stopPropagation()
    },
      h('div', { className: 'auth-modal-header' },
        h('div', { style: { display: 'flex', alignItems: 'center', gap: '0.75rem' } },
          h('span', { style: { fontSize: '1.75rem' } }, '🎪'),
          h('div', null,
            h('h3', { style: { margin: 0, fontSize: '1.25rem', fontWeight: 700 } }, 'Đăng Ký Trở Thành Nhà Tổ Chức'),
            h('p', { style: { margin: '0.25rem 0 0', fontSize: '0.875rem', color: '#64748b' } },
              'Xuất bản sự kiện, bán vé concert và quản lý phân bổ chỗ ngồi độc quyền'
            )
          )
        ),
        h('button', {
          type: 'button',
          className: 'auth-modal-close',
          onClick: onClose,
          'aria-label': 'Đóng'
        }, '✕')
      ),

      h('div', { className: 'auth-modal-body', style: { padding: '1.5rem 0 0' } },
        loading ? h('div', { style: { textAlign: 'center', padding: '2rem 0', color: '#64748b' } }, 'Đang kiểm tra trạng thái hồ sơ...') :
        isApproved ? h('div', { className: 'onboarding-status-banner status-approved', style: { background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '12px', padding: '1.5rem', textAlign: 'center' } },
          h('div', { style: { fontSize: '2.5rem', marginBottom: '0.5rem' } }, '🎉'),
          h('h4', { style: { color: '#166534', margin: '0 0 0.5rem', fontSize: '1.15rem' } }, 'Hồ sơ của bạn đã được phê duyệt!'),
          h('p', { style: { color: '#15803d', fontSize: '0.9rem', marginBottom: '1.25rem' } },
            'Tài khoản của bạn đã được kích hoạt đầy đủ quyền Nhà tổ chức (ORGANIZER). Bạn có thể bắt đầu tạo sự kiện và phát hành vé ngay.'
          ),
          h('button', {
            type: 'button',
            className: 'btn-primary',
            style: { width: '100%', padding: '0.75rem' },
            onClick: () => {
              onClose();
              onNavigateToOrganizer();
            }
          }, 'Vào Bàn Làm Việc Nhà Tổ Chức →')
        ) :
        isPending ? h('div', { className: 'onboarding-status-banner status-pending', style: { background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '12px', padding: '1.5rem', textAlign: 'center' } },
          h('div', { style: { fontSize: '2.5rem', marginBottom: '0.5rem' } }, '⏳'),
          h('h4', { style: { color: '#92400e', margin: '0 0 0.5rem', fontSize: '1.15rem' } }, 'Hồ sơ đang chờ phê duyệt'),
          h('p', { style: { color: '#b45309', fontSize: '0.9rem', marginBottom: '1rem', lineHeight: 1.5 } },
            `Đơn vị: ` + (statusData?.organizationName || 'Doanh nghiệp') + ` (${statusData?.saasPlan || 'PROFESSIONAL'})`
          ),
          h('p', { style: { color: '#78350f', fontSize: '0.85rem', background: '#fef3c7', padding: '0.75rem', borderRadius: '8px', margin: '0 0 1rem' } },
            'Đội ngũ Quản trị viên EventFlow đã tiếp nhận hồ sơ của bạn và sẽ liên hệ xác minh, phê duyệt tài khoản trong vòng 24 giờ.'
          ),
          h('button', {
            type: 'button',
            className: 'btn-secondary',
            style: { width: '100%', padding: '0.65rem' },
            onClick: onClose
          }, 'Đã hiểu, quay lại sau')
        ) :
        h('form', { onSubmit: handleSubmit, className: 'onboarding-form' },
          error && h('div', { className: 'alert error', style: { marginBottom: '1rem' } }, error),
          success && h('div', { className: 'alert success', style: { marginBottom: '1rem', background: '#f0fdf4', color: '#166534', border: '1px solid #bbf7d0', padding: '0.75rem', borderRadius: '8px' } }, success),
          isRejected && h('div', { style: { background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem', color: '#991b1b', fontSize: '0.875rem' } },
            h('strong', null, 'Lưu ý: '),
            'Hồ sơ trước đó bị từ chối: ' + (statusData?.notes || 'Thông tin chưa đầy đủ') + '. Vui lòng cập nhật lại thông tin bên dưới để gửi xét duyệt lại.'
          ),

          // User Info (Locked Read-Only)
          h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1rem' } },
            h('div', { className: 'form-group' },
              h('label', { style: { fontSize: '0.8rem', fontWeight: 600, color: '#475569', display: 'block', marginBottom: '0.25rem' } },
                'Email tài khoản (Khóa cứng 🔒)'
              ),
              h('input', {
                type: 'email',
                value: user?.email || '',
                disabled: true,
                style: { width: '100%', padding: '0.65rem', background: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: '8px', color: '#64748b', cursor: 'not-allowed', fontSize: '0.875rem' }
              })
            ),
            h('div', { className: 'form-group' },
              h('label', { style: { fontSize: '0.8rem', fontWeight: 600, color: '#475569', display: 'block', marginBottom: '0.25rem' } },
                'Người đại diện'
              ),
              h('input', {
                type: 'text',
                value: user?.displayName || '',
                disabled: true,
                style: { width: '100%', padding: '0.65rem', background: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: '8px', color: '#64748b', cursor: 'not-allowed', fontSize: '0.875rem' }
              })
            )
          ),

          // Org Details
          h('div', { className: 'form-group', style: { marginBottom: '1rem' } },
            h('label', { style: { fontSize: '0.85rem', fontWeight: 600, color: '#1e293b', display: 'block', marginBottom: '0.35rem' } },
              'Tên Công Ty / Đơn Vị Tổ Chức *'
            ),
            h('input', {
              type: 'text',
              required: true,
              placeholder: 'Ví dụ: Công ty Cổ phần Giải Trí Mây Đỏ (Red Cloud Ent)',
              value: organizationName,
              onChange: (e: any) => setOrganizationName(e.target.value),
              style: { width: '100%', padding: '0.65rem 0.75rem', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.9rem' }
            })
          ),

          h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1rem' } },
            h('div', { className: 'form-group' },
              h('label', { style: { fontSize: '0.85rem', fontWeight: 600, color: '#1e293b', display: 'block', marginBottom: '0.35rem' } },
                'Số Điện Thoại Liên Hệ *'
              ),
              h('input', {
                type: 'tel',
                required: true,
                placeholder: '0901234567',
                value: phone,
                onChange: (e: any) => setPhone(e.target.value),
                style: { width: '100%', padding: '0.65rem 0.75rem', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.9rem' }
              })
            ),
            h('div', { className: 'form-group' },
              h('label', { style: { fontSize: '0.85rem', fontWeight: 600, color: '#1e293b', display: 'block', marginBottom: '0.35rem' } },
                'Mã Số Thuế (MST)'
              ),
              h('input', {
                type: 'text',
                placeholder: '0101234567 hoặc 0312345678',
                value: taxCode,
                onChange: (e: any) => setTaxCode(e.target.value),
                style: { width: '100%', padding: '0.65rem 0.75rem', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.9rem' }
              })
            )
          ),

          h('div', { className: 'form-group', style: { marginBottom: '1rem' } },
            h('label', { style: { fontSize: '0.85rem', fontWeight: 600, color: '#1e293b', display: 'block', marginBottom: '0.35rem' } },
              'Gói Hạ Tầng SaaS Phù Hợp'
            ),
            h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.5rem' } },
              ([
                { id: 'STARTER', label: 'Starter', desc: 'Sự kiện < 1.000 khách' },
                { id: 'PROFESSIONAL', label: 'Professional', desc: 'Đại nhạc hội < 15.000 vé' },
                { id: 'ENTERPRISE', label: 'Enterprise', desc: 'Sân vận động > 20.000 vé' }
              ] as const).map(p => h('label', {
                key: p.id,
                style: {
                  display: 'flex',
                  flexDirection: 'column',
                  padding: '0.6rem',
                  border: saasPlan === p.id ? '2px solid #2563eb' : '1px solid #e2e8f0',
                  borderRadius: '8px',
                  background: saasPlan === p.id ? '#eff6ff' : '#ffffff',
                  cursor: 'pointer',
                  fontSize: '0.8rem'
                }
              },
                h('div', { style: { display: 'flex', alignItems: 'center', gap: '0.35rem', fontWeight: 700, color: saasPlan === p.id ? '#1e40af' : '#1e293b' } },
                  h('input', {
                    type: 'radio',
                    name: 'saasPlan',
                    checked: saasPlan === p.id,
                    onChange: () => setSaasPlan(p.id)
                  }),
                  p.label
                ),
                h('span', { style: { fontSize: '0.72rem', color: '#64748b', marginTop: '0.2rem' } }, p.desc)
              ))
            )
          ),

          h('div', { className: 'form-group', style: { marginBottom: '1.25rem' } },
            h('label', { style: { fontSize: '0.85rem', fontWeight: 600, color: '#1e293b', display: 'block', marginBottom: '0.35rem' } },
              'Kinh Nghiệm / Nhu Cầu Tổ Chức'
            ),
            h('textarea', {
              rows: 2,
              placeholder: 'Mô tả ngắn quy mô, thể loại sự kiện (Concert, Workshop, Thể thao) bạn dự kiến mở bán...',
              value: message,
              onChange: (e: any) => setMessage(e.target.value),
              style: { width: '100%', padding: '0.65rem 0.75rem', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.875rem' }
            })
          ),

          h('button', {
            type: 'submit',
            className: 'btn-primary',
            disabled: submitting,
            style: { width: '100%', padding: '0.75rem', fontSize: '0.95rem', fontWeight: 600 }
          }, submitting ? 'Đang gửi hồ sơ...' : 'Gửi Hồ Sơ Xét Duyệt Nhà Tổ Chức 🚀')
        )
      )
    )
  );
}
