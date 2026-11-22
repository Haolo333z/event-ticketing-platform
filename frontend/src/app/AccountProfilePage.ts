import { createElement as h, useState, useEffect } from 'react';
import type { Api, User, UserProfile, ActiveSession, VatDefaults, TransferItem, WaitlistEntry, EventReminder, OnboardingStatusResponse } from '../api/client';

export interface AccountProfilePageProps {
  api: Api;
  user: User | null;
  onNavigate: (path: string) => void;
  onOpenOnboardingModal?: () => void;
  onUserUpdated?: (updated: User) => void;
}

export function AccountProfilePage({ api, user, onNavigate, onOpenOnboardingModal, onUserUpdated }: AccountProfilePageProps) {
  const [activeTab, setActiveTab] = useState<'profile' | 'security' | 'vat' | 'notifications' | 'organizer'>('profile');
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Tab 1: Profile data
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [displayName, setDisplayName] = useState<string>('');
  const [phone, setPhone] = useState<string>('');
  const [avatarUrl, setAvatarUrl] = useState<string>('');

  // Tab 2: Security & Sessions
  const [oldPassword, setOldPassword] = useState<string>('');
  const [newPassword, setNewPassword] = useState<string>('');
  const [confirmPassword, setConfirmPassword] = useState<string>('');
  const [sessions, setSessions] = useState<ActiveSession[]>([]);

  // Tab 3: VAT Defaults
  const [vatDefaults, setVatDefaults] = useState<VatDefaults>({
    companyName: '',
    taxCode: '',
    companyAddress: '',
    recipientEmail: ''
  });

  // Tab 4: Waitlists, Reminders, Transfers
  const [waitlists, setWaitlists] = useState<WaitlistEntry[]>([]);
  const [reminders, setReminders] = useState<EventReminder[]>([]);
  const [transferTab, setTransferTab] = useState<'sent' | 'received'>('sent');
  const [sentTransfers, setSentTransfers] = useState<TransferItem[]>([]);
  const [receivedTransfers, setReceivedTransfers] = useState<TransferItem[]>([]);

  // Tab 5: Organizer Partner
  const [onboardingStatus, setOnboardingStatus] = useState<OnboardingStatusResponse | null>(null);

  function showToast(text: string, type: 'success' | 'error' = 'success') {
    setToastMessage({ text, type });
    setTimeout(() => setToastMessage(null), 4000);
  }

  // Load initial data
  useEffect(() => {
    if (!user) {
      setLoading(false);
      return;
    }
    loadAllData();
  }, [user]);

  async function loadAllData() {
    setLoading(true);
    try {
      const tasks: Promise<unknown>[] = [];

      // 1. Profile Core
      const pProfile = api.profile?.get ? api.profile.get().then(prof => {
        if (prof) {
          setProfile(prof);
          setDisplayName(prof.displayName || '');
          setPhone(prof.phone || '');
          setAvatarUrl(prof.avatarUrl || '');
        }
      }).catch(err => {
        console.warn('Could not load profile:', err);
      }) : Promise.resolve();
      tasks.push(pProfile);

      // 2. VAT Defaults
      const pVat = api.profile?.vatDefaults ? api.profile.vatDefaults().then(vat => {
        if (vat) setVatDefaults(vat);
      }).catch(err => {
        console.warn('Could not load VAT defaults:', err);
      }) : Promise.resolve();
      tasks.push(pVat);

      // 3. Sessions
      const pSessions = api.profile?.sessions ? api.profile.sessions().then(sess => {
        if (sess?.sessions) setSessions(sess.sessions);
      }).catch(err => {
        console.warn('Could not load sessions:', err);
      }) : Promise.resolve();
      tasks.push(pSessions);

      // 4. Waitlists
      const pWaitlists = api.v3?.myWaitlists ? api.v3.myWaitlists().then(wl => {
        if (wl?.items) setWaitlists(wl.items);
      }).catch(err => {
        console.warn('Could not load waitlists:', err);
      }) : Promise.resolve();
      tasks.push(pWaitlists);

      // 5. Reminders
      const pReminders = api.reminders?.list ? api.reminders.list().then(rm => {
        if (rm?.items) setReminders(rm.items);
      }).catch(err => {
        console.warn('Could not load reminders:', err);
      }) : Promise.resolve();
      tasks.push(pReminders);

      // 6. Transfers Sent
      const pTransfersSent = api.transfers?.sent ? api.transfers.sent().then(st => {
        if (st?.items) setSentTransfers(st.items);
      }).catch(err => {
        console.warn('Could not load sent transfers:', err);
      }) : Promise.resolve();
      tasks.push(pTransfersSent);

      // 7. Transfers Received
      const pTransfersReceived = api.transfers?.received ? api.transfers.received().then(rt => {
        if (rt?.items) setReceivedTransfers(rt.items);
      }).catch(err => {
        console.warn('Could not load received transfers:', err);
      }) : Promise.resolve();
      tasks.push(pTransfersReceived);

      // 8. B2B Organizer Onboard status
      const pOnboard = api.onboard?.status ? api.onboard.status().then(ob => {
        if (ob) setOnboardingStatus(ob);
      }).catch(err => {
        console.warn('Could not load onboarding status:', err);
        setOnboardingStatus({ hasSubmitted: false, status: 'NONE' });
      }) : Promise.resolve();
      tasks.push(pOnboard);

      await Promise.allSettled(tasks);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể tải thông tin hồ sơ';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }

  // Handle Tab 1: Update Profile
  async function handleSaveProfile(e: React.FormEvent) {
    e.preventDefault();
    if (!displayName.trim()) {
      showToast('Họ tên không được để trống', 'error');
      return;
    }
    setSaving(true);
    try {
      if (api.profile) {
        const updated = await api.profile.update({
          displayName: displayName.trim(),
          phone: phone.trim(),
          avatarUrl: avatarUrl.trim() || undefined
        });
        setProfile(updated);
        showToast('Cập nhật thông tin cá nhân thành công!');
        if (onUserUpdated && user) {
          onUserUpdated({
            ...user,
            displayName: updated.displayName,
            phone: updated.phone
          });
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi khi cập nhật thông tin';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }

  // Handle Tab 2: Change Password
  async function handleChangePassword(e: React.FormEvent) {
    e.preventDefault();
    if (newPassword.length < 6) {
      showToast('Mật khẩu mới phải có tối thiểu 6 ký tự', 'error');
      return;
    }
    if (newPassword !== confirmPassword) {
      showToast('Xác nhận mật khẩu mới không trùng khớp', 'error');
      return;
    }
    setSaving(true);
    try {
      if (api.profile) {
        const res = await api.profile.changePassword({
          oldPassword: oldPassword || undefined,
          newPassword
        });
        showToast(res.message || 'Đổi mật khẩu thành công!');
        setOldPassword('');
        setNewPassword('');
        setConfirmPassword('');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi khi đổi mật khẩu';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }

  // Handle Tab 2: Revoke Session
  async function handleRevokeSession(familyId: string) {
    try {
      if (api.profile) {
        await api.profile.revokeSession(familyId);
        showToast('Đã thu hồi phiên hoạt động');
        setSessions(prev => prev.filter(s => s.familyId !== familyId));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể thu hồi phiên';
      showToast(msg, 'error');
    }
  }

  // Handle Tab 2: Revoke Other Sessions
  async function handleRevokeOtherSessions() {
    try {
      if (api.profile) {
        await api.profile.revokeOtherSessions();
        showToast('Đã đăng xuất khỏi mọi thiết bị khác');
        setSessions(prev => prev.filter(s => s.current));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể đăng xuất các thiết bị khác';
      showToast(msg, 'error');
    }
  }

  // Handle Tab 3: Save VAT Defaults
  async function handleSaveVat(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      if (api.profile) {
        const updated = await api.profile.updateVatDefaults(vatDefaults);
        setVatDefaults(updated);
        showToast('Đã lưu thông tin xuất hóa đơn VAT mặc định!');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể lưu thông tin VAT';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  }

  // Handle Tab 4: Cancel Waitlist
  async function handleCancelWaitlist(eventId: string) {
    try {
      if (api.v3?.cancelWaitlist) {
        await api.v3.cancelWaitlist(eventId);
        showToast('Đã hủy tham gia danh sách chờ');
        setWaitlists(prev => prev.filter(w => w.eventId !== eventId));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi khi hủy danh sách chờ';
      showToast(msg, 'error');
    }
  }

  // Handle Tab 4: Cancel Reminder
  async function handleCancelReminder(eventId: string) {
    try {
      if (api.reminders?.cancel) {
        await api.reminders.cancel(eventId);
        showToast('Đã hủy nhắc hẹn sự kiện');
        setReminders(prev => prev.filter(r => r.eventId !== eventId));
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Lỗi khi hủy nhắc hẹn';
      showToast(msg, 'error');
    }
  }

  if (!user) {
    return h('div', { className: 'account-profile-container' },
      h('div', { className: 'account-unauth-banner' },
        h('div', { className: 'unauth-icon' }, '🔒'),
        h('h2', null, 'Yêu cầu đăng nhập'),
        h('p', null, 'Vui lòng đăng nhập để truy cập và quản lý thông tin tài khoản cá nhân của bạn.'),
        h('button', {
          type: 'button',
          className: 'btn-primary-action',
          onClick: () => onNavigate('/')
        }, 'Quay về Trang chủ')
      )
    );
  }

  const userRoles = (profile?.roles && profile.roles.length > 0)
    ? profile.roles
    : (user.roles && user.roles.length > 0 ? user.roles : [user.role || 'CUSTOMER']);
  const isOrganizer = userRoles.includes('ORGANIZER') || userRoles.includes('ADMIN');

  return h('div', { className: 'account-profile-container' },
    // Toast Notification
    toastMessage && h('div', { className: 'account-toast toast-' + toastMessage.type },
      toastMessage.type === 'success' ? '✓ ' : '⚠️ ',
      toastMessage.text
    ),

    // Header Card
    h('div', { className: 'account-header-card' },
      h('div', { className: 'account-header-left' },
        h('div', { className: 'account-avatar-large' },
          avatarUrl ? h('img', { src: avatarUrl, alt: displayName, className: 'avatar-img' }) :
          h('span', { className: 'avatar-initial' }, (displayName || user.displayName || 'U').charAt(0).toUpperCase())
        ),
        h('div', { className: 'account-header-info' },
          h('h1', { className: 'account-display-name' }, displayName || user.displayName),
          h('div', { className: 'account-email-row' },
            h('span', { className: 'account-email' }, profile?.email || user.email),
            h('div', { className: 'account-roles-tags' },
              userRoles.map(r => h('span', { key: r, className: 'role-badge-tag role-' + r.toLowerCase() }, r))
            )
          )
        )
      ),
      h('div', { className: 'account-header-actions' },
        h('button', {
          type: 'button',
          className: 'btn-view-tickets-quick',
          onClick: () => onNavigate('/ve-cua-toi')
        }, '🎟️ Ví vé của tôi')
      )
    ),

    // Navigation Tabs Bar
    h('div', { className: 'account-tabs-nav' },
      h('button', {
        type: 'button',
        className: 'tab-item-btn' + (activeTab === 'profile' ? ' active' : ''),
        onClick: () => setActiveTab('profile')
      }, '👤 Thông tin cá nhân'),
      h('button', {
        type: 'button',
        className: 'tab-item-btn' + (activeTab === 'security' ? ' active' : ''),
        onClick: () => setActiveTab('security')
      }, '🛡️ Bảo mật & Phiên'),
      h('button', {
        type: 'button',
        className: 'tab-item-btn' + (activeTab === 'vat' ? ' active' : ''),
        onClick: () => setActiveTab('vat')
      }, '🧾 Hóa đơn VAT'),
      h('button', {
        type: 'button',
        className: 'tab-item-btn' + (activeTab === 'notifications' ? ' active' : ''),
        onClick: () => setActiveTab('notifications')
      }, '🔔 Nhắc hẹn & Chờ vé (' + (waitlists.length + reminders.length) + ')'),
      h('button', {
        type: 'button',
        className: 'tab-item-btn' + (activeTab === 'organizer' ? ' active' : ''),
        onClick: () => setActiveTab('organizer')
      }, '🎪 Đối tác Tổ chức')
    ),

    // Tab Body Content
    loading ? h('div', { className: 'account-loading-spinner' },
      h('div', { className: 'ef-spinner ef-spinner-primary' }),
      h('p', null, 'Đang tải dữ liệu tài khoản...')
    ) : h('div', { className: 'account-tab-panel' },

      // ================= TAB 1: PROFILE =================
      activeTab === 'profile' && h('div', { className: 'tab-content-block' },
        h('div', { className: 'section-heading' },
          h('h2', null, 'Hồ sơ người dùng'),
          h('p', null, 'Quản lý thông tin định danh và tùy chọn hiển thị trên nền tảng EventFlow')
        ),
        h('form', { onSubmit: handleSaveProfile, className: 'account-form' },
          h('div', { className: 'form-group-grid' },
            h('div', { className: 'form-field' },
              h('label', null, 'Họ và tên'),
              h('input', {
                type: 'text',
                className: 'form-input',
                value: displayName,
                onChange: e => setDisplayName(e.target.value),
                required: true
              })
            ),
            h('div', { className: 'form-field' },
              h('label', null, 'Số điện thoại'),
              h('input', {
                type: 'tel',
                className: 'form-input',
                value: phone,
                placeholder: '0901234567',
                onChange: e => setPhone(e.target.value)
              })
            )
          ),
          h('div', { className: 'form-field' },
            h('label', null, 'Email đăng nhập (Cố định)'),
            h('input', {
              type: 'email',
              className: 'form-input input-readonly',
              value: profile?.email || user.email,
              disabled: true,
              readOnly: true
            }),
            h('span', { className: 'input-caption' }, 'Email được sử dụng làm mã định danh duy nhất và không thể thay đổi')
          ),
          h('div', { className: 'form-field' },
            h('label', null, 'Đường dẫn ảnh đại diện (Avatar URL)'),
            h('input', {
              type: 'url',
              className: 'form-input',
              value: avatarUrl,
              placeholder: 'https://example.com/avatar.jpg',
              onChange: e => setAvatarUrl(e.target.value)
            })
          ),
          h('div', { className: 'oauth-identity-card' },
            h('div', { className: 'oauth-card-left' },
              h('span', { className: 'oauth-icon' }, '🌐'),
              h('div', null,
                h('strong', null, 'Liên kết Google OAuth2'),
                h('p', null, profile?.googleLinked ? 'Tài khoản của bạn đã được liên kết với Google 1-Chạm.' : 'Chưa liên kết Google.')
              )
            ),
            h('span', { className: 'oauth-badge ' + (profile?.googleLinked ? 'linked' : 'not-linked') },
              profile?.googleLinked ? '✓ Đã liên kết' : 'Chưa kích hoạt'
            )
          ),
          h('div', { className: 'form-submit-row' },
            h('button', {
              type: 'submit',
              className: 'btn-save-profile',
              disabled: saving
            }, saving ? 'Đang lưu...' : '💾 Lưu thông tin cá nhân')
          )
        )
      ),

      // ================= TAB 2: SECURITY & SESSIONS =================
      activeTab === 'security' && h('div', { className: 'tab-content-block' },
        h('div', { className: 'section-heading' },
          h('h2', null, 'Đổi mật khẩu & Bảo mật'),
          h('p', null, 'Bảo vệ tài khoản bằng mật khẩu mạnh và theo dõi các phiên đăng nhập hoạt động')
        ),
        h('form', { onSubmit: handleChangePassword, className: 'account-form' },
          h('div', { className: 'form-group-grid-3' },
            h('div', { className: 'form-field' },
              h('label', null, 'Mật khẩu hiện tại'),
              h('input', {
                type: 'password',
                className: 'form-input',
                value: oldPassword,
                placeholder: 'Nhập mật khẩu cũ (nếu có)',
                onChange: e => setOldPassword(e.target.value)
              })
            ),
            h('div', { className: 'form-field' },
              h('label', null, 'Mật khẩu mới'),
              h('input', {
                type: 'password',
                className: 'form-input',
                value: newPassword,
                placeholder: 'Tối thiểu 6 ký tự',
                onChange: e => setNewPassword(e.target.value),
                required: true
              })
            ),
            h('div', { className: 'form-field' },
              h('label', null, 'Xác nhận mật khẩu mới'),
              h('input', {
                type: 'password',
                className: 'form-input',
                value: confirmPassword,
                placeholder: 'Nhập lại mật khẩu mới',
                onChange: e => setConfirmPassword(e.target.value),
                required: true
              })
            )
          ),
          h('div', { className: 'form-submit-row' },
            h('button', {
              type: 'submit',
              className: 'btn-change-password',
              disabled: saving
            }, saving ? 'Đang xử lý...' : '🔒 Cập nhật mật khẩu')
          )
        ),

        h('hr', { className: 'section-divider' }),

        h('div', { className: 'sessions-management-section' },
          h('div', { className: 'sessions-heading-row' },
            h('div', null,
              h('h3', null, 'Thiết bị & Phiên hoạt động (Token Families)'),
              h('p', null, 'Danh sách các trình duyệt và thiết bị đang duy trì phiên đăng nhập')
            ),
            sessions.length > 1 && h('button', {
              type: 'button',
              className: 'btn-revoke-others',
              onClick: handleRevokeOtherSessions
            }, '🚪 Đăng xuất khỏi mọi thiết bị khác')
          ),
          h('div', { className: 'sessions-table-wrapper' },
            sessions.length === 0 ? h('p', { className: 'empty-notice' }, 'Không có phiên phụ nào đang hoạt động.') :
            h('table', { className: 'sessions-table' },
              h('thead', null,
                h('tr', null,
                  h('th', null, 'Trình duyệt / Thiết bị'),
                  h('th', null, 'Địa chỉ IP'),
                  h('th', null, 'Thời điểm tạo'),
                  h('th', null, 'Hết hạn'),
                  h('th', null, 'Trạng thái')
                )
              ),
              h('tbody', null,
                sessions.map(s => h('tr', { key: s.familyId, className: s.current ? 'current-session-row' : '' },
                  h('td', null,
                    h('span', { className: 'device-icon' }, s.userAgent?.includes('Mobile') ? '📱' : '💻'),
                    h('span', { className: 'user-agent-text', title: s.userAgent }, s.userAgent ? s.userAgent.substring(0, 45) + '...' : 'Trình duyệt Web')
                  ),
                  h('td', null, s.ipAddress || '127.0.0.1'),
                  h('td', null, new Date(s.createdAt).toLocaleString('vi-VN')),
                  h('td', null, new Date(s.expiresAt).toLocaleDateString('vi-VN')),
                  h('td', null,
                    s.current ? h('span', { className: 'current-session-badge' }, '✓ Phiên hiện tại') :
                    h('button', {
                      type: 'button',
                      className: 'btn-revoke-single',
                      onClick: () => handleRevokeSession(s.familyId)
                    }, 'Thu hồi')
                  )
                ))
              )
            )
          )
        )
      ),

      // ================= TAB 3: VAT INVOICE DEFAULTS =================
      activeTab === 'vat' && h('div', { className: 'tab-content-block' },
        h('div', { className: 'section-heading' },
          h('h2', null, 'Thông tin xuất hóa đơn VAT mặc định'),
          h('p', null, 'Lưu sẵn thông tin doanh nghiệp để tự động điền khi thanh toán vé và tích chọn "Xuất hóa đơn VAT"')
        ),
        h('form', { onSubmit: handleSaveVat, className: 'account-form' },
          h('div', { className: 'form-group-grid' },
            h('div', { className: 'form-field' },
              h('label', null, 'Tên Công ty / Đơn vị mua vé'),
              h('input', {
                type: 'text',
                className: 'form-input',
                value: vatDefaults.companyName || '',
                placeholder: 'Công ty Cổ phần Công nghệ EventFlow',
                onChange: e => setVatDefaults({ ...vatDefaults, companyName: e.target.value })
              })
            ),
            h('div', { className: 'form-field' },
              h('label', null, 'Mã số thuế (MST)'),
              h('input', {
                type: 'text',
                className: 'form-input',
                value: vatDefaults.taxCode || '',
                placeholder: '0101234567',
                onChange: e => setVatDefaults({ ...vatDefaults, taxCode: e.target.value })
              })
            )
          ),
          h('div', { className: 'form-field' },
            h('label', null, 'Địa chỉ trụ sở doanh nghiệp theo ĐKKD'),
            h('input', {
              type: 'text',
              className: 'form-input',
              value: vatDefaults.companyAddress || '',
              placeholder: 'Số 1 Đại Cồ Việt, Phường Bách Khoa, Quận Hai Bà Trưng, Hà Nội',
              onChange: e => setVatDefaults({ ...vatDefaults, companyAddress: e.target.value })
            })
          ),
          h('div', { className: 'form-field' },
            h('label', null, 'Email nhận hóa đơn điện tử (.pdf & .xml)'),
            h('input', {
              type: 'email',
              className: 'form-input',
              value: vatDefaults.recipientEmail || '',
              placeholder: 'ketoan@company.com',
              onChange: e => setVatDefaults({ ...vatDefaults, recipientEmail: e.target.value })
            }),
            h('span', { className: 'input-caption' }, 'Hóa đơn điện tử hợp lệ theo Thông tư 78/2021/TT-BTC sẽ được gửi tự động về email này')
          ),
          h('div', { className: 'form-submit-row' },
            h('button', {
              type: 'submit',
              className: 'btn-save-profile',
              disabled: saving
            }, saving ? 'Đang lưu...' : '💾 Lưu thông tin hóa đơn VAT')
          )
        )
      ),

      // ================= TAB 4: NOTIFICATIONS & REMINDERS =================
      activeTab === 'notifications' && h('div', { className: 'tab-content-block' },
        h('div', { className: 'section-heading' },
          h('h2', null, 'Danh sách chờ & Nhắc hẹn mở bán'),
          h('p', null, 'Theo dõi các lượt đăng ký vé tự động và sự kiện đã kích hoạt thông báo')
        ),

        // 1. Waitlists
        h('div', { className: 'sub-section' },
          h('h3', { className: 'sub-title' }, '⏳ Danh sách chờ vé thông minh (Smart Waitlist)'),
          waitlists.length === 0 ? h('p', { className: 'empty-notice' }, 'Bạn chưa tham gia danh sách chờ vé nào.') :
          h('div', { className: 'cards-list-grid' },
            waitlists.map(w => h('div', { key: w.id, className: 'item-card-record' },
              h('div', { className: 'item-card-header' },
                h('strong', null, w.eventTitle),
                h('span', { className: 'status-tag tag-' + w.status.toLowerCase() }, w.status)
              ),
              h('p', { className: 'item-card-sub' },
                'Số lượng vé chờ: ', h('b', null, w.desiredQuantity),
                ' · Vị trí hàng đợi: ', h('span', { className: 'queue-badge' }, '#' + w.queuePosition)
              ),
              h('div', { className: 'item-card-footer' },
                h('span', { className: 'time-text' }, new Date(w.createdAt).toLocaleString('vi-VN')),
                w.status === 'WAITING' && h('button', {
                  type: 'button',
                  className: 'btn-cancel-item',
                  onClick: () => handleCancelWaitlist(w.eventId)
                }, 'Hủy chờ')
              )
            ))
          )
        ),

        h('hr', { className: 'section-divider' }),

        // 2. Reminders
        h('div', { className: 'sub-section' },
          h('h3', { className: 'sub-title' }, '⏰ Nhắc giờ mở bán sự kiện (Event Reminders)'),
          reminders.length === 0 ? h('p', { className: 'empty-notice' }, 'Bạn chưa đăng ký nhắc giờ mở bán sự kiện nào.') :
          h('div', { className: 'cards-list-grid' },
            reminders.map(r => h('div', { key: r.id, className: 'item-card-record' },
              h('div', { className: 'item-card-header' },
                h('strong', null, r.eventTitle),
                h('span', { className: 'status-tag tag-' + r.status.toLowerCase() }, r.status)
              ),
              h('p', { className: 'item-card-sub' },
                'Thời gian diễn ra: ', new Date(r.startsAt).toLocaleString('vi-VN'),
                ' · Nhắc trước: ', h('b', null, r.remindBeforeMinutes + ' phút')
              ),
              h('div', { className: 'item-card-footer' },
                h('span', { className: 'time-text' }, new Date(r.createdAt).toLocaleString('vi-VN')),
                r.status === 'PENDING' && h('button', {
                  type: 'button',
                  className: 'btn-cancel-item',
                  onClick: () => handleCancelReminder(r.eventId)
                }, 'Hủy nhắc')
              )
            ))
          )
        ),

        h('hr', { className: 'section-divider' }),

        // 3. Ticket Transfers
        h('div', { className: 'sub-section' },
          h('div', { className: 'transfers-sub-header' },
            h('h3', { className: 'sub-title' }, '🎁 Vé tặng bạn bè (Ticket Transfers)'),
            h('div', { className: 'transfer-toggle-buttons' },
              h('button', {
                type: 'button',
                className: 'toggle-btn' + (transferTab === 'sent' ? ' active' : ''),
                onClick: () => setTransferTab('sent')
              }, 'Vé đã gửi (' + sentTransfers.length + ')'),
              h('button', {
                type: 'button',
                className: 'toggle-btn' + (transferTab === 'received' ? ' active' : ''),
                onClick: () => setTransferTab('received')
              }, 'Vé đã nhận (' + receivedTransfers.length + ')')
            )
          ),
          (transferTab === 'sent' ? sentTransfers : receivedTransfers).length === 0 ?
            h('p', { className: 'empty-notice' }, transferTab === 'sent' ? 'Bạn chưa gửi tặng vé nào.' : 'Bạn chưa nhận vé tặng nào.') :
            h('div', { className: 'cards-list-grid' },
              (transferTab === 'sent' ? sentTransfers : receivedTransfers).map(t => h('div', { key: t.id, className: 'item-card-record' },
                h('div', { className: 'item-card-header' },
                  h('strong', null, t.eventTitle),
                  h('span', { className: 'status-tag tag-' + t.status.toLowerCase() }, t.status)
                ),
                h('p', { className: 'item-card-sub' },
                  'Mã vé: ', h('code', null, t.ticketCode),
                  ' · ' + (transferTab === 'sent' ? 'Gửi tới: ' : 'Người gửi: '), h('b', null, t.toEmail)
                ),
                t.note && h('p', { className: 'transfer-note' }, '💬 "' + t.note + '"'),
                h('div', { className: 'item-card-footer' },
                  h('span', { className: 'time-text' }, new Date(t.createdAt).toLocaleString('vi-VN')),
                  h('span', { className: 'token-code' }, 'Mã: ' + t.transferToken.substring(0, 16) + '...')
                )
              ))
            )
        )
      ),

      // ================= TAB 5: ORGANIZER PARTNER =================
      activeTab === 'organizer' && h('div', { className: 'tab-content-block' },
        h('div', { className: 'section-heading' },
          h('h2', null, 'Hồ sơ Nhà tổ chức sự kiện'),
          h('p', null, 'Nâng cấp và quản lý tư cách đối tác xuất bản sự kiện trên EventFlow')
        ),

        isOrganizer ? h('div', { className: 'partner-status-card approved' },
          h('div', { className: 'partner-status-header' },
            h('span', { className: 'partner-status-badge' }, '✓ ĐỐI TÁC CHÍNH THỨC'),
            h('h3', null, 'Chào mừng Nhà tổ chức!')
          ),
          h('p', null, 'Tài khoản của bạn đã được cấp quyền Nhà tổ chức (ORGANIZER). Bạn có toàn quyền thiết lập sơ đồ ghế, mở bán vé, quản lý nhân viên soát vé và yêu cầu rút tiền doanh thu.'),
          h('div', { className: 'partner-actions-row' },
            h('button', {
              type: 'button',
              className: 'btn-organizer-portal-enter',
              onClick: () => onNavigate('/organizer')
            }, '🎪 Vào Bàn Làm Việc Nhà Tổ Chức')
          )
        ) : (onboardingStatus && onboardingStatus.hasSubmitted && onboardingStatus.status !== 'REJECTED') ?
          h('div', { className: 'partner-status-card pending' },
            h('div', { className: 'partner-status-header' },
              h('span', { className: 'partner-status-badge pending' }, '⏳ ĐANG CHỜ PHÊ DUYỆT'),
              h('h3', null, onboardingStatus.organizationName || 'Hồ sơ đối tác')
            ),
            h('p', null, 'Hồ sơ đăng ký Nhà tổ chức của bạn đang được Ban Quản trị EventFlow xem xét. Thời gian phản hồi tiêu chuẩn là trong vòng 24 giờ làm việc.'),
            h('div', { className: 'partner-details-grid' },
              h('div', null, h('span', null, 'Email liên hệ:'), h('strong', null, onboardingStatus.contactEmail || user.email)),
              h('div', null, h('span', null, 'Gói dịch vụ:'), h('strong', null, onboardingStatus.saasPlan || 'ENTERPRISE')),
              h('div', null, h('span', null, 'Thời điểm nộp:'), h('strong', null, onboardingStatus.submittedAt ? new Date(onboardingStatus.submittedAt).toLocaleString('vi-VN') : 'Gần đây'))
            )
          ) :
          h('div', { className: 'partner-status-card unapplied' },
            h('div', { className: 'unapplied-banner' },
              h('span', { className: 'unapplied-icon' }, '🚀'),
              h('h3', null, 'Trở thành Nhà tổ chức sự kiện trên EventFlow'),
              h('p', null, 'Tiếp cận hàng triệu khán giả, bán vé với công nghệ Zero Oversell, soát vé tốc độ cao tại cổng và nhận đối soát tài chính minh bạch.'),
              h('button', {
                type: 'button',
                className: 'btn-apply-organizer',
                onClick: () => {
                  if (onOpenOnboardingModal) {
                    onOpenOnboardingModal();
                  } else {
                    onNavigate('/lien-he');
                  }
                }
              }, '+ Đăng ký trở thành Nhà tổ chức ngay')
            )
          )
      )
    )
  );
}
