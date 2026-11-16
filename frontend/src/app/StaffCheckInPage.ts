import { createElement as h, useState, useEffect, useRef } from 'react';
import type { Api, User, AssignedEvent, CheckInResult, CheckInStats, CheckInHistoryItem } from '../api/client.ts';

function playBeep(type: 'success' | 'warning' | 'error') {
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    if (type === 'success') {
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.25);
    } else {
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(220, ctx.currentTime);
      gain.gain.setValueAtTime(0.4, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.4);
    }
  } catch {
    // Audio context may be restricted or unsupported
  }
}

const formatDateTime = (isoString?: string | null) => {
  if (!isoString) return '--';
  return new Date(isoString).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
};

export function StaffCheckInPage({
  api,
  user,
  onNavigate,
  onOpenAuth
}: {
  api: Api;
  user: User | null;
  onNavigate: (path: string) => void;
  onOpenAuth: () => void;
}) {
  const [events, setEvents] = useState<AssignedEvent[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Check-in input
  const [activeTab, setActiveTab] = useState<'qr' | 'manual'>('qr');
  const [qrInput, setQrInput] = useState('');
  const [ticketCodeInput, setTicketCodeInput] = useState('');
  const [gateName, setGateName] = useState('MAIN_GATE');
  const [isScanning, setIsScanning] = useState(false);

  // Result & stats
  const [lastResult, setLastResult] = useState<CheckInResult | null>(null);
  const [lastError, setLastError] = useState<{ code: string; message: string } | null>(null);
  const [stats, setStats] = useState<CheckInStats | null>(null);
  const [history, setHistory] = useState<CheckInHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const manualInputRef = useRef<HTMLInputElement>(null);

  const hasStaffOrAdminRole = !!user && (
    user.role === 'STAFF' ||
    user.role === 'ADMIN' ||
    (user.roles && (user.roles.includes('STAFF') || user.roles.includes('ADMIN')))
  );

  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }
    if (!hasStaffOrAdminRole) {
      setLoading(false);
      return;
    }
    void loadAssignedEvents();
  }, [user]);

  useEffect(() => {
    if (selectedEventId) {
      void loadEventData(selectedEventId);
    }
  }, [selectedEventId]);

  async function loadAssignedEvents() {
    setLoading(true);
    setError(null);
    try {
      if (!api.staff) {
        throw new Error('API soát vé chưa sẵn sàng');
      }
      const data = await api.staff.events();
      setEvents(data);
      if (data.length > 0) {
        setSelectedEventId(data[0].eventId);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách sự kiện phân công');
    } finally {
      setLoading(false);
    }
  }

  async function loadEventData(eventId: string) {
    if (!api.staff) return;
    try {
      const [statsData, historyData] = await Promise.all([
        api.staff.stats(eventId),
        api.staff.history(eventId, 50)
      ]);
      setStats(statsData);
      setHistory(historyData);
    } catch (err: unknown) {
      console.warn('Could not load stats or history:', err);
    }
  }

  async function handleCheckIn(mode: 'qr' | 'manual') {
    if (!selectedEventId || !api.staff) return;

    const payload = mode === 'qr'
      ? { qrToken: qrInput.trim(), gateName, deviceInfo: 'Web Scanner (Desktop/Mobile)' }
      : { ticketCode: ticketCodeInput.trim(), gateName, deviceInfo: 'Manual Input Terminal' };

    if (mode === 'qr' && !qrInput.trim()) {
      setLastError({ code: 'VALIDATION_ERROR', message: 'Vui lòng nhập hoặc quét mã QR!' });
      return;
    }
    if (mode === 'manual' && !ticketCodeInput.trim()) {
      setLastError({ code: 'VALIDATION_ERROR', message: 'Vui lòng nhập mã vé!' });
      return;
    }

    setIsScanning(true);
    setLastResult(null);
    setLastError(null);

    try {
      const res = await api.staff.checkIn(selectedEventId, payload);
      setLastResult(res);
      setLastError(null);
      playBeep('success');

      // Clear inputs
      if (mode === 'qr') setQrInput('');
      if (mode === 'manual') {
        setTicketCodeInput('');
        manualInputRef.current?.focus();
      }

      // Refresh stats & history
      void loadEventData(selectedEventId);
    } catch (err: unknown) {
      const errorObj = err as { code?: string; message?: string; status?: number };
      const code = errorObj.code || 'CHECKIN_ERROR';
      const msg = errorObj.message || 'Lỗi khi soát vé';

      setLastResult(null);
      setLastError({ code, message: msg });

      if (code === 'TICKET_ALREADY_USED') {
        playBeep('warning');
      } else {
        playBeep('error');
      }
    } finally {
      setIsScanning(false);
    }
  }

  const selectedEvent = events.find(e => e.eventId === selectedEventId);

  // Unauthenticated screen
  if (!user) {
    return h('div', { className: 'staff-portal-container' },
      h('div', { className: 'staff-auth-gate' },
        h('div', { className: 'staff-auth-gate-icon' }, '📱'),
        h('h2', null, 'CỔNG VẬN HÀNH SOÁT VÉ (STAFF PORTAL)'),
        h('p', null, 'Bạn cần đăng nhập tài khoản Nhân viên (STAFF) hoặc Quản trị viên (ADMIN) để thực hiện soát vé tại cổng.'),
        h('button', {
          className: 'btn btn-primary btn-lg',
          onClick: onOpenAuth
        }, 'Đăng Nhập Ngay')
      )
    );
  }

  // Unauthorized role screen
  if (!hasStaffOrAdminRole) {
    return h('div', { className: 'staff-portal-container' },
      h('div', { className: 'staff-auth-gate' },
        h('div', { className: 'staff-auth-gate-icon' }, '⛔'),
        h('h2', null, 'KHÔNG CÓ QUYỀN TRUY CẬP'),
        h('p', null, `Tài khoản (${user.email}) hiện chỉ có vai trò ${user.role}. Phân hệ này dành riêng cho Nhân viên soát vé (STAFF) và Quản trị viên (ADMIN).`),
        h('button', {
          className: 'btn btn-secondary',
          onClick: () => onNavigate('/events')
        }, 'Quay Lại Trang Chủ')
      )
    );
  }

  return h('div', { className: 'staff-portal-container' },
    // Portal Header
    h('header', { className: 'staff-portal-header' },
      h('div', { className: 'staff-portal-brand' },
        h('span', { className: 'brand-badge' }, 'STAFF TERMINAL'),
        h('h1', null, 'Hệ Thống Soát Vé Cổng Điện Tử')
      ),
      h('div', { className: 'staff-header-actions' },
        h('div', { className: 'gate-selector-wrap' },
          h('label', null, 'Cổng:'),
          h('select', {
            className: 'form-control gate-select',
            value: gateName,
            onChange: (e: React.ChangeEvent<HTMLSelectElement>) => setGateName(e.target.value)
          },
            h('option', { value: 'MAIN_GATE' }, 'CỔNG CHÍNH (MAIN_GATE)'),
            h('option', { value: 'GATE_A_VIP' }, 'CỔNG A — VIP Khán Phòng'),
            h('option', { value: 'GATE_B_GA' }, 'CỔNG B — Vé Thường GA'),
            h('option', { value: 'GATE_C' }, 'CỔNG C — Cửa Tây'),
            h('option', { value: 'GATE_D' }, 'CỔNG D — Cửa Đông')
          )
        ),
        h('div', { className: 'user-badge-tag' },
          h('span', { className: 'dot-online' }),
          h('span', null, user.displayName || user.email)
        )
      )
    ),

    // Event Selector & Status bar
    h('div', { className: 'staff-event-bar' },
      loading ? h('div', { className: 'loading-text' }, 'Đang tải danh sách sự kiện...') :
      events.length === 0 ? h('div', { className: 'alert alert-warning' },
        '⚠️ Bạn chưa được phân công phụ trách soát vé sự kiện nào. Vui lòng liên hệ Nhà tổ chức để được gán quyền.'
      ) :
      h('div', { className: 'event-selector-row' },
        h('div', { className: 'event-picker' },
          h('label', null, 'Sự Kiện Đang Phụ Trách:'),
          h('select', {
            className: 'form-control event-select',
            value: selectedEventId,
            onChange: (e: React.ChangeEvent<HTMLSelectElement>) => setSelectedEventId(e.target.value)
          },
            events.map(ev => h('option', { key: ev.eventId, value: ev.eventId }, `${ev.title} (${ev.venue})`))
          )
        ),
        selectedEvent && h('div', { className: 'event-meta-info' },
          h('span', { className: 'meta-venue' }, `📍 ${selectedEvent.venue}`),
          h('span', { className: 'meta-time' }, `🕒 Khởi hành: ${formatDateTime(selectedEvent.startsAt)}`)
        )
      )
    ),

    // Realtime Check-in Stats
    stats && h('div', { className: 'staff-stats-grid' },
      h('div', { className: 'stat-metric-card' },
        h('div', { className: 'metric-title' }, 'TỔNG VÉ PHÁT HÀNH'),
        h('div', { className: 'metric-value' }, stats.totalSold.toLocaleString('vi-VN')),
        h('div', { className: 'metric-desc' }, 'Vé đã thanh toán CONFIRMED')
      ),
      h('div', { className: 'stat-metric-card stat-metric-success' },
        h('div', { className: 'metric-title' }, 'ĐÃ SOÁT VÉ (CHECKED-IN)'),
        h('div', { className: 'metric-value text-success' }, stats.checkedInCount.toLocaleString('vi-VN')),
        h('div', { className: 'metric-desc' }, `Tỷ lệ lấp đầy: ${stats.checkInRatePercent}%`)
      ),
      h('div', { className: 'stat-metric-card' },
        h('div', { className: 'metric-title' }, 'CHƯA CHECK-IN'),
        h('div', { className: 'metric-value text-warning' }, stats.remainingCount.toLocaleString('vi-VN')),
        h('div', { className: 'metric-desc' }, 'Khách chưa qua cổng')
      ),
      h('div', { className: 'stat-metric-card' },
        h('div', { className: 'metric-title' }, 'TIẾN ĐỘ CHECK-IN'),
        h('div', { className: 'metric-progress-wrap' },
          h('div', {
            className: 'metric-progress-bar',
            style: { width: `${Math.min(100, Math.max(0, stats.checkInRatePercent))}%` }
          })
        ),
        h('div', { className: 'metric-desc' }, `${stats.checkInRatePercent}% hoàn thành`)
      )
    ),

    // Main Operation Panel: Scanner & Instant Feedback Card
    h('div', { className: 'staff-workbench-grid' },
      // Left Column: Scanner & Input
      h('div', { className: 'staff-scan-box' },
        // Tabs
        h('div', { className: 'scan-tabs' },
          h('button', {
            className: `scan-tab-btn ${activeTab === 'qr' ? 'active' : ''}`,
            onClick: () => setActiveTab('qr')
          }, '📷 Quét Mã QR'),
          h('button', {
            className: `scan-tab-btn ${activeTab === 'manual' ? 'active' : ''}`,
            onClick: () => setActiveTab('manual')
          }, '⌨️ Nhập Mã Thủ Công')
        ),

        // QR Mode
        activeTab === 'qr' && h('div', { className: 'tab-content qr-content' },
          h('div', { className: 'laser-scanner-frame' },
            h('div', { className: 'laser-beam' }),
            h('div', { className: 'scanner-corner tl' }),
            h('div', { className: 'scanner-corner tr' }),
            h('div', { className: 'scanner-corner bl' }),
            h('div', { className: 'scanner-corner br' }),
            h('div', { className: 'scanner-hint' }, 'Căn khung quét vào mã QR trên vé')
          ),
          h('div', { className: 'form-group mt-3' },
            h('label', null, 'Dán Mã Token QR hoặc Quét từ Camera:'),
            h('input', {
              type: 'text',
              className: 'form-control scan-input',
              placeholder: 'Dán mã token QR (ticketId.code.eventId.issuedAt.sig)...',
              value: qrInput,
              onChange: (e: React.ChangeEvent<HTMLInputElement>) => setQrInput(e.target.value),
              onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => {
                if (e.key === 'Enter') void handleCheckIn('qr');
              }
            })
          ),
          h('div', { className: 'scan-btn-group' },
            h('button', {
              className: 'btn btn-primary btn-block btn-lg',
              disabled: isScanning || !qrInput.trim(),
              onClick: () => handleCheckIn('qr')
            }, isScanning ? 'Đang xác thực...' : '⚡ XÁC THỰC MÃ QR')
          )
        ),

        // Manual Mode
        activeTab === 'manual' && h('div', { className: 'tab-content manual-content' },
          h('div', { className: 'form-group' },
            h('label', null, 'Nhập Mã Vé Điện Tử:'),
            h('input', {
              ref: manualInputRef,
              type: 'text',
              className: 'form-control manual-code-input',
              placeholder: 'VD: EF-2026-CUSTOMER-01 hoặc TKT-A12...',
              value: ticketCodeInput,
              onChange: (e: React.ChangeEvent<HTMLInputElement>) => setTicketCodeInput(e.target.value),
              onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => {
                if (e.key === 'Enter') void handleCheckIn('manual');
              }
            })
          ),
          h('button', {
            className: 'btn btn-primary btn-block btn-lg',
            disabled: isScanning || !ticketCodeInput.trim(),
            onClick: () => handleCheckIn('manual')
          }, isScanning ? 'Đang tra cứu...' : '⚡ XÁC NHẬN SOÁT VÉ')
        )
      ),

      // Right Column: High-Visibility Instant Feedback Card
      h('div', { className: 'staff-feedback-box' },
        // SUCCESS STATE (GREEN)
        lastResult && h('div', { className: 'checkin-card checkin-card-success animate-fade-in' },
          h('div', { className: 'card-status-header' },
            h('span', { className: 'status-icon' }, '✅'),
            h('h2', null, lastResult.message || 'HỢP LỆ — MỜI VÀO')
          ),
          h('div', { className: 'card-attendee-name' }, lastResult.attendeeName),
          h('div', { className: 'card-details-grid' },
            h('div', { className: 'detail-item' },
              h('span', { className: 'label' }, 'Hạng Vé:'),
              h('span', { className: 'val' }, lastResult.ticketType)
            ),
            h('div', { className: 'detail-item' },
              h('span', { className: 'label' }, 'Vị Trí Ghế:'),
              h('span', { className: 'val seat-highlight' }, lastResult.seatLabel)
            ),
            h('div', { className: 'detail-item' },
              h('span', { className: 'label' }, 'Mã Vé:'),
              h('span', { className: 'val code-val' }, lastResult.ticketCode)
            ),
            h('div', { className: 'detail-item' },
              h('span', { className: 'label' }, 'Cổng Vào:'),
              h('span', { className: 'val' }, lastResult.gate)
            ),
            h('div', { className: 'detail-item' },
              h('span', { className: 'label' }, 'Thời Gian:'),
              h('span', { className: 'val' }, formatDateTime(lastResult.checkedInAt))
            )
          )
        ),

        // DUPLICATE / CONFLICT STATE (RED FLASHING)
        lastError && lastError.code === 'TICKET_ALREADY_USED' && h('div', { className: 'checkin-card checkin-card-duplicate animate-shake' },
          h('div', { className: 'card-status-header' },
            h('span', { className: 'status-icon' }, '⛔'),
            h('h2', null, 'CẢNH BÁO: QUÉT TRÙNG VÉ!')
          ),
          h('div', { className: 'duplicate-alert-body' },
            h('p', { className: 'duplicate-message' }, lastError.message),
            h('div', { className: 'security-note' }, 'Vé này đã được ghi nhận check-in trước đó. Tuyệt đối KHÔNG CHO VÀO!')
          )
        ),

        // INVALID STATE (AMBER)
        lastError && lastError.code !== 'TICKET_ALREADY_USED' && h('div', { className: 'checkin-card checkin-card-invalid animate-fade-in' },
          h('div', { className: 'card-status-header' },
            h('span', { className: 'status-icon' }, '⚠️'),
            h('h2', null, 'VÉ KHÔNG HỢP LỆ!')
          ),
          h('div', { className: 'invalid-alert-body' },
            h('div', { className: 'error-code-badge' }, lastError.code),
            h('p', { className: 'invalid-message' }, lastError.message)
          )
        ),

        // IDLE STATE
        !lastResult && !lastError && h('div', { className: 'checkin-card checkin-card-idle' },
          h('div', { className: 'idle-icon' }, '🎯'),
          h('h3', null, 'Sẵn Sàng Tiếp Nhận Vé'),
          h('p', null, 'Quét mã QR hoặc nhập mã vé thủ công ở khung bên trái để bắt đầu soát vé.')
        )
      )
    ),

    // Check-in History Table
    h('div', { className: 'staff-history-section mt-4' },
      h('div', { className: 'section-header' },
        h('h3', null, `📋 Nhật Ký Soát Vé Gần Đây (${history.length} lượt)`),
        h('button', {
          className: 'btn btn-secondary btn-sm',
          onClick: () => selectedEventId && void loadEventData(selectedEventId)
        }, '🔄 Tải Lại')
      ),
      h('div', { className: 'table-responsive' },
        h('table', { className: 'table staff-history-table' },
          h('thead', null,
            h('tr', null,
              h('th', null, 'Thời Gian'),
              h('th', null, 'Mã Vé'),
              h('th', null, 'Khách Hàng'),
              h('th', null, 'Hạng Vé'),
              h('th', null, 'Ghế / Khu Vực'),
              h('th', null, 'Cổng'),
              h('th', null, 'Nhân Viên')
            )
          ),
          h('tbody', null,
            history.length === 0 ? h('tr', null,
              h('td', { colSpan: 7, className: 'text-center text-muted py-4' }, 'Chưa có lượt soát vé nào cho sự kiện này.')
            ) :
            history.map(item => h('tr', { key: item.checkInId },
              h('td', null, formatDateTime(item.checkedInAt)),
              h('td', { className: 'font-mono' }, item.ticketCode),
              h('td', null, item.attendeeName),
              h('td', null, item.ticketType),
              h('td', null, h('span', { className: 'badge badge-seat' }, item.seatLabel)),
              h('td', null, h('span', { className: 'badge badge-gate' }, item.gateName)),
              h('td', null, item.staffName)
            ))
          )
        )
      )
    )
  );
}
