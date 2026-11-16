import { createElement as h, useState, useEffect } from 'react';
import type { Api, User, OrganizerProfile, Venue, EventCategory, OrganizerEvent, StaffAssignment } from '../api/client.ts';

const vnd = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
const formatDateTime = (isoString?: string) => {
  if (!isoString) return '--';
  return new Date(isoString).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
};

export function OrganizerDashboard({
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
  const [profile, setProfile] = useState<OrganizerProfile | null>(null);
  const [events, setEvents] = useState<OrganizerEvent[]>([]);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [categories, setCategories] = useState<EventCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Modals
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [eventModalOpen, setEventModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<OrganizerEvent | null>(null);

  const [ticketModalOpen, setTicketModalOpen] = useState(false);
  const [targetEventForTicket, setTargetEventForTicket] = useState<OrganizerEvent | null>(null);

  const [staffModalOpen, setStaffModalOpen] = useState(false);
  const [targetEventForStaff, setTargetEventForStaff] = useState<OrganizerEvent | null>(null);
  const [staffAssignments, setStaffAssignments] = useState<StaffAssignment[]>([]);

  // Profile form
  const [orgName, setOrgName] = useState('');
  const [orgEmail, setOrgEmail] = useState('');
  const [orgPhone, setOrgPhone] = useState('');
  const [orgTaxCode, setOrgTaxCode] = useState('');

  // Event form
  const [eventTitle, setEventTitle] = useState('');
  const [eventDesc, setEventDesc] = useState('');
  const [eventVenue, setEventVenue] = useState('');
  const [eventVenueId, setEventVenueId] = useState('');
  const [eventCategory, setEventCategory] = useState('');
  const [saleStart, setSaleStart] = useState('');
  const [saleEnd, setSaleEnd] = useState('');
  const [startsAt, setStartsAt] = useState('');

  // Ticket form
  const [ticketName, setTicketName] = useState('');
  const [ticketMode, setTicketMode] = useState<'GA' | 'SEATED'>('GA');
  const [ticketPrice, setTicketPrice] = useState(150000);
  const [ticketCapacity, setTicketCapacity] = useState(500);
  const [seatLabels, setSeatLabels] = useState('A1, A2, A3, A4, A5, B1, B2, B3, B4, B5');

  // Staff form
  const [staffEmail, setStaffEmail] = useState('');

  const loadData = async () => {
    if (!api.organizer) return;
    setLoading(true);
    setError(null);
    try {
      const [p, v, c, evts] = await Promise.all([
        api.organizer.getProfile().catch(() => null),
        api.organizer.venues().catch(() => []),
        api.organizer.categories().catch(() => []),
        api.organizer.events().catch(() => [])
      ]);
      setProfile(p);
      setVenues(v);
      setCategories(c);
      setEvents(evts);
      if (p) {
        setOrgName(p.organizationName);
        setOrgEmail(p.contactEmail);
        setOrgPhone(p.phone);
        setOrgTaxCode(p.taxCode || '');
      } else if (user) {
        setOrgName(user.displayName || 'Doanh nghiệp tổ chức');
        setOrgEmail(user.email || '');
        setOrgPhone(user.phone || '');
      }
    } catch (err: any) {
      setError(err.message || 'Không thể tải dữ liệu Nhà tổ chức');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }
    loadData();
  }, [user]);

  const showNotification = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 4000);
  };

  const handleSaveProfile = async (e: any) => {
    e.preventDefault();
    if (!api.organizer) return;
    try {
      const saved = await api.organizer.saveProfile({
        organizationName: orgName,
        contactEmail: orgEmail,
        phone: orgPhone,
        taxCode: orgTaxCode
      });
      setProfile(saved);
      setProfileModalOpen(false);
      showNotification('Hồ sơ đối tác đã được cập nhật thành công!');
    } catch (err: any) {
      setError(err.message || 'Lỗi khi lưu hồ sơ đối tác');
    }
  };

  const openCreateEventModal = () => {
    setEditingEvent(null);
    setEventTitle('');
    setEventDesc('');
    setEventVenue('');
    setEventVenueId('');
    setEventCategory(categories[0]?.id || 'CONCERT');
    const now = new Date();
    const sStart = new Date(now.getTime() + 2 * 3600000).toISOString().slice(0, 16);
    const sEnd = new Date(now.getTime() + 72 * 3600000).toISOString().slice(0, 16);
    const evStart = new Date(now.getTime() + 96 * 3600000).toISOString().slice(0, 16);
    setSaleStart(sStart);
    setSaleEnd(sEnd);
    setStartsAt(evStart);
    setEventModalOpen(true);
  };

  const openEditEventModal = (ev: OrganizerEvent) => {
    setEditingEvent(ev);
    setEventTitle(ev.title);
    setEventDesc(ev.description || '');
    setEventVenue(ev.venue);
    setEventVenueId(ev.venueId || '');
    setEventCategory(ev.categoryId || '');
    setSaleStart(ev.saleStartAt.slice(0, 16));
    setSaleEnd(ev.saleEndAt.slice(0, 16));
    setStartsAt(ev.startsAt.slice(0, 16));
    setEventModalOpen(true);
  };

  const handleSaveEvent = async (e: any) => {
    e.preventDefault();
    if (!api.organizer) return;
    try {
      const payload = {
        title: eventTitle,
        description: eventDesc,
        venue: eventVenue,
        venueId: eventVenueId || undefined,
        categoryId: eventCategory || undefined,
        saleStartAt: new Date(saleStart).toISOString(),
        saleEndAt: new Date(saleEnd).toISOString(),
        startsAt: new Date(startsAt).toISOString()
      };

      if (editingEvent) {
        await api.organizer.updateEvent(editingEvent.id, payload);
        showNotification('Cập nhật sự kiện thành công!');
      } else {
        await api.organizer.createEvent(payload);
        showNotification('Đã tạo sự kiện mới ở trạng thái Bản nháp (DRAFT)!');
      }
      setEventModalOpen(false);
      loadData();
    } catch (err: any) {
      setError(err.message || 'Lỗi khi lưu sự kiện');
    }
  };

  const handlePublishEvent = async (eventId: string) => {
    if (!api.organizer) return;
    if (!confirm('Bạn có chắc muốn xuất bản sự kiện này lên Public Catalog để khán giả đặt vé?')) return;
    try {
      await api.organizer.publishEvent(eventId);
      showNotification('Chúc mừng! Sự kiện đã được xuất bản mở bán công khai!');
      loadData();
    } catch (err: any) {
      setError(err.message || 'Không thể xuất bản sự kiện');
    }
  };

  const openTicketModal = (ev: OrganizerEvent) => {
    setTargetEventForTicket(ev);
    setTicketName('Vé Tiêu Chuẩn');
    setTicketMode('GA');
    setTicketPrice(150000);
    setTicketCapacity(500);
    setSeatLabels('A1, A2, A3, A4, A5, B1, B2, B3, B4, B5');
    setTicketModalOpen(true);
  };

  const handleAddTicketType = async (e: any) => {
    e.preventDefault();
    if (!api.organizer || !targetEventForTicket) return;
    try {
      const labels = ticketMode === 'SEATED'
        ? seatLabels.split(',').map(s => s.trim()).filter(Boolean)
        : undefined;

      await api.organizer.addTicketType(targetEventForTicket.id, {
        name: ticketName,
        mode: ticketMode,
        priceVnd: Number(ticketPrice),
        capacity: ticketMode === 'GA' ? Number(ticketCapacity) : undefined,
        seatLabels: labels
      });

      showNotification('Đã thêm loại vé thành công!');
      setTicketModalOpen(false);
      loadData();
    } catch (err: any) {
      setError(err.message || 'Lỗi khi thêm loại vé');
    }
  };

  const openStaffModal = async (ev: OrganizerEvent) => {
    setTargetEventForStaff(ev);
    setStaffEmail('');
    setStaffModalOpen(true);
    if (!api.organizer) return;
    try {
      const staffList = await api.organizer.staff(ev.id);
      setStaffAssignments(staffList);
    } catch (err: any) {
      setError(err.message || 'Không thể tải danh sách nhân sự');
    }
  };

  const handleAssignStaff = async (e: any) => {
    e.preventDefault();
    if (!api.organizer || !targetEventForStaff) return;
    try {
      await api.organizer.assignStaff(targetEventForStaff.id, { staffEmail });
      showNotification('Đã phân công nhân viên soát vé thành công!');
      setStaffEmail('');
      const staffList = await api.organizer.staff(targetEventForStaff.id);
      setStaffAssignments(staffList);
    } catch (err: any) {
      setError(err.message || 'Lỗi khi gán nhân viên');
    }
  };

  const handleRevokeStaff = async (staffUserId: string) => {
    if (!api.organizer || !targetEventForStaff) return;
    if (!confirm('Bạn có chắc muốn thu hồi quyền soát vé của nhân viên này?')) return;
    try {
      await api.organizer.revokeStaff(targetEventForStaff.id, staffUserId);
      showNotification('Đã thu hồi quyền soát vé thành công!');
      const staffList = await api.organizer.staff(targetEventForStaff.id);
      setStaffAssignments(staffList);
    } catch (err: any) {
      setError(err.message || 'Lỗi khi thu hồi quyền');
    }
  };

  if (!user) {
    return h('div', { className: 'organizer-page-container' },
      h('div', { className: 'organizer-hero' },
        h('h1', null, '🎪 Khu Vực Nhà Tổ Chức Sự Kiện (EventFlow Organizer)'),
        h('p', null, 'Nền tảng quản lý sự kiện, sơ đồ khán phòng và phân bổ nhân sự chuyên nghiệp.')
      ),
      h('div', { className: 'auth-required-panel' },
        h('span', { className: 'auth-lock-icon' }, '🔒'),
        h('h2', null, 'Yêu Cầu Đăng Nhập'),
        h('p', null, 'Vui lòng đăng nhập với tài khoản Nhà tổ chức (ORGANIZER) hoặc Quản trị viên để truy cập.'),
        h('button', {
          type: 'button',
          className: 'btn-auth-login large-btn',
          onClick: onOpenAuth
        }, 'Đăng Nhập Ngay')
      )
    );
  }

  const totalCap = events.reduce((sum, e) => sum + (e.totalCapacity || 0), 0);
  const totalSold = events.reduce((sum, e) => sum + (e.soldTickets || 0), 0);
  const publishedCount = events.filter(e => e.status === 'PUBLISHED').length;
  const draftCount = events.filter(e => e.status === 'DRAFT').length;

  return h('div', { className: 'organizer-page-container' },
    // Header Hero Banner
    h('header', { className: 'organizer-header-banner' },
      h('div', { className: 'organizer-header-left' },
        h('div', { className: 'organizer-badge-tag' }, '🎪 Phân Hệ Đối Tác Tổ Chức'),
        h('h1', { className: 'organizer-title' }, profile?.organizationName || 'Bàn Làm Việc Nhà Tổ Chức'),
        h('p', { className: 'organizer-subtitle' },
          'Quản lý vòng đời sự kiện, cấu hình vé GA/Seated, xuất bản mở bán và phân bổ nhân sự soát vé.'
        )
      ),
      h('div', { className: 'organizer-header-right' },
        h('button', {
          type: 'button',
          className: 'btn-profile-settings',
          onClick: () => setProfileModalOpen(true)
        }, '⚙️ Hồ Sơ Đối Tác'),
        h('button', {
          type: 'button',
          className: 'btn-create-event-primary',
          onClick: openCreateEventModal
        }, '➕ Tạo Sự Kiện Mới')
      )
    ),

    // Notifications
    successMsg && h('div', { className: 'alert-banner success' }, '✅ ' + successMsg),
    error && h('div', { className: 'alert-banner error' },
      '⚠️ ' + error,
      h('button', { type: 'button', className: 'btn-close-alert', onClick: () => setError(null) }, '✕')
    ),

    // Quick Stats Bar
    h('div', { className: 'organizer-stats-grid' },
      h('div', { className: 'stat-card' },
        h('span', { className: 'stat-label' }, 'Tổng sự kiện'),
        h('span', { className: 'stat-value' }, events.length)
      ),
      h('div', { className: 'stat-card' },
        h('span', { className: 'stat-label' }, 'Đang mở bán'),
        h('span', { className: 'stat-value published-val' }, publishedCount)
      ),
      h('div', { className: 'stat-card' },
        h('span', { className: 'stat-label' }, 'Bản nháp'),
        h('span', { className: 'stat-value draft-val' }, draftCount)
      ),
      h('div', { className: 'stat-card' },
        h('span', { className: 'stat-label' }, 'Tổng vé phát hành'),
        h('span', { className: 'stat-value' }, totalCap.toLocaleString('vi-VN'))
      ),
      h('div', { className: 'stat-card' },
        h('span', { className: 'stat-label' }, 'Vé đã bán'),
        h('span', { className: 'stat-value sold-val' }, totalSold.toLocaleString('vi-VN'))
      )
    ),

    // Main Content: Events Table
    h('section', { className: 'organizer-events-section' },
      h('div', { className: 'section-toolbar' },
        h('h2', { className: 'section-heading' }, 'Danh Sách Sự Kiện Quản Lý'),
        h('button', {
          type: 'button',
          className: 'btn-refresh-data',
          onClick: loadData
        }, '🔄 Tải Lại')
      ),

      loading ? h('div', { className: 'loading-state' }, 'Đang tải danh sách sự kiện...') :
      events.length === 0 ? h('div', { className: 'empty-events-panel' },
        h('span', { className: 'empty-icon' }, '🎭'),
        h('h3', null, 'Bạn chưa có sự kiện nào'),
        h('p', null, 'Hãy bấm "Tạo sự kiện mới" để bắt đầu thiết lập chương trình và mở bán vé.'),
        h('button', {
          type: 'button',
          className: 'btn-create-event-primary',
          onClick: openCreateEventModal
        }, '➕ Tạo Sự Kiện Ngay')
      ) :
      h('div', { className: 'events-table-wrapper' },
        h('table', { className: 'organizer-table' },
          h('thead', null,
            h('tr', null,
              h('th', null, 'Tên Sự Kiện & Thể Loại'),
              h('th', null, 'Địa Điểm'),
              h('th', null, 'Thời Gian Diễn Ra'),
              h('th', null, 'Hạng Vé / Sức Chứa'),
              h('th', null, 'Trạng Thái'),
              h('th', { className: 'text-right' }, 'Thao Tác')
            )
          ),
          h('tbody', null,
            events.map(ev => {
              const statusClass = ev.status === 'PUBLISHED' ? 'status-published' :
                                  ev.status === 'DRAFT' ? 'status-draft' : 'status-closed';
              const statusLabel = ev.status === 'PUBLISHED' ? '🟢 Mở Bán' :
                                  ev.status === 'DRAFT' ? '🟡 Bản Nháp' : '⚪ Đã Đóng';
              return h('tr', { key: ev.id, className: 'event-row' },
                h('td', null,
                  h('strong', { className: 'event-title-link', onClick: () => openEditEventModal(ev) }, ev.title),
                  h('div', { className: 'event-submeta' },
                    h('span', { className: 'category-pill' }, ev.categoryId || 'Sự kiện'),
                    ' · ID: ', ev.id.slice(0, 8)
                  )
                ),
                h('td', null,
                  h('span', { className: 'venue-name' }, ev.venue)
                ),
                h('td', null,
                  h('div', { className: 'date-text' }, formatDateTime(ev.startsAt)),
                  h('div', { className: 'sale-window' }, 'Bán: ' + formatDateTime(ev.saleStartAt))
                ),
                h('td', null,
                  h('div', { className: 'tickets-summary' },
                    ev.ticketTypes && ev.ticketTypes.length > 0 ? (
                      h('span', null,
                        ev.ticketTypes.map(t => `${t.name} (${t.mode}): ${t.capacity || 0}`).join(' | ')
                      )
                    ) : h('span', { className: 'no-tickets-warning' }, '⚠️ Chưa cấu hình vé')
                  ),
                  h('div', { className: 'capacity-stat' },
                    `Tổng: ${ev.totalCapacity.toLocaleString('vi-VN')} vé (Bán: ${ev.soldTickets})`
                  )
                ),
                h('td', null,
                  h('span', { className: 'status-badge ' + statusClass }, statusLabel)
                ),
                h('td', { className: 'text-right' },
                  h('div', { className: 'action-btn-group' },
                    ev.status === 'DRAFT' && h('button', {
                      type: 'button',
                      className: 'btn-action-publish',
                      onClick: () => handlePublishEvent(ev.id),
                      title: 'Xuất bản sự kiện mở bán công khai'
                    }, '🚀 Mở Bán'),
                    h('button', {
                      type: 'button',
                      className: 'btn-action-tickets',
                      onClick: () => openTicketModal(ev),
                      title: 'Thêm hoặc cấu hình loại vé'
                    }, '🎟️ Thêm Vé'),
                    h('button', {
                      type: 'button',
                      className: 'btn-action-staff',
                      onClick: () => openStaffModal(ev),
                      title: 'Phân công nhân viên soát vé'
                    }, '👥 Staff'),
                    h('button', {
                      type: 'button',
                      className: 'btn-action-edit',
                      onClick: () => openEditEventModal(ev),
                      title: 'Chỉnh sửa thông tin sự kiện'
                    }, '✏️ Sửa')
                  )
                )
              );
            })
          )
        )
      )
    ),

    // Modal: Create / Edit Event
    eventModalOpen && h('div', { className: 'modal-backdrop' },
      h('div', { className: 'modal-dialog modal-large' },
        h('div', { className: 'modal-header' },
          h('h3', null, editingEvent ? 'Chỉnh Sửa Sự Kiện' : 'Khởi Tạo Sự Kiện Mới (Bản Nháp)'),
          h('button', { type: 'button', className: 'btn-close-modal', onClick: () => setEventModalOpen(false) }, '✕')
        ),
        h('form', { onSubmit: handleSaveEvent, className: 'modal-form' },
          h('div', { className: 'form-group' },
            h('label', null, 'Tiêu đề sự kiện *'),
            h('input', {
              type: 'text',
              className: 'form-input',
              required: true,
              placeholder: 'Ví dụ: Đại nhạc hội Summer Concert 2026',
              value: eventTitle,
              onChange: (e: any) => setEventTitle(e.target.value)
            })
          ),
          h('div', { className: 'form-row' },
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Thể loại sự kiện'),
              h('select', {
                className: 'form-select',
                value: eventCategory,
                onChange: (e: any) => setEventCategory(e.target.value)
              },
                categories.map(c => h('option', { key: c.id, value: c.id }, c.name))
              )
            ),
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Địa điểm chuẩn hóa'),
              h('select', {
                className: 'form-select',
                value: eventVenueId,
                onChange: (e: any) => {
                  const id = e.target.value;
                  setEventVenueId(id);
                  const found = venues.find(v => v.id === id);
                  if (found) setEventVenue(found.name + ' - ' + found.address);
                }
              },
                h('option', { value: '' }, '-- Chọn địa điểm biểu diễn --'),
                venues.map(v => h('option', { key: v.id, value: v.id }, `${v.name} (${v.city} - ${v.capacity} chỗ)`))
              )
            )
          ),
          h('div', { className: 'form-group' },
            h('label', null, 'Tên địa điểm chi tiết *'),
            h('input', {
              type: 'text',
              className: 'form-input',
              required: true,
              placeholder: 'Ví dụ: Sân vận động Mỹ Đình, Hà Nội',
              value: eventVenue,
              onChange: (e: any) => setEventVenue(e.target.value)
            })
          ),
          h('div', { className: 'form-row' },
            h('div', { className: 'form-group col-third' },
              h('label', null, 'Thời gian mở bán *'),
              h('input', {
                type: 'datetime-local',
                className: 'form-input',
                required: true,
                value: saleStart,
                onChange: (e: any) => setSaleStart(e.target.value)
              })
            ),
            h('div', { className: 'form-group col-third' },
              h('label', null, 'Kết thúc bán *'),
              h('input', {
                type: 'datetime-local',
                className: 'form-input',
                required: true,
                value: saleEnd,
                onChange: (e: any) => setSaleEnd(e.target.value)
              })
            ),
            h('div', { className: 'form-group col-third' },
              h('label', null, 'Bắt đầu sự kiện *'),
              h('input', {
                type: 'datetime-local',
                className: 'form-input',
                required: true,
                value: startsAt,
                onChange: (e: any) => setStartsAt(e.target.value)
              })
            )
          ),
          h('div', { className: 'form-group' },
            h('label', null, 'Mô tả chương trình & nghệ sĩ tham gia'),
            h('textarea', {
              className: 'form-textarea',
              rows: 3,
              placeholder: 'Giới thiệu thông tin đêm nhạc, line-up nghệ sĩ, quy định tham gia...',
              value: eventDesc,
              onChange: (e: any) => setEventDesc(e.target.value)
            })
          ),
          h('div', { className: 'modal-footer' },
            h('button', { type: 'button', className: 'btn-secondary', onClick: () => setEventModalOpen(false) }, 'Hủy'),
            h('button', { type: 'submit', className: 'btn-primary' }, editingEvent ? 'Lưu Thay Đổi' : 'Tạo Bản Nháp')
          )
        )
      )
    ),

    // Modal: Add Ticket Type
    ticketModalOpen && targetEventForTicket && h('div', { className: 'modal-backdrop' },
      h('div', { className: 'modal-dialog' },
        h('div', { className: 'modal-header' },
          h('h3', null, 'Thêm Hạng Vé Cho Sự Kiện'),
          h('button', { type: 'button', className: 'btn-close-modal', onClick: () => setTicketModalOpen(false) }, '✕')
        ),
        h('form', { onSubmit: handleAddTicketType, className: 'modal-form' },
          h('div', { className: 'form-group' },
            h('label', null, 'Sự kiện: '),
            h('strong', null, targetEventForTicket.title)
          ),
          h('div', { className: 'form-group' },
            h('label', null, 'Tên hạng vé *'),
            h('input', {
              type: 'text',
              className: 'form-input',
              required: true,
              placeholder: 'Ví dụ: Vé Phổ Thông GA, Vé VIP Khán Phòng',
              value: ticketName,
              onChange: (e: any) => setTicketName(e.target.value)
            })
          ),
          h('div', { className: 'form-row' },
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Hình thức vé'),
              h('select', {
                className: 'form-select',
                value: ticketMode,
                onChange: (e: any) => setTicketMode(e.target.value as any)
              },
                h('option', { value: 'GA' }, 'Vé đứng / Phổ thông (GA)'),
                h('option', { value: 'SEATED' }, 'Vé ghế chỉ định (Seated)')
              )
            ),
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Đơn giá (VNĐ) *'),
              h('input', {
                type: 'number',
                className: 'form-input',
                required: true,
                min: 0,
                step: 10000,
                value: ticketPrice,
                onChange: (e: any) => setTicketPrice(e.target.value)
              })
            )
          ),
          ticketMode === 'GA' ? h('div', { className: 'form-group' },
            h('label', null, 'Sức chứa / Số lượng vé phát hành *'),
            h('input', {
              type: 'number',
              className: 'form-input',
              required: true,
              min: 1,
              max: 100000,
              value: ticketCapacity,
              onChange: (e: any) => setTicketCapacity(e.target.value)
            }),
            h('small', { className: 'form-hint' }, 'Hệ thống chống bán lố (Zero Oversell) bảo vệ tồn kho bằng ACID Row Lock.')
          ) : h('div', { className: 'form-group' },
            h('label', null, 'Danh sách nhãn ghế (Cách nhau bởi dấu phẩy) *'),
            h('textarea', {
              className: 'form-textarea',
              rows: 3,
              required: true,
              placeholder: 'A1, A2, A3, B1, B2, B3...',
              value: seatLabels,
              onChange: (e: any) => setSeatLabels(e.target.value)
            }),
            h('small', { className: 'form-hint' }, 'Mỗi nhãn ghế là một vị trí độc nhất trên sơ đồ khán phòng.')
          ),
          h('div', { className: 'modal-footer' },
            h('button', { type: 'button', className: 'btn-secondary', onClick: () => setTicketModalOpen(false) }, 'Đóng'),
            h('button', { type: 'submit', className: 'btn-primary' }, 'Lưu Hạng Vé')
          )
        )
      )
    ),

    // Modal: Staff Assignment
    staffModalOpen && targetEventForStaff && h('div', { className: 'modal-backdrop' },
      h('div', { className: 'modal-dialog modal-large' },
        h('div', { className: 'modal-header' },
          h('h3', null, 'Phân Công Nhân Sự Soát Vé Sự Kiện'),
          h('button', { type: 'button', className: 'btn-close-modal', onClick: () => setStaffModalOpen(false) }, '✕')
        ),
        h('div', { className: 'staff-modal-content' },
          h('div', { className: 'event-banner-sub' },
            'Sự kiện: ', h('strong', null, targetEventForStaff.title),
            ' · Ngày: ', formatDateTime(targetEventForStaff.startsAt)
          ),

          // Add Staff Form
          h('form', { onSubmit: handleAssignStaff, className: 'staff-assign-form' },
            h('div', { className: 'form-row' },
              h('div', { className: 'form-group col-three-fourth' },
                h('input', {
                  type: 'email',
                  className: 'form-input',
                  required: true,
                  placeholder: 'Nhập email nhân viên có tài khoản STAFF (ví dụ: staff@eventflow.vn)',
                  value: staffEmail,
                  onChange: (e: any) => setStaffEmail(e.target.value)
                })
              ),
              h('div', { className: 'form-group col-one-fourth' },
                h('button', { type: 'submit', className: 'btn-primary full-width' }, '➕ Gán Nhân Viên')
              )
            )
          ),

          // Staff List
          h('h4', { className: 'subheading' }, 'Danh Sách Nhân Viên Đang Được Phân Công:'),
          staffAssignments.length === 0 ? h('p', { className: 'empty-text' }, 'Chưa có nhân viên nào được phân công phụ trách sự kiện này.') :
          h('div', { className: 'staff-table-wrapper' },
            h('table', { className: 'staff-table' },
              h('thead', null,
                h('tr', null,
                  h('th', null, 'Họ Tên'),
                  h('th', null, 'Email'),
                  h('th', null, 'Thời Điểm Gán'),
                  h('th', { className: 'text-right' }, 'Thao Tác')
                )
              ),
              h('tbody', null,
                staffAssignments.map(s => h('tr', { key: s.id },
                  h('td', null, h('strong', null, s.staffName)),
                  h('td', null, s.staffEmail),
                  h('td', null, formatDateTime(s.assignedAt)),
                  h('td', { className: 'text-right' },
                    h('button', {
                      type: 'button',
                      className: 'btn-revoke-staff',
                      onClick: () => handleRevokeStaff(s.staffUserId)
                    }, 'Thu hồi')
                  )
                ))
              )
            )
          )
        ),
        h('div', { className: 'modal-footer' },
          h('button', { type: 'button', className: 'btn-secondary', onClick: () => setStaffModalOpen(false) }, 'Đóng')
        )
      )
    ),

    // Modal: Partner Profile Setup
    profileModalOpen && h('div', { className: 'modal-backdrop' },
      h('div', { className: 'modal-dialog' },
        h('div', { className: 'modal-header' },
          h('h3', null, 'Cập Nhật Hồ Sơ Đối Tác Tổ Chức'),
          h('button', { type: 'button', className: 'btn-close-modal', onClick: () => setProfileModalOpen(false) }, '✕')
        ),
        h('form', { onSubmit: handleSaveProfile, className: 'modal-form' },
          h('div', { className: 'form-group' },
            h('label', null, 'Tên doanh nghiệp / Đơn vị tổ chức *'),
            h('input', {
              type: 'text',
              className: 'form-input',
              required: true,
              placeholder: 'Ví dụ: Công ty CP Giải Trí EventFlow',
              value: orgName,
              onChange: (e: any) => setOrgName(e.target.value)
            })
          ),
          h('div', { className: 'form-group' },
            h('label', null, 'Email liên hệ nghiệp vụ *'),
            h('input', {
              type: 'email',
              className: 'form-input',
              required: true,
              placeholder: 'contact@eventflow.vn',
              value: orgEmail,
              onChange: (e: any) => setOrgEmail(e.target.value)
            })
          ),
          h('div', { className: 'form-row' },
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Số điện thoại liên hệ *'),
              h('input', {
                type: 'tel',
                className: 'form-input',
                required: true,
                placeholder: '0901234567',
                value: orgPhone,
                onChange: (e: any) => setOrgPhone(e.target.value)
              })
            ),
            h('div', { className: 'form-group col-half' },
              h('label', null, 'Mã số thuế doanh nghiệp'),
              h('input', {
                type: 'text',
                className: 'form-input',
                placeholder: '0312345678',
                value: orgTaxCode,
                onChange: (e: any) => setOrgTaxCode(e.target.value)
              })
            )
          ),
          h('div', { className: 'modal-footer' },
            h('button', { type: 'button', className: 'btn-secondary', onClick: () => setProfileModalOpen(false) }, 'Hủy'),
            h('button', { type: 'submit', className: 'btn-primary' }, 'Lưu Hồ Sơ')
          )
        )
      )
    )
  );
}
