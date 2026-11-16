import { createElement as h } from 'react';

export function SolutionsPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  return h('div', { className: 'dedicated-page solutions-page' },
    // Page Header / Hero Banner
    h('section', { className: 'page-hero solutions-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Giải pháp doanh nghiệp')
        ),
        h('div', { className: 'hero-badge' }, '★ HẠ TẦNG VẬN HÀNH SỰ KIỆN DOANH NGHIỆP TOÀN DIỆN'),
        h('h1', { className: 'page-title' }, 'Hạ Tầng Bán Vé Tốc Độ Cao Định Vị Đẳng Cấp Sự Kiện'),
        h('p', { className: 'page-subtitle' },
          'Giải pháp công nghệ chuyên sâu cho các nhà tổ chức sự kiện chuyên nghiệp: Phân phối vé mở bán quy mô lớn, ' +
          'quản trị sơ đồ ghế realtime chính xác 100%, bảo đảm tuyệt đối 0% bán lố (Zero Oversell) bằng kiến trúc phân tán hướng sự kiện EDA.'
        ),
        h('div', { className: 'hero-cta-group' },
          h('button', {
            type: 'button',
            className: 'hero-cta-coral',
            onClick: () => onNavigate('/lien-he')
          }, 'Đặt lịch tư vấn giải pháp ngay →'),
          h('button', {
            type: 'button',
            className: 'hero-cta-secondary',
            onClick: () => onNavigate('/dat-ve')
          }, 'Trải nghiệm hệ thống đặt vé ↓')
        )
      )
    ),

    // Showcase Images Section
    h('section', { className: 'solutions-media-showcase shell' },
      h('div', { className: 'media-grid' },
        h('div', { className: 'media-card featured' },
          h('img', {
            src: 'https://images.unsplash.com/photo-1557804506-669a67965ba0?w=1200&auto=format&fit=crop&q=80',
            alt: 'Đội ngũ chuyên gia EventFlow tư vấn giải pháp vận hành sự kiện lớn',
            className: 'showcase-img'
          }),
          h('div', { className: 'media-caption' },
            h('strong', null, 'Đội ngũ kỹ sư & Chuyên gia On-site'),
            h('p', null, 'Trực chiến 24/7 tại trung tâm điều hành và tại cổng sự kiện để đảm bảo hệ thống vận hành thông suốt.')
          )
        ),
        h('div', { className: 'media-card' },
          h('img', {
            src: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80',
            alt: 'Đại nhạc hội quy mô 50.000 khán giả mở bán đồng loạt',
            className: 'showcase-img'
          }),
          h('div', { className: 'media-caption' },
            h('strong', null, 'Đại nhạc hội & Concert'),
            h('p', null, 'Sẵn sàng chịu tải hàng chục ngàn lượt mua vé đồng thời tại đúng thời điểm mở bán.')
          )
        ),
        h('div', { className: 'media-card' },
          h('img', {
            src: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&auto=format&fit=crop&q=80',
            alt: 'Nhà hát và nghệ thuật thính phòng',
            className: 'showcase-img'
          }),
          h('div', { className: 'media-caption' },
            h('strong', null, 'Nhà hát & Thính phòng Hàn lâm'),
            h('p', null, 'Quản lý ghế chỉ định chính xác từng vị trí, phân tầng khán phòng theo chuẩn quốc tế.')
          )
        )
      )
    ),

    // 3 Detailed Industry Solutions
    h('section', { className: 'solutions-sectors-section shell' },
      h('div', { className: 'section-heading center' },
        h('h2', null, '3 Phân Khúc Giải Pháp Chuyên Sâu Theo Ngành'),
        h('p', { className: 'section-sub' }, 'Mỗi loại hình sự kiện có đặc thù vận hành riêng biệt. EventFlow được thiết kế may đo cho từng bài toán.')
      ),
      h('div', { className: 'sectors-container' },
        // Sector 1: Concert
        h('div', { className: 'sector-row' },
          h('div', { className: 'sector-content' },
            h('div', { className: 'sector-badge' }, '🎸 ĐẠI NHẠC HỘI & LỄ HỘI ÂM NHẠC'),
            h('h3', null, 'Chịu tải 50.000 vé/phút — Chống nghẽn F5 & Chống đầu cơ'),
            h('p', null,
              'Các đợt mở bán vé concert lớn thường tạo ra cơn sốt truy cập khổng lồ gây sập hạ tầng truyền thống. ' +
              'EventFlow ứng dụng hàng đợi thông minh kết hợp cơ chế khóa hàng vi mô ACID PostgreSQL, đảm bảo xử lý công bằng, ' +
              'tuyệt đối không bị bán lố (Zero Oversell) dù lưu lượng tăng vọt hàng trăm lần.'
            ),
            h('ul', { className: 'sector-feature-list' },
              h('li', null, '✓ Khả năng mở rộng tự động (Elastic Auto-scaling) tức thì'),
              h('li', null, '✓ Cơ chế phân bổ vé GA nguyên tử, kiểm tra sức chứa chặt chẽ'),
              h('li', null, '✓ Giới hạn tối đa 6 vé/người mua nhằm ngăn chặn bot đầu cơ chợ đen'),
              h('li', null, '✓ Hỗ trợ hàng chờ ảo (Virtual Waiting Room) thông minh')
            ),
            h('button', {
              type: 'button',
              className: 'sector-cta-btn',
              onClick: () => onNavigate('/lien-he')
            }, 'Tư vấn giải pháp Concert →')
          ),
          h('div', { className: 'sector-visual' },
            h('img', {
              src: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
              alt: 'Đại nhạc hội quy mô lớn',
              className: 'sector-img'
            })
          )
        ),

        // Sector 2: Theater
        h('div', { className: 'sector-row reverse' },
          h('div', { className: 'sector-content' },
            h('div', { className: 'sector-badge' }, '🎻 NHÀ HÁT & NGHỆ THUẬT HÀN LÂM'),
            h('h3', null, 'Sơ đồ phân tầng khán phòng độc quyền — Giữ ghế chính xác 100%'),
            h('p', null,
              'Khán giả của các chương trình nghệ thuật hàn lâm, kịch nghệ và hòa nhạc cổ điển đòi hỏi trải nghiệm chọn chỗ tỉ mỉ. ' +
              'EventFlow cung cấp công cụ xây dựng sơ đồ rạp hát trực quan, phân định rõ khu vực SÂN KHẤU CHÍNH, Hàng ghế VIP, ' +
              'và các tầng lửng với màu sắc trạng thái ghế cập nhật thời gian thực.'
            ),
            h('ul', { className: 'sector-feature-list' },
              h('li', null, '✓ Sơ đồ ghế tương tác trực quan chuẩn tỷ lệ thực tế'),
              h('li', null, '✓ Khóa giữ ghế tạm thời 10 phút để người mua thanh toán thong thả'),
              h('li', null, '✓ Tự động thu hồi ghế quá hạn bằng Expiry Worker tự động'),
              h('li', null, '✓ Cấm tuyệt đối tình trạng hai người cùng mua một ghế')
            ),
            h('button', {
              type: 'button',
              className: 'sector-cta-btn',
              onClick: () => onNavigate('/lien-he')
            }, 'Tư vấn giải pháp Nhà hát →')
          ),
          h('div', { className: 'sector-visual' },
            h('img', {
              src: 'https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?w=800&auto=format&fit=crop&q=80',
              alt: 'Khán phòng nhà hát sang trọng',
              className: 'sector-img'
            })
          )
        ),

        // Sector 3: Conference
        h('div', { className: 'sector-row' },
          h('div', { className: 'sector-content' },
            h('div', { className: 'sector-badge' }, '💡 HỘI NGHỊ CÔNG NGHỆ & TRIỂN LÃM'),
            h('h3', null, 'Soát vé mã QR đa cổng — Phân luồng khách VIP tốc độ cao'),
            h('p', null,
              'Tại các hội nghị quy mô hàng ngàn chuyên gia và đại biểu VIP, tốc độ check-in tại cổng quyết định ấn tượng đầu tiên. ' +
              'Vé điện tử của EventFlow được định dạng Boarding Pass với mã QR Vector SVG sắc nét, quét được ngay trên mọi màn hình điện thoại.'
            ),
            h('ul', { className: 'sector-feature-list' },
              h('li', null, '✓ Tốc độ xác thực QR Code dưới 0.3 giây/lượt khách'),
              h('li', null, '✓ Chống quay vòng vé (Anti-passback) và chống quét trùng lặp'),
              h('li', null, '✓ Đồng bộ dữ liệu điểm danh đại biểu thời gian thực về Dashboard'),
              h('li', null, '✓ Tùy chỉnh thông tin vé và thương hiệu của nhà tài trợ')
            ),
            h('button', {
              type: 'button',
              className: 'sector-cta-btn',
              onClick: () => onNavigate('/lien-he')
            }, 'Tư vấn giải pháp Hội nghị →')
          ),
          h('div', { className: 'sector-visual' },
            h('img', {
              src: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&auto=format&fit=crop&q=80',
              alt: 'Hội nghị công nghệ quy mô lớn',
              className: 'sector-img'
            })
          )
        )
      )
    ),

    // Enterprise 4-Step Process Section
    h('section', { className: 'enterprise-process-section shell' },
      h('div', { className: 'section-heading center' },
        h('h2', null, 'Quy Trình Triển Khai Giải Pháp Doanh Nghiệp'),
        h('p', { className: 'section-sub' }, 'Chặt chẽ, chuyên nghiệp và đồng hành từ khâu chuẩn bị đến khi sự kiện bế mạc.')
      ),
      h('div', { className: 'enterprise-steps-grid' },
        h('div', { className: 'enterprise-step-card' },
          h('div', { className: 'step-num' }, '01'),
          h('h4', null, 'Khảo sát & Thiết kế'),
          h('p', null, 'Đánh giá quy mô, đặc thù khán phòng, cấu hình loại vé GA/Ghế và kịch bản mở bán.')
        ),
        h('div', { className: 'enterprise-step-card' },
          h('div', { className: 'step-num' }, '02'),
          h('h4', null, 'Tích hợp & Kiểm thử tải'),
          h('p', null, 'Kết nối cổng thanh toán, thiết lập bảo mật và chạy diễn tập mô phỏng lưu lượng cao.')
        ),
        h('div', { className: 'enterprise-step-card' },
          h('div', { className: 'step-num' }, '03'),
          h('h4', null, 'Mở bán & Giám sát Live'),
          h('p', null, 'Mở bán vé chính thức với dashboard giám sát độ trễ, lưu lượng và doanh thu từng giây.')
        ),
        h('div', { className: 'enterprise-step-card' },
          h('div', { className: 'step-num' }, '04'),
          h('h4', null, 'Soát vé & Đối soát'),
          h('p', null, 'Vận hành máy quét QR tại cổng, hỗ trợ khách tại chỗ và đối soát quyết toán minh bạch.')
        )
      )
    ),

    // Final CTA Box
    h('section', { className: 'solutions-bottom-cta shell' },
      h('div', { className: 'bottom-cta-inner' },
        h('h2', null, 'Sẵn Sàng Nâng Tầm Trải Nghiệm Bán Vé Của Bạn?'),
        h('p', null, 'Liên hệ ngay với đội ngũ chuyên gia của EventFlow để được tư vấn kịch bản mở bán tối ưu nhất.'),
        h('div', { className: 'cta-action-buttons' },
          h('button', {
            type: 'button',
            className: 'hero-cta-coral',
            onClick: () => onNavigate('/lien-he')
          }, 'Đặt lịch tư vấn trực tiếp →'),
          h('button', {
            type: 'button',
            className: 'hero-cta-secondary',
            onClick: () => onNavigate('/bang-gia')
          }, 'Xem bảng giá dịch vụ')
        )
      )
    )
  );
}
