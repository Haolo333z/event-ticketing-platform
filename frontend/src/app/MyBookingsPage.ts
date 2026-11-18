import { createElement as h, useState, useEffect } from 'react';
import type { Api, Booking, User, Ticket, Event } from '../api/client.ts';
import { QrCodeSvg } from './QrCodeSvg.ts';

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
  const [qrMap, setQrMap] = useState<Record<string, string>>({});
  const [eventDetail, setEventDetail] = useState<Event | null>(null);

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

  // Load event details for selected booking
  useEffect(() => {
    if (!selectedBooking) {
      setEventDetail(null);
      return;
    }
    const eventId = (selectedBooking as { eventId?: string }).eventId;
    if (!eventId) return;

    let active = true;
    api.detail(eventId)
      .then(ev => {
        if (active) setEventDetail(ev);
      })
      .catch(() => {
        if (active) setEventDetail(null);
      });
    return () => { active = false; };
  }, [selectedBooking, api]);

  // Load tickets and fetch HMAC-SHA256 Signed QR Code tokens
  useEffect(() => {
    if (!selectedBooking || selectedBooking.status !== 'CONFIRMED') {
      setTickets([]);
      setQrMap({});
      return;
    }
    let active = true;
    setTicketsLoading(true);
    api.tickets(selectedBooking.bookingId)
      .then(async items => {
        if (!active) return;
        setTickets(items);

        // Fetch signed QR tokens for each ticket
        const map: Record<string, string> = {};
        await Promise.allSettled(
          items.map(async t => {
            try {
              if (api.ticketQr) {
                const qrRes = await api.ticketQr(t.id);
                if (qrRes && qrRes.qrToken) {
                  map[t.id] = qrRes.qrToken;
                }
              }
            } catch {
              // Fallback to code if qr endpoint fails
            }
          })
        );
        if (active) {
          setQrMap(map);
        }
      })
      .catch(() => {
        if (active) {
          setTickets([]);
          setQrMap({});
        }
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
        h('div', { className: 'hero-badge' }, '🎟️ VÍ VÉ ĐIỆN TỬ & MÃ SOÁT VÉ QR CODE KÝ SỐ HMAC-SHA256'),
        h('h1', { className: 'page-title' }, 'Vé Điện Tử Của Tôi (My E-Tickets)'),
        h('p', { className: 'page-subtitle' },
          'Toàn bộ vé sự kiện và chỗ ngồi đã thanh toán của bạn. ' +
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
            h('div', { className: 'boarding-pass printable-pass' },
              h('div', { className: 'boarding-pass-notch top-notch' }),
              h('div', { className: 'pass-header' },
                h('div', { className: 'pass-brand' },
                  h('strong', null, 'EVENTFLOW BOARDING PASS'),
                  h('span', null, 'CỔNG SOÁT VÉ ĐIỆN TỬ THÔNG MINH')
                ),
                h('div', { className: 'pass-code' }, selectedBooking.bookingId.slice(0, 13).toUpperCase())
              ),

              h('div', { className: 'pass-body' },
                // Event information banner
                eventDetail && h('div', { className: 'pass-event-highlight' },
                  h('div', { className: 'event-badge-label' }, '🎵 THÔNG TIN SỰ KIỆN'),
                  h('h2', { className: 'event-headline-title' }, eventDetail.title),
                  h('div', { className: 'event-venue-time' },
                    h('span', { className: 'venue-span' }, '📍 ' + eventDetail.venue),
                    h('span', { className: 'time-span' }, '⏰ ' + date(eventDetail.startsAt))
                  )
                ),

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
                    h('span', { className: 'field-label' }, 'TRẠNG THÁI ĐƠN HÀNG'),
                    h('strong', { className: 'field-value status-text' }, labels[selectedBooking.status] ?? selectedBooking.status)
                  )
                ),

                // Security guarantee tag
                selectedBooking.status === 'CONFIRMED' && h('div', { className: 'pass-security-seal' },
                  h('span', { className: 'seal-badge' }, '🛡️ BẢO MẬT CHỮ KÝ SỐ'),
                  h('span', { className: 'seal-text' }, 'Mã vé ký số HMAC-SHA256, xác thực tức thì tại cổng check-in và chống quay vòng mã.')
                ),

                // Issued Tickets List (Vector SVG QR Codes)
                selectedBooking.status === 'CONFIRMED' ? (
                  ticketsLoading ? h('p', { className: 'muted loading-pulse' }, 'Đang nạp mã vé định danh và chữ ký số…') :
                  tickets.length > 0 ? h('div', { className: 'issued-tickets-list' },
                    tickets.map((t, idx) => {
                      const qrValue = qrMap[t.id] || t.code;
                      return h('div', { key: t.code, className: 'issued-ticket-row' },
                        h('div', { className: 'ticket-qr-box' },
                          h(QrCodeSvg, {
                            value: qrValue,
                            size: 140,
                            ariaLabel: 'Mã QR soát vé ' + t.code
                          })
                        ),
                        h('div', { className: 'ticket-meta-info' },
                          h('div', { className: 'ticket-number-tag' }, 'Vé #' + (idx + 1) + (t.seatLabel ? ' — Ghế: ' + t.seatLabel : ' — Vé GA')),
                          h('div', { className: 'ticket-unique-code' }, t.code),
                          h('div', { className: 'ticket-qr-status' },
                            qrMap[t.id] ? h('span', { className: 'badge-verified' }, '✓ ĐÃ KÝ SỐ BẢO MẬT') : h('span', { className: 'badge-pending' }, 'ĐANG NẠP MÃ QR…')
                          ),
                          h('small', { className: 'ticket-hint' }, 'Xuất trình mã QR này trực tiếp trên điện thoại hoặc bản in cho nhân viên soát vé tại cửa.')
                        )
                      );
                    })
                  ) : h('div', { className: 'issued-ticket-row' },
                    h('div', { className: 'ticket-qr-box' },
                      h(QrCodeSvg, {
                        value: 'EF-' + selectedBooking.bookingId.toUpperCase(),
                        size: 140,
                        ariaLabel: 'Mã QR soát vé'
                      })
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
                  selectedBooking.status === 'CONFIRMED' && h('button', {
                    type: 'button',
                    className: 'print-pass-btn',
                    onClick: () => window.print()
                  }, '🖨️ In Vé Điện Tử (Print Pass)'),
                  selectedBooking.status === 'CONFIRMED' && eventDetail && api.v3?.calendarUrl && h('a', {
                    href: api.v3.calendarUrl(eventDetail.id),
                    download: 'eventflow-' + eventDetail.id.slice(0, 8) + '.ics',
                    className: 'order-more-btn ics-download-btn'
                  }, '📅 Thêm vào Lịch (.ics)'),
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
