import { createElement as h, useState, useEffect } from 'react';
import type { Api, Booking, User, Ticket, Event } from '../api/client.ts';
import { ApiError, clearStoredAuth } from '../api/client.ts';
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

function formatBookingDateTime(val?: string | null): string {
  if (!val) return '—';
  const d = new Date(val);
  if (isNaN(d.getTime())) return String(val);
  const pad = (n: number) => n < 10 ? '0' + n : String(n);
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = d.getFullYear();
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  return `${day}/${month}/${year} ${hours}:${minutes}`;
}

function getShortBookingCode(id: string): string {
  const clean = id.replace(/[^a-zA-Z0-9]/g, '');
  return '#EF-' + clean.slice(-6).toUpperCase();
}

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
  const [eventTitles, setEventTitles] = useState<Record<string, string>>({});
  const [currentInvoice, setCurrentInvoice] = useState<import('../api/client.ts').EInvoiceItem | null>(null);
  const [invoiceModalOpen, setInvoiceModalOpen] = useState(false);
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  const [invoiceForm, setInvoiceForm] = useState({ taxCode: '', companyName: '', companyAddress: '', recipientEmail: '' });
  const [invoiceError, setInvoiceError] = useState<string | null>(null);
  const [refundProgress, setRefundProgress] = useState<import('../api/client.ts').RefundProgress | null>(null);
  const [transferModalOpen, setTransferModalOpen] = useState(false);
  const [transferTargetTicket, setTransferTargetTicket] = useState<Ticket | null>(null);
  const [transferEmail, setTransferEmail] = useState('');
  const [transferNote, setTransferNote] = useState('');
  const [transferLoading, setTransferLoading] = useState(false);
  const [transferSuccess, setTransferSuccess] = useState<import('../api/client.ts').TransferItem | null>(null);
  const [transferError, setTransferError] = useState<string | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);
  const [resaleModalOpen, setResaleModalOpen] = useState(false);
  const [resaleTargetTicket, setResaleTargetTicket] = useState<Ticket | null>(null);
  const [resalePriceInput, setResalePriceInput] = useState<string>('');
  const [resaleLoading, setResaleLoading] = useState(false);
  const [resaleSuccess, setResaleSuccess] = useState<import('../api/client.ts').ResaleListingItem | null>(null);
  const [resaleError, setResaleError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedBooking || selectedBooking.status !== 'REFUND_REQUIRED' || !api.refunds?.getProgress) {
      setRefundProgress(null);
      return;
    }
    let active = true;
    api.refunds.getProgress(selectedBooking.bookingId)
      .then(prog => { if (active) setRefundProgress(prog); })
      .catch(() => { if (active) setRefundProgress(null); });
    return () => { active = false; };
  }, [selectedBooking, api]);

  useEffect(() => {
    if (!selectedBooking || selectedBooking.status !== 'CONFIRMED' || !api.invoices?.get) {
      setCurrentInvoice(null);
      return;
    }
    let active = true;
    api.invoices.get(selectedBooking.bookingId)
      .then(inv => { if (active) setCurrentInvoice(inv); })
      .catch(() => { if (active) setCurrentInvoice(null); });
    return () => { active = false; };
  }, [selectedBooking, api]);

  async function handleIssueInvoice() {
    if (!selectedBooking || !api.invoices?.issue) return;
    setInvoiceLoading(true);
    setInvoiceError(null);
    try {
      const inv = await api.invoices.issue(selectedBooking.bookingId, invoiceForm);
      setCurrentInvoice(inv);
    } catch (err: any) {
      setInvoiceError(err?.message || 'Không thể phát hành hóa đơn');
    } finally {
      setInvoiceLoading(false);
    }
  }

  async function handleTransferSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!transferTargetTicket || !api.transfers?.initiate) return;
    setTransferLoading(true);
    setTransferError(null);
    try {
      const res = await api.transfers.initiate({
        ticketId: transferTargetTicket.id,
        toEmail: transferEmail.trim(),
        note: transferNote.trim()
      });
      setTransferSuccess(res);
    } catch (err: any) {
      setTransferError(err?.message || 'Không thể tạo giao dịch tặng vé');
    } finally {
      setTransferLoading(false);
    }
  }

  async function handleResaleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!resaleTargetTicket || !api.resale?.create) return;
    const price = parseInt(resalePriceInput, 10);
    if (!price || price <= 0) {
      setResaleError('Giá bán lại phải lớn hơn 0');
      return;
    }
    setResaleLoading(true);
    setResaleError(null);
    try {
      const res = await api.resale.create({
        ticketId: resaleTargetTicket.id,
        askingPriceVnd: price
      });
      setResaleSuccess(res);
    } catch (err: any) {
      setResaleError(err?.message || 'Không thể đăng bán vé');
    } finally {
      setResaleLoading(false);
    }
  }

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
      try {
        const evRes = await api.events();
        if (evRes && evRes.items) {
          const map: Record<string, string> = {};
          for (const ev of evRes.items) {
            map[ev.id] = ev.title;
          }
          setEventTitles(prev => ({ ...prev, ...map }));
        }
      } catch {
        // events catalog fallback
      }
    } catch (err: unknown) {
      if (err instanceof ApiError && (err.status === 401 || err.code === 'UNAUTHENTICATED')) {
        clearStoredAuth();
        setError('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');
      } else {
        setError(err instanceof Error ? err.message : 'Không tải được danh sách vé.');
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (user) {
      void loadBookings();
    } else {
      setBookings([]);
      setError(null);
      setSelectedBooking(null);
      setTickets([]);
      setQrMap({});
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
        if (active) {
          setEventDetail(ev);
          setEventTitles(prev => ({ ...prev, [eventId]: ev.title }));
        }
      })
      .catch(() => {
        if (active) setEventDetail(null);
      });
    return () => { active = false; };
  }, [selectedBooking, api]);

  // Fetch missing event titles for any booking items
  useEffect(() => {
    if (!bookings.length) return;
    const missingIds = bookings
      .map(b => b.eventId)
      .filter((id): id is string => !!id && !eventTitles[id]);
    if (!missingIds.length) return;

    let active = true;
    for (const id of Array.from(new Set(missingIds))) {
      api.detail(id)
        .then(ev => {
          if (active && ev) {
            setEventTitles(prev => ({ ...prev, [id]: ev.title }));
          }
        })
        .catch(() => {});
    }
    return () => { active = false; };
  }, [bookings, eventTitles, api]);

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
      ) : error ? (
        (error.includes('hết hạn') || error.includes('Token không hợp lệ') || error.includes('UNAUTHENTICATED')) ? (
          h('div', { className: 'unauth-notice-card center' },
            h('div', { className: 'notice-icon' }, '🔒'),
            h('h3', null, 'Phiên Đăng Nhập Đã Hết Hạn'),
            h('p', null, 'Phiên làm việc của bạn đã hết hạn để đảm bảo an toàn. Vui lòng đăng nhập lại để tiếp tục quản lý vé điện tử.'),
            h('div', { className: 'notice-actions' },
              h('button', {
                type: 'button',
                className: 'hero-cta-coral',
                onClick: onOpenAuth
              }, '🔑 Đăng nhập lại ngay →'),
              h('button', {
                type: 'button',
                className: 'hero-cta-secondary',
                onClick: () => onNavigate('/dat-ve')
              }, 'Khám phá sự kiện mở bán')
            )
          )
        ) : (
          h('div', { role: 'alert', className: 'alert error' },
            h('strong', null, 'Không thể tải danh sách vé'),
            h('p', null, error),
            h('button', { type: 'button', onClick: loadBookings, className: 'quiet' }, 'Thử lại')
          )
        )
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
            filteredBookings.map(b => {
              const shortCode = getShortBookingCode(b.bookingId);
              const bookingDate = b.history?.[0]?.occurredAt ?? b.expiresAt;
              const eventTitle = (b.eventId && eventTitles[b.eventId])
                ? eventTitles[b.eventId]
                : (eventDetail && selectedBooking?.bookingId === b.bookingId ? eventDetail.title : 'Vé Sự Kiện');

              return h('article', {
                key: b.bookingId,
                className: 'booking-item-card' + (selectedBooking?.bookingId === b.bookingId ? ' active' : ''),
                onClick: () => setSelectedBooking(b)
              },
                h('div', { className: 'card-top' },
                  h('strong', { className: 'booking-event-name', title: eventTitle }, eventTitle),
                  h('span', { className: 'booking-status-tag status-' + b.status.toLowerCase() }, labels[b.status] ?? b.status)
                ),
                h('div', { className: 'card-middle' },
                  h('span', { className: 'booking-code-badge' }, shortCode),
                  h('span', { className: 'meta-date' }, '🗓️ ' + formatBookingDateTime(bookingDate))
                ),
                h('div', { className: 'card-bottom' },
                  h('span', { className: 'meta-price-label' }, 'Tổng thanh toán:'),
                  h('strong', { className: 'meta-amount' }, vnd(b.totalAmountVnd))
                )
              );
            })
          )
        ),

        // Right Column: Full Boarding Pass View
        h('div', { className: 'booking-detail-panel' },
          selectedBooking && h('div', { className: 'ticket-inspection-view' },
            h('div', { className: 'inspection-header' },
              h('div', null,
                h('h3', null, 'Chi Tiết Vé Điện Tử'),
                h('span', { className: 'inspection-sub-code' }, 'Mã đơn: ' + getShortBookingCode(selectedBooking.bookingId))
              ),
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
                h('div', { className: 'pass-code' }, getShortBookingCode(selectedBooking.bookingId))
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
                          h('small', { className: 'ticket-hint' }, 'Xuất trình mã QR này trực tiếp trên điện thoại hoặc bản in cho nhân viên soát vé tại cửa.'),
                          api.transfers && h('button', {
                            type: 'button',
                            className: 'ticket-transfer-trigger-btn',
                            onClick: () => {
                              setTransferTargetTicket(t);
                              setTransferEmail('');
                              setTransferNote('');
                              setTransferSuccess(null);
                              setTransferError(null);
                              setCopiedLink(false);
                              setTransferModalOpen(true);
                            }
                          }, '🎁 Gửi tặng vé cho bạn bè'),
                          api.resale && h('button', {
                            type: 'button',
                            className: 'ticket-transfer-trigger-btn',
                            style: { marginLeft: '8px', background: '#f8fafc', borderColor: '#cbd5e1', color: '#475569' },
                            onClick: () => {
                              setResaleTargetTicket(t);
                              const origPrice = Math.round(selectedBooking.totalAmountVnd / (tickets.length || 1));
                              setResalePriceInput(String(origPrice));
                              setResaleSuccess(null);
                              setResaleError(null);
                              setResaleModalOpen(true);
                            }
                          }, '🏷️ Bán lại (Resale)')
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
                ) : selectedBooking.status === 'REFUND_REQUIRED' ? (
                  h('div', { className: 'refund-progress-panel' },
                    h('div', { className: 'refund-panel-header' },
                      h('span', { className: 'refund-badge-tag' }, '💸 TIẾN TRÌNH HOÀN TIỀN MINH BẠCH'),
                      h('h4', null, 'Yêu Cầu Hoàn Tiền Đã Được Tiếp Nhận')
                    ),
                    h('p', { className: 'refund-amount-text' },
                      'Số tiền hoàn: ',
                      h('strong', null, vnd(refundProgress?.amountVnd || selectedBooking.totalAmountVnd)),
                      h('span', { className: 'refund-reason-tag' }, ' · ' + (refundProgress?.reason || 'Hết hạn giữ chỗ / Huỷ đơn'))
                    ),
                    h('div', { className: 'refund-stepper' },
                      (refundProgress?.steps || [
                        { stepNumber: 1, title: 'Tiếp nhận yêu cầu', status: 'COMPLETED', timestamp: date(selectedBooking.expiresAt), description: 'Hệ thống đã tự động ghi nhận nghĩa vụ hoàn tiền cho quý khách.' },
                        { stepNumber: 2, title: 'Đối soát & Phê duyệt', status: 'IN_PROGRESS', timestamp: 'Đang xử lý', description: 'Ban quản trị đang tiến hành đối soát tài chính định kỳ với ngân hàng.' },
                        { stepNumber: 3, title: 'Tiền về tài khoản / thẻ', status: 'PENDING', timestamp: 'Chờ duyệt', description: 'Số tiền sẽ được tự động hoàn về thẻ/tài khoản ngay sau khi phê duyệt.' }
                      ]).map(step => h('div', {
                        key: step.stepNumber,
                        className: 'refund-step-item ' + step.status.toLowerCase()
                      },
                        h('div', { className: 'step-indicator' },
                          step.status === 'COMPLETED' ? '✓' : step.status === 'IN_PROGRESS' ? '⏳' : step.status === 'REJECTED' ? '✕' : step.stepNumber
                        ),
                        h('div', { className: 'step-content' },
                          h('div', { className: 'step-title-row' },
                            h('strong', { className: 'step-title' }, step.title),
                            h('span', { className: 'step-timestamp' }, step.timestamp)
                          ),
                          h('p', { className: 'step-desc' }, step.description)
                        )
                      ))
                    )
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
                  selectedBooking.status === 'CONFIRMED' && h('button', {
                    type: 'button',
                    className: 'order-more-btn',
                    onClick: () => {
                      if (!currentInvoice && user) {
                        setInvoiceForm(f => ({ ...f, recipientEmail: f.recipientEmail || user.email }));
                      }
                      setInvoiceModalOpen(true);
                    }
                  }, currentInvoice ? '📄 Xem hóa đơn VAT' : '📄 Xuất hóa đơn VAT'),
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
      ),

      transferModalOpen && h('div', { className: 'auth-modal-backdrop', onClick: (e) => { if (e.target === e.currentTarget) setTransferModalOpen(false); } },
        h('div', { className: 'auth-modal-card', style: { maxWidth: '480px' } },
          h('div', { className: 'auth-modal-header' },
            h('div', null,
              h('h3', { style: { margin: 0, fontSize: '1.15rem', color: '#0f172a' } }, '🎁 Gửi tặng vé cho bạn bè'),
              h('p', { className: 'muted', style: { margin: '4px 0 0', fontSize: '0.82rem' } },
                transferTargetTicket ? `Mã vé: ${transferTargetTicket.code}${transferTargetTicket.seatLabel ? ' (Ghế ' + transferTargetTicket.seatLabel + ')' : ''}` : ''
              )
            ),
            h('button', { type: 'button', className: 'auth-close-btn', onClick: () => setTransferModalOpen(false) }, '×')
          ),
          transferError && h('div', { className: 'auth-error-alert' }, '⚠️ ', transferError),
          transferSuccess ? h('div', { style: { padding: '16px 0', display: 'flex', flexDirection: 'column', gap: '12px' } },
            h('div', { style: { background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '10px', padding: '12px', color: '#065f46' } },
              h('strong', null, '🎉 Đã tạo liên kết gửi tặng thành công!'),
              h('p', { style: { margin: '6px 0 0', fontSize: '0.85rem' } }, 'Hãy gửi link dưới đây cho bạn bè để họ nhận vé vào ví. Sau khi người nhận chấp nhận, mã QR cũ của bạn sẽ tự động vô hiệu hóa.')
            ),
            h('div', { style: { display: 'flex', gap: '8px' } },
              h('input', {
                type: 'text',
                readOnly: true,
                value: typeof window !== 'undefined' ? `${window.location.origin}/transfer/accept?token=${transferSuccess.transferToken}` : `/transfer/accept?token=${transferSuccess.transferToken}`,
                style: { flex: 1, padding: '8px 10px', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.82rem', background: '#f8fafc' }
              }),
              h('button', {
                type: 'button',
                className: 'btn-primary',
                style: { whiteSpace: 'nowrap' },
                onClick: () => {
                  const url = `${window.location.origin}/transfer/accept?token=${transferSuccess.transferToken}`;
                  navigator.clipboard.writeText(url);
                  setCopiedLink(true);
                  setTimeout(() => setCopiedLink(false), 2000);
                }
              }, copiedLink ? '✓ Đã copy!' : 'Sao chép link')
            ),
            h('button', {
              type: 'button',
              className: 'quiet',
              style: { alignSelf: 'flex-end', marginTop: '8px' },
              onClick: () => setTransferModalOpen(false)
            }, 'Đóng')
          ) : h('form', { onSubmit: handleTransferSubmit, style: { display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px' } },
            h('div', null,
              h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Email người nhận vé *'),
              h('input', {
                type: 'email',
                required: true,
                placeholder: 'VD: banbe@email.com',
                value: transferEmail,
                onChange: (e: any) => setTransferEmail(e.target.value),
                style: { width: '100%', padding: '8px 12px', border: '1px solid #cbd5e1', borderRadius: '8px' }
              })
            ),
            h('div', null,
              h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Lời nhắn tặng vé (Tùy chọn)'),
              h('textarea', {
                rows: 2,
                placeholder: 'VD: Chúc bạn có một đêm nhạc tuyệt vời!',
                value: transferNote,
                onChange: (e: any) => setTransferNote(e.target.value),
                style: { width: '100%', padding: '8px 12px', border: '1px solid #cbd5e1', borderRadius: '8px', resize: 'none' }
              })
            ),
            h('div', { style: { display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '8px' } },
              h('button', { type: 'button', className: 'quiet', disabled: transferLoading, onClick: () => setTransferModalOpen(false) }, 'Hủy'),
              h('button', { type: 'submit', className: 'btn-primary', disabled: transferLoading }, transferLoading ? 'Đang xử lý…' : 'Xác nhận tạo link tặng vé')
            )
          )
        )
      ),

      resaleModalOpen && h('div', { className: 'auth-modal-backdrop', onClick: (e) => { if (e.target === e.currentTarget) setResaleModalOpen(false); } },
        h('div', { className: 'auth-modal-card', style: { maxWidth: '480px' } },
          h('div', { className: 'auth-modal-header' },
            h('div', null,
              h('h3', { style: { margin: 0, fontSize: '1.15rem', color: '#0f172a' } }, '🏷️ Đăng bán lại vé chính chủ (Resale)'),
              h('p', { className: 'muted', style: { margin: '4px 0 0', fontSize: '0.82rem' } },
                resaleTargetTicket ? `Mã vé: ${resaleTargetTicket.code}${resaleTargetTicket.seatLabel ? ' (Ghế ' + resaleTargetTicket.seatLabel + ')' : ''}` : ''
              )
            ),
            h('button', { type: 'button', className: 'auth-close-btn', onClick: () => setResaleModalOpen(false) }, '×')
          ),
          resaleError && h('div', { className: 'auth-error-alert' }, '⚠️ ', resaleError),
          resaleSuccess ? h('div', { style: { padding: '16px 0', display: 'flex', flexDirection: 'column', gap: '12px' } },
            h('div', { style: { background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '10px', padding: '12px', color: '#065f46' } },
              h('strong', null, '🎉 Đã đăng bán vé thành công!'),
              h('p', { style: { margin: '6px 0 0', fontSize: '0.85rem' } },
                `Vé của bạn đã được niêm yết trên Chợ vé chính chủ với giá ${vnd(resaleSuccess.askingPriceVnd)}. ` +
                'Khi có khán giả mua vé và thanh toán thành công, hệ thống sẽ tự động chuyển tiền và vô hiệu hóa vé cũ của bạn.'
              )
            ),
            h('button', {
              type: 'button',
              className: 'hero-cta-coral',
              style: { alignSelf: 'flex-end', marginTop: '8px' },
              onClick: () => setResaleModalOpen(false)
            }, 'Hoàn tất')
          ) : (() => {
            const origPrice = selectedBooking ? Math.round(selectedBooking.totalAmountVnd / (tickets.length || 1)) : 0;
            const currentPrice = parseInt(resalePriceInput, 10) || 0;
            const exceeds = currentPrice > origPrice;
            return h('form', { onSubmit: handleResaleSubmit, style: { display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px' } },
              h('div', { style: { background: '#f8fafc', padding: '10px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '0.85rem' } },
                h('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: '4px' } },
                  h('span', { className: 'muted' }, 'Giá gốc của vé:'),
                  h('strong', null, vnd(origPrice))
                ),
                h('span', { style: { fontSize: '0.75rem', color: '#059669', display: 'block' } }, '🛡️ Chính sách chống phe vé: Giá bán lại bắt buộc nhỏ hơn hoặc bằng giá vé gốc.')
              ),
              h('div', null,
                h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Giá mong muốn bán lại (VND) *'),
                h('input', {
                  type: 'number',
                  required: true,
                  min: 1000,
                  max: origPrice,
                  placeholder: `Tối đa ${origPrice}`,
                  value: resalePriceInput,
                  onChange: (e: any) => setResalePriceInput(e.target.value),
                  style: { width: '100%', padding: '8px 12px', border: '1px solid ' + (exceeds ? '#ef4444' : '#cbd5e1'), borderRadius: '8px' }
                }),
                exceeds ? h('small', { style: { color: '#dc2626', display: 'block', marginTop: '4px' } },
                  `⚠️ Giá bán lại không được vượt quá giá gốc ${vnd(origPrice)}`
                ) : h('small', { className: 'muted', style: { display: 'block', marginTop: '4px' } },
                  'Bạn có thể giảm giá để sang nhượng vé nhanh hơn.'
                )
              ),
              h('div', { style: { display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '8px' } },
                h('button', { type: 'button', className: 'quiet', disabled: resaleLoading, onClick: () => setResaleModalOpen(false) }, 'Hủy'),
                h('button', { type: 'submit', className: 'btn-primary', disabled: resaleLoading || exceeds || currentPrice <= 0 },
                  resaleLoading ? 'Đang xử lý…' : 'Đăng bán vé ngay'
                )
              )
            );
          })()
        )
      )
    ),

    // Invoice Modal
    invoiceModalOpen && h('div', {
      className: 'modal-backdrop',
      style: {
        position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
        background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
      }
    },
      h('div', {
        className: 'modal-card',
        style: {
          background: '#fff', borderRadius: '16px', padding: '1.5rem',
          maxWidth: '520px', width: '90%', boxShadow: '0 20px 25px -5px rgba(0,0,0,0.2)'
        }
      },
        h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' } },
          h('h3', { style: { margin: 0, fontSize: '1.25rem', fontWeight: 700 } }, currentInvoice ? '🧾 Hóa Đơn Điện Tử VAT' : '📝 Yêu Cầu Xuất Hóa Đơn VAT'),
          h('button', {
            type: 'button',
            onClick: () => setInvoiceModalOpen(false),
            style: { border: 'none', background: 'transparent', fontSize: '1.25rem', cursor: 'pointer' }
          }, '✕')
        ),
        currentInvoice ? h('div', null,
          h('div', { style: { background: '#f8fafc', padding: '1rem', borderRadius: '12px', border: '1px solid #e2e8f0', marginBottom: '1rem' } },
            h('p', { style: { margin: '0 0 0.5rem', fontSize: '0.875rem' } }, h('strong', null, 'Mã tra cứu: '), h('code', { style: { color: '#4338ca', fontWeight: 700 } }, currentInvoice.invoiceLookupCode)),
            h('p', { style: { margin: '0 0 0.5rem', fontSize: '0.875rem' } }, h('strong', null, 'Đơn vị: '), currentInvoice.companyName),
            h('p', { style: { margin: '0 0 0.5rem', fontSize: '0.875rem' } }, h('strong', null, 'Mã số thuế: '), currentInvoice.taxCode),
            h('p', { style: { margin: '0 0 0.5rem', fontSize: '0.875rem' } }, h('strong', null, 'Địa chỉ: '), currentInvoice.companyAddress),
            h('p', { style: { margin: '0 0 0.5rem', fontSize: '0.875rem' } }, h('strong', null, 'Email nhận: '), currentInvoice.recipientEmail),
            h('hr', { style: { border: 'none', borderTop: '1px dashed #cbd5e1', margin: '0.75rem 0' } }),
            h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: '0.875rem', marginBottom: '0.25rem' } },
              h('span', null, 'Tiền vé trước thuế:'),
              h('span', null, vnd(currentInvoice.totalAmountVnd - currentInvoice.vatAmountVnd))
            ),
            h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: '0.875rem', marginBottom: '0.25rem' } },
              h('span', null, 'Thuế suất GTGT (10%):'),
              h('span', null, vnd(currentInvoice.vatAmountVnd))
            ),
            h('div', { style: { display: 'flex', justifyContent: 'space-between', fontSize: '1rem', fontWeight: 700, color: '#4338ca', marginTop: '0.5rem' } },
              h('span', null, 'Tổng cộng thanh toán:'),
              h('span', null, vnd(currentInvoice.totalAmountVnd))
            )
          ),
          h('div', { style: { display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' } },
            h('button', {
              type: 'button',
              className: 'print-pass-btn',
              onClick: () => window.print()
            }, '🖨️ In hóa đơn'),
            h('button', {
              type: 'button',
              className: 'order-more-btn',
              onClick: () => setInvoiceModalOpen(false)
            }, 'Đóng')
          )
        ) : h('form', {
          onSubmit: (e: any) => {
            e.preventDefault();
            void handleIssueInvoice();
          }
        },
          invoiceError && h('div', { className: 'alert error', style: { marginBottom: '1rem' } }, invoiceError),
          h('div', { style: { marginBottom: '0.75rem' } },
            h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' } }, 'Mã số thuế (10 hoặc 13 chữ số) *'),
            h('input', {
              type: 'text',
              required: true,
              placeholder: 'VD: 0101234567',
              value: invoiceForm.taxCode,
              onChange: (e: any) => setInvoiceForm(f => ({ ...f, taxCode: e.target.value })),
              style: { width: '100%', padding: '0.5rem', border: '1px solid #cbd5e1', borderRadius: '8px' }
            })
          ),
          h('div', { style: { marginBottom: '0.75rem' } },
            h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' } }, 'Tên công ty / tổ chức *'),
            h('input', {
              type: 'text',
              required: true,
              placeholder: 'VD: Công ty TNHH Giải Trí Sao Kim',
              value: invoiceForm.companyName,
              onChange: (e: any) => setInvoiceForm(f => ({ ...f, companyName: e.target.value })),
              style: { width: '100%', padding: '0.5rem', border: '1px solid #cbd5e1', borderRadius: '8px' }
            })
          ),
          h('div', { style: { marginBottom: '0.75rem' } },
            h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' } }, 'Địa chỉ doanh nghiệp *'),
            h('input', {
              type: 'text',
              required: true,
              placeholder: 'VD: Số 1 Tràng Tiền, Hoàn Kiếm, Hà Nội',
              value: invoiceForm.companyAddress,
              onChange: (e: any) => setInvoiceForm(f => ({ ...f, companyAddress: e.target.value })),
              style: { width: '100%', padding: '0.5rem', border: '1px solid #cbd5e1', borderRadius: '8px' }
            })
          ),
          h('div', { style: { marginBottom: '1rem' } },
            h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.25rem' } }, 'Email nhận hóa đơn *'),
            h('input', {
              type: 'email',
              required: true,
              placeholder: 'VD: ketoan@saokim.vn',
              value: invoiceForm.recipientEmail,
              onChange: (e: any) => setInvoiceForm(f => ({ ...f, recipientEmail: e.target.value })),
              style: { width: '100%', padding: '0.5rem', border: '1px solid #cbd5e1', borderRadius: '8px' }
            })
          ),
          h('div', { style: { display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' } },
            h('button', {
              type: 'button',
              className: 'quiet',
              disabled: invoiceLoading,
              onClick: () => setInvoiceModalOpen(false)
            }, 'Hủy'),
            h('button', {
              type: 'submit',
              className: 'btn-primary',
              disabled: invoiceLoading
            }, invoiceLoading ? 'Đang phát hành…' : 'Xác nhận phát hành hóa đơn')
          )
        )
      ),

      transferModalOpen && h('div', { className: 'auth-modal-backdrop', onClick: (e) => { if (e.target === e.currentTarget) setTransferModalOpen(false); } },
        h('div', { className: 'auth-modal-card', style: { maxWidth: '480px' } },
          h('div', { className: 'auth-modal-header' },
            h('div', null,
              h('h3', { style: { margin: 0, fontSize: '1.15rem', color: '#0f172a' } }, '🎁 Gửi tặng vé cho bạn bè'),
              h('p', { className: 'muted', style: { margin: '4px 0 0', fontSize: '0.82rem' } },
                transferTargetTicket ? `Mã vé: ${transferTargetTicket.code}${transferTargetTicket.seatLabel ? ' (Ghế ' + transferTargetTicket.seatLabel + ')' : ''}` : ''
              )
            ),
            h('button', { type: 'button', className: 'auth-close-btn', onClick: () => setTransferModalOpen(false) }, '×')
          ),
          transferError && h('div', { className: 'auth-error-alert' }, '⚠️ ', transferError),
          transferSuccess ? h('div', { style: { padding: '16px 0', display: 'flex', flexDirection: 'column', gap: '12px' } },
            h('div', { style: { background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '10px', padding: '12px', color: '#065f46' } },
              h('strong', null, '🎉 Đã tạo liên kết gửi tặng thành công!'),
              h('p', { style: { margin: '6px 0 0', fontSize: '0.85rem' } }, 'Hãy gửi link dưới đây cho bạn bè để họ nhận vé vào ví. Sau khi người nhận chấp nhận, mã QR cũ của bạn sẽ tự động vô hiệu hóa.')
            ),
            h('div', { style: { display: 'flex', gap: '8px' } },
              h('input', {
                type: 'text',
                readOnly: true,
                value: typeof window !== 'undefined' ? `${window.location.origin}/transfer/accept?token=${transferSuccess.transferToken}` : `/transfer/accept?token=${transferSuccess.transferToken}`,
                style: { flex: 1, padding: '8px 10px', border: '1px solid #cbd5e1', borderRadius: '8px', fontSize: '0.82rem', background: '#f8fafc' }
              }),
              h('button', {
                type: 'button',
                className: 'btn-primary',
                style: { whiteSpace: 'nowrap' },
                onClick: () => {
                  const url = `${window.location.origin}/transfer/accept?token=${transferSuccess.transferToken}`;
                  navigator.clipboard.writeText(url);
                  setCopiedLink(true);
                  setTimeout(() => setCopiedLink(false), 2000);
                }
              }, copiedLink ? '✓ Đã copy!' : 'Sao chép link')
            ),
            h('button', {
              type: 'button',
              className: 'quiet',
              style: { alignSelf: 'flex-end', marginTop: '8px' },
              onClick: () => setTransferModalOpen(false)
            }, 'Đóng')
          ) : h('form', { onSubmit: handleTransferSubmit, style: { display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px' } },
            h('div', null,
              h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Email người nhận vé *'),
              h('input', {
                type: 'email',
                required: true,
                placeholder: 'VD: banbe@email.com',
                value: transferEmail,
                onChange: (e: any) => setTransferEmail(e.target.value),
                style: { width: '100%', padding: '8px 12px', border: '1px solid #cbd5e1', borderRadius: '8px' }
              })
            ),
            h('div', null,
              h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Lời nhắn tặng vé (Tùy chọn)'),
              h('textarea', {
                rows: 2,
                placeholder: 'VD: Chúc bạn có một đêm nhạc tuyệt vời!',
                value: transferNote,
                onChange: (e: any) => setTransferNote(e.target.value),
                style: { width: '100%', padding: '8px 12px', border: '1px solid #cbd5e1', borderRadius: '8px', resize: 'none' }
              })
            ),
            h('div', { style: { display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '8px' } },
              h('button', { type: 'button', className: 'quiet', disabled: transferLoading, onClick: () => setTransferModalOpen(false) }, 'Hủy'),
              h('button', { type: 'submit', className: 'btn-primary', disabled: transferLoading }, transferLoading ? 'Đang xử lý…' : 'Xác nhận tạo link tặng vé')
            )
          )
        )
      ),

      resaleModalOpen && h('div', { className: 'auth-modal-backdrop', onClick: (e) => { if (e.target === e.currentTarget) setResaleModalOpen(false); } },
        h('div', { className: 'auth-modal-card', style: { maxWidth: '480px' } },
          h('div', { className: 'auth-modal-header' },
            h('div', null,
              h('h3', { style: { margin: 0, fontSize: '1.15rem', color: '#0f172a' } }, '🏷️ Đăng bán lại vé chính chủ (Resale)'),
              h('p', { className: 'muted', style: { margin: '4px 0 0', fontSize: '0.82rem' } },
                resaleTargetTicket ? `Mã vé: ${resaleTargetTicket.code}${resaleTargetTicket.seatLabel ? ' (Ghế ' + resaleTargetTicket.seatLabel + ')' : ''}` : ''
              )
            ),
            h('button', { type: 'button', className: 'auth-close-btn', onClick: () => setResaleModalOpen(false) }, '×')
          ),
          resaleError && h('div', { className: 'auth-error-alert' }, '⚠️ ', resaleError),
          resaleSuccess ? h('div', { style: { padding: '16px 0', display: 'flex', flexDirection: 'column', gap: '12px' } },
            h('div', { style: { background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '10px', padding: '12px', color: '#065f46' } },
              h('strong', null, '🎉 Đã đăng bán vé thành công!'),
              h('p', { style: { margin: '6px 0 0', fontSize: '0.85rem' } },
                `Vé của bạn đã được niêm yết trên Chợ vé chính chủ với giá ${vnd(resaleSuccess.askingPriceVnd)}. ` +
                'Khi có khán giả mua vé và thanh toán thành công, hệ thống sẽ tự động chuyển tiền và vô hiệu hóa vé cũ của bạn.'
              )
            ),
            h('button', {
              type: 'button',
              className: 'hero-cta-coral',
              style: { alignSelf: 'flex-end', marginTop: '8px' },
              onClick: () => setResaleModalOpen(false)
            }, 'Hoàn tất')
          ) : (() => {
            const origPrice = selectedBooking ? Math.round(selectedBooking.totalAmountVnd / (tickets.length || 1)) : 0;
            const currentPrice = parseInt(resalePriceInput, 10) || 0;
            const exceeds = currentPrice > origPrice;
            return h('form', { onSubmit: handleResaleSubmit, style: { display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px' } },
              h('div', { style: { background: '#f8fafc', padding: '10px 12px', borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '0.85rem' } },
                h('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: '4px' } },
                  h('span', { className: 'muted' }, 'Giá gốc của vé:'),
                  h('strong', null, vnd(origPrice))
                ),
                h('span', { style: { fontSize: '0.75rem', color: '#059669', display: 'block' } }, '🛡️ Chính sách chống phe vé: Giá bán lại bắt buộc nhỏ hơn hoặc bằng giá vé gốc.')
              ),
              h('div', null,
                h('label', { style: { display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '4px' } }, 'Giá mong muốn bán lại (VND) *'),
                h('input', {
                  type: 'number',
                  required: true,
                  min: 1000,
                  max: origPrice,
                  placeholder: `Tối đa ${origPrice}`,
                  value: resalePriceInput,
                  onChange: (e: any) => setResalePriceInput(e.target.value),
                  style: { width: '100%', padding: '8px 12px', border: '1px solid ' + (exceeds ? '#ef4444' : '#cbd5e1'), borderRadius: '8px' }
                }),
                exceeds ? h('small', { style: { color: '#dc2626', display: 'block', marginTop: '4px' } },
                  `⚠️ Giá bán lại không được vượt quá giá gốc ${vnd(origPrice)}`
                ) : h('small', { className: 'muted', style: { display: 'block', marginTop: '4px' } },
                  'Bạn có thể giảm giá để sang nhượng vé nhanh hơn.'
                )
              ),
              h('div', { style: { display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '8px' } },
                h('button', { type: 'button', className: 'quiet', disabled: resaleLoading, onClick: () => setResaleModalOpen(false) }, 'Hủy'),
                h('button', { type: 'submit', className: 'btn-primary', disabled: resaleLoading || exceeds || currentPrice <= 0 },
                  resaleLoading ? 'Đang xử lý…' : 'Đăng bán vé ngay'
                )
              )
            );
          })()
        )
      )
    )
  );
}
