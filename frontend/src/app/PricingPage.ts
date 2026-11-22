import { createElement as h } from 'react';

export function PricingPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  return h('div', { className: 'dedicated-page pricing-page' },
    // Page Header / Hero Banner
    h('section', { className: 'page-hero pricing-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Bảng giá & Dịch vụ')
        ),
        h('div', { className: 'hero-badge' }, '💎 CHÍNH SÁCH GIÁ MINH BẠCH & TỐI ƯU HIỆU QUẢ ĐẦU TƯ'),
        h('h1', { className: 'page-title' }, 'Bảng Giá & So Sánh Các Gói Dịch Vụ Vận Hành'),
        h('p', { className: 'page-subtitle' },
          'Không phí khởi tạo, không chi phí ẩn, không phụ thuộc cam kết dài hạn. ' +
          'Chỉ thanh toán phí khi có booking vé thành công. Lựa chọn gói giải pháp phù hợp với quy mô sự kiện của bạn.'
        ),
        h('div', { className: 'hero-cta-group' },
          h('button', {
            type: 'button',
            className: 'hero-cta-coral',
            onClick: () => onNavigate('/lien-he?package=enterprise')
          }, 'Liên hệ nhận báo giá tùy biến →'),
          h('button', {
            type: 'button',
            className: 'hero-cta-secondary',
            onClick: () => onNavigate('/giai-phap')
          }, 'Tìm hiểu tính năng chi tiết')
        )
      )
    ),

    // 3 Pricing Cards
    h('section', { className: 'pricing-cards-section shell' },
      h('div', { className: 'pricing-grid' },
        // Card 1: Starter
        h('div', { className: 'pricing-card' },
          h('div', { className: 'plan-header' },
            h('span', { className: 'plan-type' }, 'SỰ KIỆN VỪA & NHỎ'),
            h('h3', null, 'Gói Khởi Nghiệp (Starter)'),
            h('p', { className: 'plan-desc' }, 'Dành cho hội thảo, workshop, triển lãm và biểu diễn acoustic quy mô ấm cúng.')
          ),
          h('div', { className: 'plan-price' },
            h('strong', null, '8%'),
            h('span', null, '/ vé bán thành công')
          ),
          h('div', { className: 'plan-free-tier' }, 'Miễn phí 100% đối với sự kiện phát vé miễn phí'),
          h('ul', { className: 'plan-features' },
            h('li', null, '✓ Quy mô: Tối đa 500 vé / sự kiện'),
            h('li', null, '✓ Hỗ trợ phân phối vé GA đứng'),
            h('li', null, '✓ Phát vé điện tử E-Ticket qua Email & QR Code'),
            h('li', null, '✓ Báo cáo doanh số bán vé cơ bản'),
            h('li', null, '✓ Hỗ trợ kỹ thuật qua Email trong giờ hành chính')
          ),
          h('button', {
            type: 'button',
            className: 'plan-btn starter-btn',
            onClick: () => onNavigate('/lien-he?package=starter')
          }, 'Chọn gói Khởi Nghiệp')
        ),

        // Card 2: Professional (Featured)
        h('div', { className: 'pricing-card featured' },
          h('div', { className: 'popular-badge' }, '🔥 PHỔ BIẾN NHẤT — BEST VALUE'),
          h('div', { className: 'plan-header' },
            h('span', { className: 'plan-type' }, 'NHÀ HÁT & ĐẠI NHẠC HỘI'),
            h('h3', null, 'Gói Chuyên Nghiệp (Pro)'),
            h('p', { className: 'plan-desc' }, 'Lựa chọn số 1 của các đơn vị biểu diễn âm nhạc, kịch nghệ và hội nghị quy mô lớn.')
          ),
          h('div', { className: 'plan-price' },
            h('strong', null, '5%'),
            h('span', null, '/ vé bán thành công')
          ),
          h('div', { className: 'plan-free-tier' }, 'Bao gồm bảo đảm Zero Oversell 100%'),
          h('ul', { className: 'plan-features' },
            h('li', null, '✓ Quy mô: Lên đến 10.000 vé / sự kiện'),
            h('li', null, '✓ Sơ đồ ghế Sân khấu (Stage Seat Map) realtime'),
            h('li', null, '✓ Đồng hồ đếm ngược giữ chỗ 10 phút chống tranh chấp'),
            h('li', null, '✓ Mã QR SVG chống chụp màn hình gian lận'),
            h('li', null, '✓ Báo cáo tồn kho & doanh thu tức thì theo giây'),
            h('li', null, '✓ Hỗ trợ trực tuyến và hotline 24/7 trong đợt mở bán')
          ),
          h('button', {
            type: 'button',
            className: 'plan-btn pro-btn',
            onClick: () => onNavigate('/lien-he?package=pro')
          }, 'Chọn gói Chuyên Nghiệp')
        ),

        // Card 3: Enterprise
        h('div', { className: 'pricing-card enterprise' },
          h('div', { className: 'plan-header' },
            h('span', { className: 'plan-type' }, 'TẬP ĐOÀN & FESTIVAL LỚN'),
            h('h3', null, 'Gói Doanh Nghiệp (Enterprise)'),
            h('p', { className: 'plan-desc' }, 'Hạ tầng máy chủ độc lập chuyên biệt cho các sự kiện quốc gia và lưu diễn quốc tế.')
          ),
          h('div', { className: 'plan-price' },
            h('strong', null, '3%'),
            h('span', null, '/ vé bán thành công')
          ),
          h('div', { className: 'plan-free-tier' }, 'Cam kết SLA 99.99% bằng hợp đồng pháp lý'),
          h('ul', { className: 'plan-features' },
            h('li', null, '✓ Quy mô: Không giới hạn (10.000 – 100.000+ vé)'),
            h('li', null, '✓ Cụm phân tán Dedicated Cluster riêng biệt'),
            h('li', null, '✓ Đội ngũ kỹ sư trực chiến On-site tại địa điểm sự kiện'),
            h('li', null, '✓ Tùy biến thương hiệu (White-label) 100% giao diện'),
            h('li', null, '✓ Tích hợp hệ thống CRM / ERP của doanh nghiệp'),
            h('li', null, '✓ Kiểm toán an ninh mạng & Bảo hiểm rủi ro giao dịch')
          ),
          h('button', {
            type: 'button',
            className: 'plan-btn enterprise-btn',
            onClick: () => onNavigate('/lien-he?package=enterprise')
          }, 'Liên hệ tư vấn riêng')
        )
      )
    ),

    // Comprehensive Comparison Matrix Table
    h('section', { className: 'pricing-matrix-section shell' },
      h('div', { className: 'section-heading center' },
        h('h2', null, 'Bảng So Sánh Chi Tiết Tính Năng Giữa Các Gói'),
        h('p', { className: 'section-sub' }, 'Đối soát minh bạch các hạng mục kỹ thuật, vận hành và bảo vệ dữ liệu.')
      ),
      h('div', { className: 'matrix-table-wrap' },
        h('table', { className: 'matrix-table' },
          h('thead', null,
            h('tr', null,
              h('th', { className: 'feature-col' }, 'Tính năng & Tiêu chuẩn'),
              h('th', null, 'Starter'),
              h('th', { className: 'highlight-col' }, 'Professional (Pro)'),
              h('th', null, 'Enterprise')
            )
          ),
          h('tbody', null,
            // Group 1: Scale & Traffic
            h('tr', { className: 'group-header' },
              h('td', { colSpan: 4 }, '1. QUY MÔ & KHẢ NĂNG CHỊU TẢI')
            ),
            h('tr', null,
              h('td', null, 'Sức chứa vé tối đa / sự kiện'),
              h('td', null, '500 vé'),
              h('td', { className: 'highlight-col' }, '10.000 vé'),
              h('td', null, 'Không giới hạn')
            ),
            h('tr', null,
              h('td', null, 'Lưu lượng đặt vé đồng thời'),
              h('td', null, '1.000 CCU'),
              h('td', { className: 'highlight-col' }, '20.000 CCU'),
              h('td', null, '100.000+ CCU (Dedicated)')
            ),
            h('tr', null,
              h('td', null, 'Cam kết Zero Oversell (Không bán lố)'),
              h('td', null, '✓ Chuẩn ACID'),
              h('td', { className: 'highlight-col' }, '✓ Chuẩn ACID'),
              h('td', null, '✓ Chuẩn ACID + Hợp đồng SLA')
            ),

            // Group 2: Ticketing Features
            h('tr', { className: 'group-header' },
              h('td', { colSpan: 4 }, '2. TRẢI NGHIỆM ĐẶT VÉ & SƠ ĐỒ CHỖ')
            ),
            h('tr', null,
              h('td', null, 'Bán vé phổ thông (GA đứng)'),
              h('td', null, '✓ Có'),
              h('td', { className: 'highlight-col' }, '✓ Có'),
              h('td', null, '✓ Có')
            ),
            h('tr', null,
              h('td', null, 'Sơ đồ ghế rạp hát (Seat Map Sân khấu)'),
              h('td', null, '—'),
              h('td', { className: 'highlight-col' }, '✓ Sân khấu chính + Phân hàng ghế'),
              h('td', null, '✓ Phân tầng 3D đa khán phòng')
            ),
            h('tr', null,
              h('td', null, 'Đồng hồ đếm ngược giữ chỗ 10 phút'),
              h('td', null, '—'),
              h('td', { className: 'highlight-col' }, '✓ Tự động thu hồi quá hạn'),
              h('td', null, '✓ Cấu hình thời gian tùy chỉnh')
            ),
            h('tr', null,
              h('td', null, 'Vé điện tử E-Ticket Boarding Pass'),
              h('td', null, 'Email cơ bản'),
              h('td', { className: 'highlight-col' }, '✓ Boarding Pass + Mã QR SVG'),
              h('td', null, '✓ Boarding Pass + Thiết kế riêng')
            ),

            // Group 3: Support & SLA
            h('tr', { className: 'group-header' },
              h('td', { colSpan: 4 }, '3. DỊCH VỤ HỖ TRỢ & VẬN HÀNH')
            ),
            h('tr', null,
              h('td', null, 'Kênh hỗ trợ kỹ thuật'),
              h('td', null, 'Email (Giờ hành chính)'),
              h('td', { className: 'highlight-col' }, 'Email + Hotline 24/7'),
              h('td', null, 'Đội ngũ Kỹ sư On-site tại sự kiện')
            ),
            h('tr', null,
              h('td', null, 'Thời gian đối soát doanh thu'),
              h('td', null, 'T+3 ngày làm việc'),
              h('td', { className: 'highlight-col' }, 'T+1 ngày làm việc'),
              h('td', null, 'Tức thì (Realtime Settlement)')
            ),
            h('tr', null,
              h('td', null, 'Tùy biến nhận diện thương hiệu'),
              h('td', null, 'Logo cơ bản'),
              h('td', { className: 'highlight-col' }, 'Tùy chỉnh màu sắc & Banner'),
              h('td', null, '✓ Toàn quyền White-label')
            )
          )
        )
      )
    ),

    // Pricing CTA Box
    h('section', { className: 'pricing-bottom-cta shell' },
      h('div', { className: 'bottom-cta-inner' },
        h('h2', null, 'Cần Giải Pháp Được Thiết Kế Riêng Cho Sự Kiện Của Bạn?'),
        h('p', null, 'Đội ngũ chuyên viên tư vấn tài chính và giải pháp kỹ thuật của chúng tôi sẵn sàng giải đáp mọi thắc mắc.'),
        h('button', {
          type: 'button',
          className: 'hero-cta-coral',
          onClick: () => onNavigate('/lien-he?package=pro')
        }, 'Liên hệ phòng kinh doanh ngay →')
      )
    )
  );
}
