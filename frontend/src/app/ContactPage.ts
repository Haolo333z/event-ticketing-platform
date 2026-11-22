import { createElement as h, useState, useEffect } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import type { Api } from '../api/client.ts';

function parseSelectedPackage(path?: string): string {
  let search = '';
  if (typeof window !== 'undefined' && window.location.search) {
    search = window.location.search;
  }
  if (!search && path && path.includes('?')) {
    search = path.slice(path.indexOf('?'));
  }
  const params = new URLSearchParams(search);
  const pkg = (params.get('package') || params.get('plan') || '').toLowerCase();
  if (pkg.includes('start')) return 'STARTER';
  if (pkg.includes('ent')) return 'ENTERPRISE';
  if (pkg.includes('pro')) return 'PRO';
  return 'PRO';
}

export function ContactPage({ api, onNavigate, currentPath }: { api: Api; onNavigate: (path: string) => void; currentPath?: string }) {
  const [form, setForm] = useState({
    fullName: '',
    orgName: '',
    email: '',
    phone: '',
    eventType: 'CONCERT',
    saasPlan: parseSelectedPackage(currentPath),
    message: ''
  });
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [leadId, setLeadId] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const pkg = parseSelectedPackage(currentPath);
    setForm(prev => ({ ...prev, saasPlan: pkg }));
  }, [currentPath]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.fullName.trim() || !form.phone.trim() || !form.email.trim()) {
      setError('Vui lòng điền đầy đủ Họ tên, Số điện thoại và Email.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (api.contact && typeof api.contact.submitLead === 'function') {
        const planName = form.saasPlan === 'STARTER' ? 'Khởi Nghiệp (Starter)' : form.saasPlan === 'ENTERPRISE' ? 'Doanh Nghiệp (Enterprise)' : 'Chuyên Nghiệp (Pro)';
        const fullMessage = form.message.trim() 
          ? `[Gói quan tâm: ${planName}] ${form.message.trim()}`
          : `[Gói quan tâm: ${planName}] Yêu cầu tư vấn triển khai bán vé`;
        const res = await api.contact.submitLead({
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          companyName: form.orgName.trim() || 'Cá nhân',
          eventScale: form.saasPlan,
          message: fullMessage
        });
        setLeadId(res.id);
      }
      setSubmitted(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không gửi được yêu cầu tư vấn. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  }

  return h('div', { className: 'dedicated-page contact-page' },
    // Page Header / Hero
    h('section', { className: 'page-hero contact-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Liên hệ & Đặt lịch tư vấn')
        ),
        h('div', { className: 'hero-badge' }, '📞 KẾT NỐI TRỰC TIẾP VỚI ĐỘI NGŨ CHUYÊN GIA EVENTFLOW'),
        h('h1', { className: 'page-title' }, 'Liên Hệ Hợp Tác & Đặt Lịch Tư Vấn Doanh Nghiệp'),
        h('p', { className: 'page-subtitle' },
          'Chúng tôi cam kết đồng hành cùng các nhà tổ chức sự kiện xây dựng giải pháp phân phối vé ổn định, ' +
          'an toàn và mang lại trải nghiệm xuất sắc nhất cho khán giả.'
        )
      )
    ),

    // Main Contact Section (2 Columns)
    h('section', { className: 'contact-main-section shell' },
      h('div', { className: 'contact-layout-grid' },
        // Left Column: Direct Info
        h('div', { className: 'contact-info-col' },
          h('h3', { className: 'col-title' }, 'Thông Tin Văn Phòng & Tổng Đài Hỗ Trợ'),
          h('p', { className: 'col-sub' }, 'Mọi yêu cầu tư vấn kỹ thuật và hợp tác thương mại được tiếp nhận và phản hồi nhanh chóng.'),

          h('div', { className: 'contact-cards-list' },
            h('div', { className: 'contact-card-item' },
              h('div', { className: 'card-icon' }, '🏢'),
              h('div', { className: 'card-text' },
                h('strong', null, 'Trụ sở chính (Hà Nội)'),
                h('p', null, 'Tầng 8, Tòa nhà EventFlow Tower, Duy Tân, Cầu Giấy, Hà Nội'),
                h('small', null, 'Giờ làm việc: 8:00 – 18:00 (Thứ Hai – Thứ Bảy)')
              )
            ),

            h('div', { className: 'contact-card-item' },
              h('div', { className: 'card-icon' }, '🌆'),
              h('div', { className: 'card-text' },
                h('strong', null, 'Chi nhánh TP. Hồ Chí Minh'),
                h('p', null, 'Tầng 12, Tòa nhà Sailing Tower, Pasteur, Phường Bến Nghé, Quận 1, TP. HCM'),
                h('small', null, 'Trung tâm hỗ trợ kỹ thuật On-site khu vực phía Nam')
              )
            ),

            h('div', { className: 'contact-card-item highlight-item' },
              h('div', { className: 'card-icon' }, '📞'),
              h('div', { className: 'card-text' },
                h('strong', null, 'Tổng đài Hotline 24/7 (Miễn phí)'),
                h('a', { href: 'tel:19006868', className: 'phone-link' }, '1900 6868'),
                h('span', null, ' · Khẩn cấp sự kiện: 024 7300 6868')
              )
            ),

            h('div', { className: 'contact-card-item' },
              h('div', { className: 'card-icon' }, '✉️'),
              h('div', { className: 'card-text' },
                h('strong', null, 'Hộp thư điện tử chính thức'),
                h('p', null, 'Tư vấn đối tác: contact@eventflow.vn'),
                h('p', null, 'Phòng kinh doanh: sales@eventflow.vn')
              )
            ),

            h('div', { className: 'sla-guarantee-box' },
              h('span', { className: 'sla-icon' }, '⚡'),
              h('div', null,
                h('strong', null, 'Cam Kết Phản Hồi Trong 15 Phút'),
                h('p', null, 'Chuyên viên kỹ thuật và tư vấn giải pháp sẽ liên hệ trực tiếp với bạn ngay khi nhận được thông tin.')
              )
            )
          )
        ),

        // Right Column: Interactive Consultation Form
        h('div', { className: 'contact-form-col' },
          h('div', { className: 'form-box-card' },
            h('h3', null, 'Đặt Lịch Tư Vấn Triển Khai'),
            h('p', { className: 'form-intro' }, 'Điền thông tin sự kiện để nhận phương án kỹ thuật và báo giá tối ưu nhất:'),

            error && h('div', { role: 'alert', className: 'alert error' }, error),

            submitted ? h('div', { className: 'submission-success' },
              h('div', { className: 'success-badge' }, '✓'),
              h('h4', null, 'Gửi Yêu Cầu Tư Vấn Thành Công!'),
              h('p', null, 'Cảm ơn quý đối tác đã tin tưởng EventFlow. Chuyên viên tư vấn phụ trách sự kiện sẽ liên hệ qua điện thoại trong vòng 15 phút.'),
              leadId && h('div', { className: 'lead-reference' }, 'Mã hồ sơ: ' + leadId),
              h('div', { className: 'success-actions' },
                h('button', {
                  type: 'button',
                  className: 'hero-cta-coral',
                  onClick: () => {
                    setSubmitted(false);
                    setForm({ fullName: '', orgName: '', email: '', phone: '', eventType: 'CONCERT', saasPlan: parseSelectedPackage(currentPath), message: '' });
                  }
                }, 'Gửi thêm yêu cầu khác'),
                h('button', {
                  type: 'button',
                  className: 'hero-cta-secondary',
                  onClick: () => onNavigate('/dat-ve')
                }, 'Xem hệ thống đặt vé')
              )
            ) : h('form', { onSubmit: handleSubmit, className: 'consultation-form' },
              h('div', { className: 'form-row' },
                h('div', { className: 'form-group' },
                  h('label', { htmlFor: 'contact-fullname' }, 'Họ và tên người liên hệ *'),
                  h('input', {
                    id: 'contact-fullname',
                    type: 'text',
                    required: true,
                    placeholder: 'Ví dụ: Nguyễn Văn An',
                    value: form.fullName,
                    onChange: (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, fullName: e.target.value })
                  })
                ),
                h('div', { className: 'form-group' },
                  h('label', { htmlFor: 'contact-phone' }, 'Số điện thoại liên hệ *'),
                  h('input', {
                    id: 'contact-phone',
                    type: 'tel',
                    required: true,
                    placeholder: 'Ví dụ: 0912 345 678',
                    value: form.phone,
                    onChange: (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, phone: e.target.value })
                  })
                )
              ),

              h('div', { className: 'form-row' },
                h('div', { className: 'form-group' },
                  h('label', { htmlFor: 'contact-email' }, 'Email doanh nghiệp / cá nhân *'),
                  h('input', {
                    id: 'contact-email',
                    type: 'email',
                    required: true,
                    placeholder: 'an.nguyen@company.vn',
                    value: form.email,
                    onChange: (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, email: e.target.value })
                  })
                ),
                h('div', { className: 'form-group' },
                  h('label', { htmlFor: 'contact-org' }, 'Tên công ty / Đơn vị tổ chức'),
                  h('input', {
                    id: 'contact-org',
                    type: 'text',
                    placeholder: 'Công ty Cổ phần Giải trí ABC',
                    value: form.orgName,
                    onChange: (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, orgName: e.target.value })
                  })
                )
              ),

              h('div', { className: 'form-group' },
                h('label', { htmlFor: 'contact-saas-plan' }, 'Gói giải pháp dịch vụ quan tâm *'),
                h('select', {
                  id: 'contact-saas-plan',
                  value: form.saasPlan,
                  onChange: (e: ChangeEvent<HTMLSelectElement>) => setForm({ ...form, saasPlan: e.target.value })
                },
                  h('option', { value: 'STARTER' }, '🌱 Gói Khởi Nghiệp (Starter) — Phí sàn 8% / vé bán thành công (Tối đa 500 vé)'),
                  h('option', { value: 'PRO' }, '🔥 Gói Chuyên Nghiệp (Pro) — Phí sàn 5% / vé bán thành công (Sơ đồ ghế 3D, Zero Oversell)'),
                  h('option', { value: 'ENTERPRISE' }, '🏢 Gói Doanh Nghiệp (Enterprise) — Phí sàn 3% / vé bán thành công (Cluster riêng, SLA 99.99%)')
                )
              ),

              h('div', { className: 'form-group' },
                h('label', { htmlFor: 'contact-event-type' }, 'Loại hình sự kiện dự kiến'),
                h('select', {
                  id: 'contact-event-type',
                  value: form.eventType,
                  onChange: (e: ChangeEvent<HTMLSelectElement>) => setForm({ ...form, eventType: e.target.value })
                },
                  h('option', { value: 'CONCERT' }, '🎸 Đại nhạc hội / Concert / Lễ hội âm nhạc (5.000 - 50.000 vé)'),
                  h('option', { value: 'THEATER' }, '🎻 Nhà hát / Thính phòng / Vở kịch (Sơ đồ ghế chỉ định)'),
                  h('option', { value: 'CONFERENCE' }, '💡 Hội nghị Công nghệ / Diễn đàn Kinh tế (Soát vé VIP)'),
                  h('option', { value: 'SPORTS' }, '⚽ Sự kiện Thể thao / Giải đấu (Khán đài A, B, C, D)'),
                  h('option', { value: 'OTHER' }, '🎪 Triển lãm / Lễ hội văn hóa / Khác')
                )
              ),

              h('div', { className: 'form-group' },
                h('label', { htmlFor: 'contact-message' }, 'Quy mô dự kiến & Ghi chú yêu cầu'),
                h('textarea', {
                  id: 'contact-message',
                  rows: 4,
                  placeholder: 'Mô tả số lượng khán giả dự kiến, địa điểm tổ chức, thời gian mở bán hoặc các yêu cầu kỹ thuật đặc biệt...',
                  value: form.message,
                  onChange: (e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, message: e.target.value })
                })
              ),

              h('button', {
                type: 'submit',
                disabled: loading,
                className: 'submit-lead-btn'
              }, loading ? 'Đang gửi thông tin...' : 'Gửi yêu cầu tư vấn ngay →')
            )
          )
        )
      )
    )
  );
}
