import { createElement as h, useState, useEffect } from 'react';
import type { Api, Booking, User, Ticket } from '../api/client.ts';

const labels: Record<string, string> = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  CONFIRMED: 'Đã xác nhận',
  PAYMENT_FAILED: 'Thanh toán thất bại',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Đã hết hạn',
  REFUND_REQUIRED: 'Cần hoàn tiền'
};

const vnd = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
const date = (value: string) => new Date(value).toLocaleString('vi-VN');

export function MyBookingsPage({
  api,
  user,
  onOpenAuth,
  onNavigate,
  onSelectBooking
}: {
  api: Api;
  user: User | null;
  onOpenAuth: () => void;
  onNavigate: (path: string) => void;
  onSelectBooking?: (id: string) => void;
}) {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [ticketsLoading, setTicketsLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState<string>('ALL');

  async function loadBookings() {
    if (!user) return;
    setLoading(true);
    setError(null);
    try {
      const res = await api.mine();
      setBookings(res.items);
      if (res.items.length > 0 && !selectedBooking) {
        setSelectedBooking(res.items[0]);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không tải được danh sách vé.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (user) {
      void loadBookings();
    }
  }, [user]);

  useEffect(() => {
    if (!selectedBooking || selectedBooking.status !== 'CONFIRMED') {
      setTickets([]);
      return;
    }
    let active = true;
    setTicketsLoading(true);
    api.tickets(selectedBooking.bookingId)
      .then(items => {
        if (active) setTickets(items);
      })
      .catch(() => {
        if (active) setTickets([]);
      })
      .finally(() => {
        if (active) setTicketsLoading(false);
      });
    return () => { active = false; };
  }, [selectedBooking, api]);

  const filteredBookings = bookings.filter(b => {
    if (filterStatus === 'ALL') return true;
    return b.status === filterStatus;
  });

  return h('div', { className: 'dedicated-page my-bookings-page' },
    // Header / Hero
    h('section', { className: 'page-hero my-bookings-hero full-bleed-hero' },
      h('div', { className: 'hero-content-wrapper shell' },
        h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
          h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => onNavigate('/') }, 'Trang chủ'),
          h('span', { className: 'breadcrumb-separator' }, ' / '),
          h('span', { className: 'breadcrumb-current' }, 'Vé của tôi')
        ),
        h('div', { className: 'hero-badge' }, '🎟️ QUẢN LÝ VÉ ĐIỆN TỬ & MÃ SOÁT VÉ QR CODE'),
        h('h1', { className: 'page-title' }, 'Vé Điện Tử Của Tôi (My E-Tickets)'),
        h('p', { className: 'page-subtitle' },
          'Toàn bộ vé sự kiện và chỗ ngồi đã xác nhận của bạn. ' +
          'Xuất trình mã QR Boarding Pass tại cửa kiểm soát hoặc in vé để tham dự sự kiện.'
        )
      )
    ),

    // Content Section
    h('section', { className: 'my-bookings-main shell' },
      !user ? h('div', { className: 'unauth-notice-card center' },
        h('div', { className: 'notice-icon' }, '🔒'),
        h('h3', null, 'Yêu Cầu Đăng Nhập'),
        h('p', null, 'Vui lòng đăng nhập tài khoản EventFlow để tra cứu và quản lý các vé điện tử bạn đã đặt.'),
        h('div', { className: 'notice-actions' },
          h('button', {
            type: 'button',
            className: 'hero-cta-coral',
            onClick: onOpenAuth
          }, 'Đăng nhập tài khoản ngay →'),
          h('button', {
            type: 'button',
            className: 'hero-cta-secondary',
            onClick: () => onNavigate('/dat-ve')
          }, 'Khám phá sự kiện mở bán')
        )
      ) : loading ? h('div', { className: 'loading-state-box' },
        h('p', { role: 'status' }, 'Đang tải danh sách vé điện tử từ hệ thống…')
      ) : error ? h('div', { role: 'alert', className: 'alert error' },
        h('strong', null, 'Không thể tải danh sách vé'),
        h('p', null, error),
        h('button', { type: 'button', onClick: loadBookings, className: 'quiet' }, 'Thử lại')
      ) : bookings.length === 0 ? h('div', { className: 'empty-tickets-card center' },
        h('div', { className: 'empty-icon' }, '🎫'),
        h('h3', null, 'Bạn chưa có vé sự kiện nào'),
        h('p', null, 'Hãy khám phá các concert, đêm nhạc thính phòng và hội thảo công nghệ đang mở bán để đặt vé ngay hôm nay!'),
        h('button', {
          type: 'button',
          className: 'hero-cta-coral',
          onClick: () => onNavigate('/dat-ve')
        }, 'Khám phá và Đặt vé ngay →')
      ) : h('div', { className: 'bookings-management-layout' },
        // Left Column: Bookings list with status tabs
        h('div', { className: 'bookings-list-panel' },
          h('div', { className: 'bookings-filter-bar' },
            h('button', {
              type: 'button',
              className: 'tab-pill' + (filterStatus === 'ALL' ? ' active' : ''),
              onClick: () => setFilterStatus('ALL')
            }, 'Tất cả (' + bookings.length + ')'),
            h('button', {
              type: 'button',
              className: 'tab-pill' + (filterStatus === 'CONFIRMED' ? ' active' : ''),
              onClick: () => setFilterStatus('CONFIRMED')
            }, 'Đã xác nhận'),
            h('button', {
              type: 'button',
              className: 'tab-pill' + (filterStatus === 'PENDING_PAYMENT' ? ' active' : ''),
              onClick: () => setFilterStatus('PENDING_PAYMENT')
            }, 'Chờ thanh toán')
          ),

          h('div', { className: 'bookings-scroll-list' },
            filteredBookings.map(b => h('article', {
              key: b.bookingId,
              className: 'booking-item-card' + (selectedBooking?.bookingId === b.bookingId ? ' active' : ''),
              onClick: () => setSelectedBooking(b)
            },
              h('div', { className: 'card-top' },
                h('span', { className: 'booking-code' }, 'Mã đơn: ' + b.bookingId.slice(0, 8) + '…'),
                h('span', { className: 'booking-status-tag status-' + b.status.toLowerCase() }, labels[b.status] ?? b.status)
              ),
              h('div', { className: 'card-meta' },
                h('div', { className: 'meta-amount' }, vnd(b.totalAmountVnd)),
                h('div', { className: 'meta-date' }, 'Ngày tạo: ' + date(b.history?.[0]?.occurredAt ?? b.expiresAt))
              )
            ))
          )
        ),

        // Right Column: Full Boarding Pass View
        h('div', { className: 'booking-detail-panel' },
          selectedBooking && h('div', { className: 'ticket-inspection-view' },
            h('div', { className: 'inspection-header' },
              h('h3', null, 'Chi Tiết Vé Điện Tử'),
              h('span', { className: 'booking-status-tag status-' + selectedBooking.status.toLowerCase() },
                labels[selectedBooking.status] ?? selectedBooking.status
              )
            ),

            // Boarding Pass Ticket Container
            h('div', { className: 'boarding-pass' },
              h('div', { className: 'boarding-pass-notch top-notch' }),
              h('div', { className: 'pass-header' },
                h('div', { className: 'pass-brand' },
                  h('strong', null, 'EVENTFLOW BOARDING PASS'),
                  h('span', null, 'CỔNG CHECK-IN ĐIỆN TỬ')
                ),
                h('div', { className: 'pass-code' }, selectedBooking.bookingId.slice(0, 13).toUpperCase())
              ),

              h('div', { className: 'pass-body' },
                h('div', { className: 'pass-info-grid' },
                  h('div', { className: 'pass-field' },
                    h('span', { className: 'field-label' }, 'HỌ VÀ TÊN KHÁN GIẢ'),
                    h('strong', { className: 'field-value' }, user.displayName)
                  ),
                  h('div', { className: 'pass-field' },
                    h('span', { className: 'field-label' }, 'EMAIL ĐĂNG KÝ'),
                    h('strong', { className: 'field-value' }, user.email)
                  ),
                  h('div', { className: 'pass-field' },
                    h('span', { className: 'field-label' }, 'TỔNG TIỀN THANH TOÁN'),
                    h('strong', { className: 'field-value' }, vnd(selectedBooking.totalAmountVnd))
                  ),
                  h('div', { className: 'pass-field' },
                    h('span', { className: 'field-label' }, 'TRẠNG THÁI VÉ'),
                    h('strong', { className: 'field-value status-text' }, labels[selectedBooking.status] ?? selectedBooking.status)
                  )
                ),

                // Issued Tickets List (QR Codes)
                selectedBooking.status === 'CONFIRMED' ? (
                  ticketsLoading ? h('p', { className: 'muted' }, 'Đang nạp mã vé định danh…') :
                  tickets.length > 0 ? h('div', { className: 'issued-tickets-list' },
                    tickets.map((t, idx) => h('div', { key: t.code, className: 'issued-ticket-row' },
                      h('div', { className: 'ticket-qr-box' },
                        h('svg', {
                          viewBox: '0 0 100 100',
                          className: 'qr-svg-code',
                          'aria-label': 'Mã QR soát vé ' + t.code
                        },
                          h('rect', { width: '100', height: '100', fill: '#ffffff' }),
                          h('path', {
                            d: 'M10 10h30v30h-30zM15 15h20v20h-20zM60 10h30v30h-30zM65 15h20v20h-20zM10 60h30v30h-30zM15 65h20v20h-20zM45 15h10v10h-10zM45 35h10v10h-10zM15 45h10v10h-10zM35 45h10v10h-10zM55 45h10v10h-10zM75 45h10v10h-10zM45 55h10v10h-10zM65 55h10v10h-10zM45 75h10v10h-10zM65 75h10v10h-10zM75 75h15v15h-15z',
                            fill: '#05122f'
                          })
                        )
                      ),
                      h('div', { className: 'ticket-meta-info' },
                        h('div', { className: 'ticket-number-tag' }, 'Vé #' + (idx + 1) + (t.seatLabel ? ' — Ghế: ' + t.seatLabel : ' — Vé GA')),
                        h('div', { className: 'ticket-unique-code' }, t.code),
                        h('small', { className: 'ticket-hint' }, 'Xuất trình mã này cho nhân viên soát vé tại cửa vào.')
                      )
                    ))
                  ) : h('div', { className: 'issued-ticket-row' },
                    h('div', { className: 'ticket-qr-box' },
                      h('svg', {
                        viewBox: '0 0 100 100',
                        className: 'qr-svg-code',
                        'aria-label': 'Mã QR soát vé'
                      },
                        h('rect', { width: '100', height: '100', fill: '#ffffff' }),
                        h('path', {
                          d: 'M10 10h30v30h-30zM15 15h20v20h-20zM60 10h30v30h-30zM65 15h20v20h-20zM10 60h30v30h-30zM15 65h20v20h-20zM45 15h10v10h-10zM45 35h10v10h-10zM15 45h10v10h-10zM35 45h10v10h-10zM55 45h10v10h-10zM75 45h10v10h-10zM45 55h10v10h-10zM65 55h10v10h-10zM45 75h10v10h-10zM65 75h10v10h-10zM75 75h15v15h-15z',
                          fill: '#05122f'
                        })
                      )
                    ),
                    h('div', { className: 'ticket-meta-info' },
                      h('div', { className: 'ticket-number-tag' }, 'Vé Điện Tử Hợp Lệ'),
                      h('div', { className: 'ticket-unique-code' }, 'EF-' + selectedBooking.bookingId.slice(0, 18).toUpperCase()),
                      h('small', { className: 'ticket-hint' }, 'Xuất trình mã QR tại cổng kiểm soát.')
                    )
                  )
                ) : selectedBooking.status === 'PENDING_PAYMENT' ? (
                  h('div', { className: 'pending-payment-warning' },
                    h('p', null, 'Đơn hàng đang chờ hoàn tất thanh toán mô phỏng.'),
                    onSelectBooking && h('button', {
                      type: 'button',
                      className: 'hero-cta-coral',
                      onClick: () => onSelectBooking(selectedBooking.bookingId)
                    }, 'Tiếp tục thanh toán ngay →')
                  )
                ) : null
              ),

              h('div', { className: 'boarding-pass-notch bottom-notch' }),
              h('div', { className: 'pass-footer' },
                h('div', { className: 'pass-actions' },
                  h('button', {
                    type: 'button',
                    className: 'print-pass-btn',
                    onClick: () => window.print()
                  }, '🖨️ In vé / Lưu PDF'),
                  h('button', {
                    type: 'button',
                    className: 'order-more-btn',
                    onClick: () => onNavigate('/dat-ve')
                  }, '🎟️ Mua thêm vé sự kiện khác')
                )
              )
            )
          )
        )
      )
    )
  );
}
