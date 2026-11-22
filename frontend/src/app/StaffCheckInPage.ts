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

  // V4 Offline-first check-in state
  const [isOfflineMode, setIsOfflineMode] = useState(false);
  const [offlineManifest, setOfflineManifest] = useState<import('../api/client.ts').OfflineManifest | null>(null);
  const [offlinePendingQueue, setOfflinePendingQueue] = useState<import('../api/client.ts').OfflineSyncItem[]>([]);
  const [offlineLoading, setOfflineLoading] = useState(false);
  const [syncResult, setSyncResult] = useState<import('../api/client.ts').BatchSyncResponse | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);

  const manualInputRef = useRef<HTMLInputElement>(null);

  const hasStaffOrAdminRole = !!user && (
    user.role === 'STAFF' ||
    user.role === 'ADMIN' ||
    (user.roles && (user.roles.includes('STAFF') || user.roles.includes('ADMIN')))
  );

  useEffect(() => {
    const handleOnline = () => { if (isOfflineMode) setIsOfflineMode(false); };
    const handleOffline = () => { setIsOfflineMode(true); };
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [isOfflineMode]);

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
      try {
        const cachedManifest = localStorage.getItem('eventflow.offline_manifest.' + selectedEventId);
        if (cachedManifest) setOfflineManifest(JSON.parse(cachedManifest));
        else setOfflineManifest(null);

        const cachedQueue = localStorage.getItem('eventflow.offline_pending.' + selectedEventId);
        if (cachedQueue) setOfflinePendingQueue(JSON.parse(cachedQueue));
        else setOfflinePendingQueue([]);
      } catch {
        // ignore localStorage access issues in sandbox
      }
    }
  }, [selectedEventId]);

  async function handleDownloadOfflineManifest() {
    if (!selectedEventId || !api.staff?.getOfflineManifest) return;
    setOfflineLoading(true);
    setError(null);
    try {
      const manifest = await api.staff.getOfflineManifest(selectedEventId);
      setOfflineManifest(manifest);
      try { localStorage.setItem('eventflow.offline_manifest.' + selectedEventId, JSON.stringify(manifest)); } catch {}
      playBeep('success');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu soát vé offline');
      playBeep('error');
    } finally {
      setOfflineLoading(false);
    }
  }

  async function handleSyncOfflineQueue() {
    if (!selectedEventId || !api.staff?.batchSync || offlinePendingQueue.length === 0 || isSyncing) return;
    setIsSyncing(true);
    setError(null);
    try {
      const res = await api.staff.batchSync({ eventId: selectedEventId, items: offlinePendingQueue });
      setSyncResult(res);
      setOfflinePendingQueue([]);
      try { localStorage.removeItem('eventflow.offline_pending.' + selectedEventId); } catch {}
      playBeep('success');
      void loadEventData(selectedEventId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi đồng bộ dữ liệu soát vé lên máy chủ');
      playBeep('error');
    } finally {
      setIsSyncing(false);
    }
  }

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

    // V4 Offline-first local validation
    if (isOfflineMode || (typeof navigator !== 'undefined' && !navigator.onLine)) {
      setIsScanning(false);
      if (!offlineManifest || offlineManifest.tickets.length === 0) {
        setLastError({
          code: 'OFFLINE_NO_DATA',
          message: 'Chưa có danh sách vé offline cho sự kiện này. Vui lòng bấm \"Tải Dữ Liệu Offline\" trước.'
        });
        playBeep('error');
        return;
      }

      let targetTicket: import('../api/client.ts').OfflineTicketItem | undefined;
      if (mode === 'qr') {
        const raw = qrInput.trim();
        targetTicket = offlineManifest.tickets.find(t => t.qrToken === raw || raw.startsWith(t.ticketId));
      } else {
        const code = ticketCodeInput.trim().toUpperCase();
        targetTicket = offlineManifest.tickets.find(t => t.ticketCode.toUpperCase() === code);
      }

      if (!targetTicket) {
        setLastError({ code: 'NOT_FOUND', message: 'Vé không tồn tại trong danh mục vé sự kiện này!' });
        playBeep('error');
        return;
      }

      if (targetTicket.checkedIn) {
        setLastError({
          code: 'TICKET_ALREADY_USED',
          message: `CẢNH BÁO: Vé này đã được quét ${targetTicket.checkedInAt ? 'lúc ' + formatDateTime(targetTicket.checkedInAt) : 'trước đó'}!`
        });
        playBeep('warning');
        return;
      }

      // Mark locally checked-in
      const nowIso = new Date().toISOString();
      targetTicket.checkedIn = true;
      targetTicket.checkedInAt = nowIso;
      try {
        localStorage.setItem('eventflow.offline_manifest.' + selectedEventId, JSON.stringify(offlineManifest));
      } catch {}

      // Add to pending sync queue
      const queueItem: import('../api/client.ts').OfflineSyncItem = {
        clientSyncId: 'OFF-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
        ticketId: targetTicket.ticketId,
        ticketCode: targetTicket.ticketCode,
        qrToken: targetTicket.qrToken,
        gateName,
        clientTimestamp: nowIso,
        deviceInfo: 'Offline Web Terminal'
      };
      const nextQueue = [...offlinePendingQueue, queueItem];
      setOfflinePendingQueue(nextQueue);
      try {
        localStorage.setItem('eventflow.offline_pending.' + selectedEventId, JSON.stringify(nextQueue));
      } catch {}

      setLastResult({
        status: 'CHECKED_IN_OFFLINE',
        ticketId: targetTicket.ticketId,
        ticketCode: targetTicket.ticketCode,
        attendeeName: targetTicket.attendeeName,
        ticketType: targetTicket.ticketType,
        seatLabel: targetTicket.seatLabel,
        checkedInAt: nowIso,
        gate: gateName,
        message: 'HỢP LỆ — SOÁT VÉ NGOẠI TUYẾN THÀNH CÔNG (ĐÃ LƯU HÀNG ĐỢI)'
      });
      setLastError(null);
      playBeep('success');

      if (mode === 'qr') setQrInput('');
      if (mode === 'manual') {
        setTicketCodeInput('');
        manualInputRef.current?.focus();
      }
      return;
    }

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

    // V4 Offline-First Control Bar
    h('div', { className: 'staff-offline-bar', style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem', background: isOfflineMode ? '#fffbeb' : '#f0fdf4', padding: '0.75rem 1rem', borderRadius: '0.5rem', border: `1px solid ${isOfflineMode ? '#fde68a' : '#bbf7d0'}`, marginBottom: '1.25rem' } },
      h('div', { style: { display: 'flex', alignItems: 'center', gap: '0.75rem' } },
        h('span', {
          className: 'badge-network-status',
          style: {
            padding: '0.3rem 0.6rem',
            borderRadius: '9999px',
            fontSize: '0.8rem',
            fontWeight: 'bold',
            background: isOfflineMode ? '#fef3c7' : '#dcfce7',
            color: isOfflineMode ? '#92400e' : '#166534'
          }
        }, isOfflineMode ? '🟠 CHẾ ĐỘ NGOẠI TUYẾN (OFFLINE)' : '🟢 TRỰC TUYẾN (ONLINE)'),
        h('button', {
          type: 'button',
          className: 'btn-mode-toggle',
          style: { fontSize: '0.8rem', padding: '0.3rem 0.6rem', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer' },
          onClick: () => setIsOfflineMode(x => !x)
        }, isOfflineMode ? 'Chuyển sang Online' : 'Chuyển sang Offline')
      ),
      h('div', { style: { display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' } },
        h('button', {
          type: 'button',
          className: 'btn-download-manifest',
          style: { fontSize: '0.8rem', padding: '0.35rem 0.75rem', borderRadius: '4px', background: '#0284c7', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 'bold' },
          disabled: offlineLoading || !selectedEventId,
          onClick: () => void handleDownloadOfflineManifest()
        }, offlineLoading ? 'Đang tải...' : offlineManifest ? `📥 Cập nhật vé Offline (${offlineManifest.totalTickets} vé)` : '📥 Tải danh sách vé Offline'),
        offlinePendingQueue.length > 0 && h('button', {
          type: 'button',
          className: 'btn-sync-queue',
          style: { fontSize: '0.8rem', padding: '0.35rem 0.75rem', borderRadius: '4px', background: '#059669', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 'bold' },
          disabled: isSyncing,
          onClick: () => void handleSyncOfflineQueue()
        }, isSyncing ? 'Đang đồng bộ...' : `🔄 Đồng bộ lên Server (${offlinePendingQueue.length} vé)`)
      )
    ),

    // Sync result alert
    syncResult && h('div', { className: 'alert alert-info', style: { marginBottom: '1rem', background: '#eff6ff', border: '1px solid #bfdbfe', color: '#1e40af', padding: '0.75rem', borderRadius: '0.5rem' } },
      `Đồng bộ hoàn tất: ${syncResult.syncedCount}/${syncResult.total} vé thành công (${syncResult.conflictCount} vé xung đột quét trùng).`
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
