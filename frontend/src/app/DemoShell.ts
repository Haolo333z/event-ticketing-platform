import { createElement as h, useState, useEffect } from 'react';
import { App } from './App.ts';
import { AdminDashboard } from './AdminDashboard.ts';
import { AuthModal } from './AuthModal.ts';
import { MyBookingsModal } from './MyBookingsModal.ts';
import { SolutionsPage } from './SolutionsPage.ts';
import { PricingPage } from './PricingPage.ts';
import { ContactPage } from './ContactPage.ts';
import { MyBookingsPage } from './MyBookingsPage.ts';
import { PaymentReturnPage } from './PaymentReturnPage.ts';
import { OrganizerDashboard } from './OrganizerDashboard.ts';
import { StaffCheckInPage } from './StaffCheckInPage.ts';
import { TransferAcceptPage } from './TransferAcceptPage.ts';
import { AiConciergeWidget } from './AiConciergeWidget.ts';
import { OrganizerOnboardingModal } from './OrganizerOnboardingModal.ts';
import { AccountProfilePage } from './AccountProfilePage.ts';
import type { Api, Store, User } from '../api/client.ts';
import { getStoredUser, clearStoredAuth } from '../api/client.ts';
import type { AdminApi } from '../api/admin.ts';

export function DemoShell({ customer, admin, store }: { customer: Api; admin: AdminApi; store: Store }) {
  const isUserAdmin = (u: User | null): boolean => !!(u && (u.role === 'ADMIN' || u.roles?.includes('ADMIN')));

  const searchParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
  const mockCustomer = searchParams?.get('mockUser') === 'customer';
  const mockAdmin = searchParams?.get('mockUser') === 'admin';
  const initialModal = searchParams?.get('openAuth');
  const initialGate = searchParams?.get('gate') === 'true';
  const initialMyBookings = searchParams?.get('openMyBookings') === 'true';
  const initialDropdown = searchParams?.get('openDropdown') ?? null;

  const initialUser: User | null = mockCustomer ? {
    id: '830eb6e6-5d10-48dc-9f71-80f6dec72679',
    displayName: 'Nguyễn Văn A',
    email: 'nguyenvana@gmail.com',
    phone: '0901234567',
    role: 'CUSTOMER'
  } : (mockAdmin ? {
    id: '10000000-0000-4000-8000-000000000002',
    displayName: 'Quản trị viên',
    email: 'admin@eventflow.vn',
    phone: '0909999999',
    role: 'ADMIN'
  } : getStoredUser());

  const isInitialAdmin = isUserAdmin(initialUser);
  const rawPath = typeof window !== 'undefined' ? window.location.pathname : '/';
  const isDirectAdminAccess = (rawPath === '/admin' || (typeof window !== 'undefined' && window.location.hash === '#admin') || searchParams?.get('persona') === 'admin');
  const shouldBlockAdmin = isDirectAdminAccess && !isInitialAdmin;

  // Normalize initial route path
  const getInitialPath = (): string => {
    if (typeof window === 'undefined') return '/';
    const path = window.location.pathname;
    if (path === '/admin' || window.location.hash === '#admin' || searchParams?.get('persona') === 'admin') {
      if (isInitialAdmin) return '/admin';
      return '/';
    }
    if (path === '/giai-phap') return '/giai-phap';
    if (path === '/bang-gia') return '/bang-gia';
    if (path === '/ve-cua-toi') return '/ve-cua-toi';
    if (path === '/payment-return') return '/payment-return';
    if (path === '/organizer') return '/organizer';
    if (path === '/staff') return '/staff';
    if (path === '/tai-khoan') return '/tai-khoan';
    if (path.startsWith('/transfer/accept') || path === '/transfer/accept') return '/transfer/accept';
    if (path === '/lien-he') return '/lien-he';
    if (path === '/dat-ve' || path === '/events') return '/dat-ve';
    return '/';
  };

  const initialPersona = (isInitialAdmin && (searchParams?.get('persona') === 'admin' || (typeof window !== 'undefined' && window.location.hash === '#admin'))) ? 'admin' : 'customer';

  const [currentPath, setCurrentPath] = useState<string>(getInitialPath);
  const [persona, setPersona] = useState<'customer' | 'admin'>(isInitialAdmin && (initialPersona === 'admin' || getInitialPath() === '/admin') ? 'admin' : 'customer');
  const [adminVisited, setAdminVisited] = useState(isInitialAdmin && (initialPersona === 'admin' || getInitialPath() === '/admin'));

  const [user, setUser] = useState<User | null>(initialUser);
  const [authModalOpen, setAuthModalOpen] = useState(!!initialModal || initialGate || shouldBlockAdmin);
  const [authModalTab, setAuthModalTab] = useState<'login' | 'register'>(initialModal === 'register' ? 'register' : 'login');
  const [gateNotice, setGateNotice] = useState(
    shouldBlockAdmin
      ? 'Yêu cầu quyền Quản trị viên để truy cập trang này. Vui lòng đăng nhập với tài khoản ADMIN.'
      : (initialGate ? 'Vui lòng đăng nhập để tiếp tục giữ vé. Lựa chọn của bạn đã được lưu tự động.' : '')
  );
  const [myBookingsOpen, setMyBookingsOpen] = useState(initialMyBookings);
  const [dropdownOpen, setDropdownOpen] = useState<string | null>(initialDropdown);
  const [pendingBookingAction, setPendingBookingAction] = useState<(() => void) | null>(null);
  const [activeBookingId, setActiveBookingId] = useState<string | null>(null);
  const [onboardingModalOpen, setOnboardingModalOpen] = useState(false);
  const [avatarDropdownOpen, setAvatarDropdownOpen] = useState(false);
  const [headerSearchQuery, setHeaderSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('ALL');

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      const target = e.target as HTMLElement | null;
      if (target && !target.closest('.user-profile-widget')) {
        setAvatarDropdownOpen(false);
      }
      if (target && !target.closest('.nav-dropdown-wrapper')) {
        setDropdownOpen(null);
      }
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('click', handleClickOutside);
      return () => window.removeEventListener('click', handleClickOutside);
    }
  }, []);

  function handleCreateEventClick() {
    if (!user) {
      setAuthModalTab('login');
      setGateNotice('Vui lòng đăng nhập để tạo và quản lý sự kiện.');
      setAuthModalOpen(true);
      return;
    }
    const userRoles = (user.roles && user.roles.length > 0) ? user.roles : [user.role];
    if (userRoles.includes('ORGANIZER') || userRoles.includes('ADMIN') || user.role === 'ORGANIZER' || user.role === 'ADMIN') {
      navigate('/organizer');
    } else {
      setOnboardingModalOpen(true);
    }
  }

  useEffect(() => {
    if (!mockCustomer && !mockAdmin) {
      const u = getStoredUser();
      if (u) setUser(u);
    }
  }, [mockCustomer, mockAdmin]);

  useEffect(() => {
    function onAuthCleared() {
      if (!mockCustomer && !mockAdmin) {
        setUser(null);
      }
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('eventflow:auth-cleared', onAuthCleared);
      return () => window.removeEventListener('eventflow:auth-cleared', onAuthCleared);
    }
  }, [mockCustomer, mockAdmin]);

  useEffect(() => {
    if (shouldBlockAdmin && typeof window !== 'undefined') {
      window.history.replaceState({}, '', '/');
    }
  }, [shouldBlockAdmin]);

  // Client-Side Router Navigation Function
  function navigate(path: string) {
    if (path === '/admin') {
      if (!isUserAdmin(user)) {
        setAuthModalTab('login');
        setGateNotice('Yêu cầu quyền Quản trị viên để truy cập trang này. Vui lòng đăng nhập với tài khoản ADMIN.');
        setAuthModalOpen(true);
        if (typeof window !== 'undefined') {
          window.history.replaceState({}, '', '/');
        }
        setCurrentPath('/');
        setPersona('customer');
        setDropdownOpen(null);
        return;
      }
      setPersona('admin');
      setAdminVisited(true);
    } else {
      setPersona('customer');
    }

    if (typeof window !== 'undefined') {
      window.history.pushState({}, '', path);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    setCurrentPath(path);
    setDropdownOpen(null);
  }

  // Handle Browser Back / Forward Buttons (popstate event)
  useEffect(() => {
    function onPopState() {
      const p = window.location.pathname;
      if (p === '/admin') {
        const u = getStoredUser();
        if (!isUserAdmin(u)) {
          if (typeof window !== 'undefined') {
            window.history.replaceState({}, '', '/');
          }
          setCurrentPath('/');
          setPersona('customer');
          setAuthModalTab('login');
          setGateNotice('Yêu cầu quyền Quản trị viên để truy cập trang này. Vui lòng đăng nhập với tài khoản ADMIN.');
          setAuthModalOpen(true);
          return;
        }
        setPersona('admin');
        setAdminVisited(true);
      } else {
        setPersona('customer');
      }
      setCurrentPath(p);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  async function handleLogout() {
    try {
      if (customer.auth?.logout) {
        await customer.auth.logout();
      }
    } catch {
      // ignore logout network errors and clear local state
    }
    clearStoredAuth();
    setUser(null);
    navigate('/');
    setMyBookingsOpen(false);
  }

  function handleAuthSuccess(loggedInUser: User) {
    setUser(loggedInUser);
    const userRoles = loggedInUser.roles || [loggedInUser.role];
    if (userRoles.includes('ADMIN')) {
      setAdminVisited(true);
    }
    if (pendingBookingAction) {
      const action = pendingBookingAction;
      setPendingBookingAction(null);
      setTimeout(() => action(), 100);
    }
  }

  return h('div', { className: 'eventflow-app-shell' },
    h('header', { className: 'demo-topbar ticketbox-header' },
      h('div', { className: 'brand-container' },
        h('a', {
          href: '/',
          className: 'brand brand-ticketbox',
          onClick: (e) => { e.preventDefault(); navigate('/'); }
        },
          'EventFlow',
          h('span', { className: 'brand-dot' }, '•')
        )
      ),

      // Ticketbox Rounded Search Bar (Center)
      h('div', { className: 'ticketbox-search-container' },
        h('span', { className: 'search-icon' }, '🔍'),
        h('input', {
          type: 'search',
          className: 'ticketbox-search-input',
          placeholder: 'Bạn tìm gì hôm nay? Tên concert, nghệ sĩ, địa điểm...',
          value: headerSearchQuery,
          onChange: (e: any) => {
            const val = e.target.value;
            setHeaderSearchQuery(val);
            if (typeof window !== 'undefined') {
              window.dispatchEvent(new CustomEvent('eventflow:search-query', { detail: val }));
            }
          },
          onKeyDown: (e: any) => {
            if (e.key === 'Enter') {
              if (currentPath !== '/' && currentPath !== '/dat-ve' && currentPath !== '/events') {
                navigate('/');
              }
            }
          }
        })
      ),

      // Ticketbox Actions Right Group
      h('div', { className: 'topbar-actions ticketbox-actions' },
        // Dropdown: Dành cho Ban tổ chức ▾
        h('div', { className: 'nav-dropdown-wrapper' + (dropdownOpen === 'organizer-menu' ? ' open' : '') },
          h('button', {
            type: 'button',
            className: 'nav-link dropdown-toggle organizer-menu-btn',
            onClick: () => setDropdownOpen(curr => curr === 'organizer-menu' ? null : 'organizer-menu')
          },
            'Dành cho Ban tổ chức ',
            h('span', { className: 'nav-caret' }, '▾')
          ),
          h('div', { className: 'mega-menu-panel ticketbox-organizer-panel' },
            h('a', {
              href: '/giai-phap',
              className: 'dropdown-menu-item',
              onClick: (e) => { e.preventDefault(); setDropdownOpen(null); navigate('/giai-phap'); }
            },
              h('span', { className: 'item-icon' }, '⚡'),
              h('span', { className: 'item-text' }, 'Giải pháp công nghệ')
            ),
            h('a', {
              href: '/bang-gia',
              className: 'dropdown-menu-item',
              onClick: (e) => { e.preventDefault(); setDropdownOpen(null); navigate('/bang-gia'); }
            },
              h('span', { className: 'item-icon' }, '💎'),
              h('span', { className: 'item-text' }, 'Bảng giá dịch vụ')
            ),
            h('a', {
              href: '/lien-he',
              className: 'dropdown-menu-item',
              onClick: (e) => { e.preventDefault(); setDropdownOpen(null); navigate('/lien-he'); }
            },
              h('span', { className: 'item-icon' }, '📞'),
              h('span', { className: 'item-text' }, 'Liên hệ hợp tác')
            )
          )
        ),

        // Direct: Vé của tôi
        h('button', {
          type: 'button',
          className: 'nav-btn-my-tickets' + (currentPath === '/ve-cua-toi' ? ' active' : ''),
          onClick: () => navigate('/ve-cua-toi'),
          title: 'Vé của tôi & Lịch sử đơn'
        }, '🎟️ Vé của tôi'),

        // Nổi bật: + Tạo sự kiện
        h('button', {
          type: 'button',
          className: 'topbar-cta-btn btn-create-event',
          onClick: handleCreateEventClick,
          title: 'Tạo và xuất bản sự kiện mới'
        }, '+ Tạo sự kiện'),

        // Real Auth Section (4-Role RBAC Support & High-Contrast Avatar Dropdown)
        user ? (() => {
          const userRoles = (user.roles && user.roles.length > 0) ? user.roles : [user.role];
          const hasAdmin = userRoles.includes('ADMIN');
          const hasStaff = userRoles.includes('STAFF');
          const hasOrganizer = userRoles.includes('ORGANIZER');

          return h('div', { className: 'user-profile-widget' },
            h('button', {
              type: 'button',
              className: 'user-avatar-trigger' + (avatarDropdownOpen ? ' active' : ''),
              onClick: (e) => {
                e.stopPropagation();
                setAvatarDropdownOpen(!avatarDropdownOpen);
              },
              title: 'Tài khoản: ' + user.displayName
            },
              h('div', { className: 'user-avatar-circle' },
                user.displayName ? user.displayName.charAt(0).toUpperCase() : 'U'
              ),
              h('span', { className: 'user-name-label' }, user.displayName),
              h('span', { className: 'avatar-dropdown-caret' }, avatarDropdownOpen ? '▴' : '▾')
            ),
            avatarDropdownOpen && h('div', { className: 'avatar-dropdown-menu' },
              h('div', { className: 'dropdown-user-header' },
                h('div', { className: 'dropdown-user-name' }, user.displayName),
                h('div', { className: 'dropdown-user-email' }, user.email),
                h('div', { className: 'dropdown-roles-badges' },
                  userRoles.map(r => h('span', { key: r, className: 'badge-role-pill role-' + r.toLowerCase() }, r))
                )
              ),
              h('div', { className: 'dropdown-divider' }),
              h('button', {
                type: 'button',
                className: 'dropdown-menu-item' + (currentPath === '/tai-khoan' ? ' active' : ''),
                onClick: () => { setAvatarDropdownOpen(false); navigate('/tai-khoan'); }
              },
                h('span', { className: 'item-icon' }, '👤'),
                h('span', { className: 'item-text' }, 'Thông tin tài khoản')
              ),
              h('button', {
                type: 'button',
                className: 'dropdown-menu-item' + (currentPath === '/ve-cua-toi' ? ' active' : ''),
                onClick: () => { setAvatarDropdownOpen(false); navigate('/ve-cua-toi'); }
              },
                h('span', { className: 'item-icon' }, '🎟️'),
                h('span', { className: 'item-text' }, 'Vé của tôi & Lịch sử đơn')
              ),
              (hasOrganizer || hasAdmin) && h('button', {
                type: 'button',
                className: 'dropdown-menu-item' + (currentPath === '/organizer' ? ' active' : ''),
                onClick: () => { setAvatarDropdownOpen(false); navigate('/organizer'); }
              },
                h('span', { className: 'item-icon' }, '🎪'),
                h('span', { className: 'item-text' }, 'Quản trị Sự kiện')
              ),
              (hasStaff || hasAdmin) && h('button', {
                type: 'button',
                className: 'dropdown-menu-item' + (currentPath === '/staff' ? ' active' : ''),
                onClick: () => { setAvatarDropdownOpen(false); navigate('/staff'); }
              },
                h('span', { className: 'item-icon' }, '📱'),
                h('span', { className: 'item-text' }, 'Cổng Soát vé')
              ),
              hasAdmin && h('button', {
                type: 'button',
                className: 'dropdown-menu-item' + (currentPath === '/admin' ? ' active' : ''),
                onClick: () => { setAvatarDropdownOpen(false); navigate('/admin'); }
              },
                h('span', { className: 'item-icon' }, '🛡️'),
                h('span', { className: 'item-text' }, 'Quản trị Sàn')
              ),
              h('div', { className: 'dropdown-divider' }),
              h('button', {
                type: 'button',
                className: 'dropdown-menu-item item-logout',
                onClick: () => { setAvatarDropdownOpen(false); handleLogout(); }
              },
                h('span', { className: 'item-icon' }, '🚪'),
                h('span', { className: 'item-text' }, 'Đăng xuất')
              )
            )
          );
        })() : h('div', { className: 'auth-buttons-group' },
          h('button', {
            type: 'button',
            className: 'btn-auth-login',
            onClick: () => { setAuthModalTab('login'); setAuthModalOpen(true); }
          }, 'Đăng nhập'),
          h('button', {
            type: 'button',
            className: 'btn-auth-register',
            onClick: () => { setAuthModalTab('register'); setAuthModalOpen(true); }
          }, 'Đăng ký')
        ),

        // Hidden accessibility persona buttons for backwards test compatibility
        h('div', { style: { display: 'none' }, 'aria-hidden': 'true' },
          h('button', {
            type: 'button',
            onClick: () => {
              setPersona('customer');
              setCurrentPath('/');
              setUser({ id: '830eb6e6-5d10-48dc-9f71-80f6dec72679', displayName: 'Khách hàng demo', email: 'customer@eventflow.vn', role: 'CUSTOMER', roles: ['CUSTOMER'] });
            }
          }, 'Khách hàng demo'),
          h('button', {
            type: 'button',
            onClick: () => {
              setAdminVisited(true);
              setPersona('admin');
              setCurrentPath('/admin');
              setUser({ id: '10000000-0000-4000-8000-000000000002', displayName: 'Quản trị viên', email: 'admin@eventflow.vn', role: 'ADMIN', roles: ['ADMIN'] });
            }
          }, 'Quản trị demo')
        )
      )
    ),

    // Ticketbox Category Sub-nav (mỏng ngay dưới Header)
    (currentPath === '/' || currentPath === '/dat-ve' || currentPath === '/events') && h('nav', {
      className: 'ticketbox-category-strip',
      'aria-label': 'Danh mục sự kiện Ticketbox'
    },
      h('div', { className: 'category-strip-inner' },
        [
          { id: 'ALL', label: 'Tất cả' },
          { id: 'CONCERT', label: '🎸 Nhạc sống' },
          { id: 'THEATER', label: '🎭 Sân khấu & Nghệ thuật' },
          { id: 'SPORTS', label: '⚽ Thể thao' },
          { id: 'CONFERENCE', label: '💡 Hội thảo' },
          { id: 'ONLINE', label: '💻 Sự kiện Online' },
          { id: 'RESALE', label: '🏷️ Vé chuyển nhượng (Resale)' }
        ].map(cat => h('button', {
          key: cat.id,
          type: 'button',
          className: 'category-strip-item' + (activeCategory === cat.id ? ' active' : ''),
          onClick: () => {
            setActiveCategory(cat.id);
            if (typeof window !== 'undefined') {
              window.dispatchEvent(new CustomEvent('eventflow:category-select', { detail: cat.id }));
            }
          }
        }, cat.label))
      )
    ),

    h(AuthModal, {
      isOpen: authModalOpen,
      initialTab: authModalTab,
      gateNotice,
      onClose: () => { setAuthModalOpen(false); setGateNotice(''); },
      onSuccess: handleAuthSuccess,
      api: customer
    }),

    h(MyBookingsModal, {
      isOpen: myBookingsOpen,
      onClose: () => setMyBookingsOpen(false),
      api: customer,
      onSelectBooking: (id) => {
        setActiveBookingId(id);
        navigate('/dat-ve');
      }
    }),

    // Multi-Page Route Render Switching
    h('div', { className: 'route-page-container' },
      // Admin Dashboard panel (preserved when visited and user is verified ADMIN)
      (adminVisited && isUserAdmin(user)) ? h('div', { style: { display: (currentPath === '/admin' || persona === 'admin') ? 'block' : 'none' } },
        h(AdminDashboard, { api: admin })
      ) : null,

      // Customer App Core (preserved to retain active booking & form states)
      h('div', { style: { display: ((currentPath === '/' || currentPath === '/dat-ve' || currentPath === '/events') && persona !== 'admin') ? 'block' : 'none' } },
        h(App, {
          api: customer,
          store,
          user,
          embedded: true,
          activeBookingId,
          currentPath: (currentPath === '/dat-ve' || currentPath === '/events') ? '/dat-ve' : '/',
          onNavigate: navigate,
          onRequireAuth: (resumeAction) => {
            setPendingBookingAction(() => resumeAction);
            setGateNotice('Vui lòng đăng nhập để tiếp tục giữ vé. Lựa chọn của bạn đã được lưu tự động.');
            setAuthModalTab('login');
            setAuthModalOpen(true);
          }
        })
      ),

      // Other Multi-Page Routes
      currentPath === '/giai-phap' && persona !== 'admin' ? h(SolutionsPage, { onNavigate: navigate }) : null,
      currentPath === '/bang-gia' && persona !== 'admin' ? h(PricingPage, { onNavigate: navigate }) : null,
      currentPath.startsWith('/lien-he') && persona !== 'admin' ? h(ContactPage, { api: customer, onNavigate: navigate, currentPath }) : null,
      currentPath === '/organizer' && persona !== 'admin' ? h(OrganizerDashboard, {
        api: customer,
        user,
        onNavigate: navigate,
        onOpenAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); }
      }) : null,
      currentPath === '/staff' && persona !== 'admin' ? h(StaffCheckInPage, {
        api: customer,
        user,
        onNavigate: navigate,
        onOpenAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); }
      }) : null,
      currentPath.startsWith('/transfer/accept') && persona !== 'admin' ? h(TransferAcceptPage, {
        api: customer,
        user,
        onNavigate: navigate,
        onOpenAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); }
      }) : null,
      currentPath === '/payment-return' && persona !== 'admin' ? h(PaymentReturnPage, {
        api: customer,
        onNavigate: navigate
      }) : null,
      currentPath === '/ve-cua-toi' && persona !== 'admin' ? h(MyBookingsPage, {
        api: customer,
        user,
        onOpenAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); },
        onNavigate: navigate,
        onSelectBooking: (id) => {
          setActiveBookingId(id);
          navigate('/dat-ve');
        }
      }) : null,
      currentPath === '/tai-khoan' && persona !== 'admin' ? h(AccountProfilePage, {
        api: customer,
        user,
        onNavigate: navigate,
        onOpenOnboardingModal: () => setOnboardingModalOpen(true),
        onUserUpdated: (updated) => setUser(updated),
        onOpenAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); }
      }) : null
    ),

    // Floating Quick Contact Dock (Sao Kim style)
    h('aside', { className: 'floating-contact-dock', 'aria-label': 'Liên hệ nhanh' },
      h('a', {
        href: 'tel:19006868',
        className: 'dock-item hotline-item',
        title: 'Hotline: 1900 6868',
        'aria-label': 'Gọi Hotline 1900 6868'
      },
        h('span', { className: 'dock-icon' }, '📞'),
        h('span', { className: 'dock-tooltip' }, 'Hotline: 1900 6868')
      ),
      h('button', {
        type: 'button',
        className: 'dock-item email-item',
        onClick: () => navigate('/lien-he'),
        title: 'Email: contact@eventflow.vn',
        'aria-label': 'Gửi email tư vấn'
      },
        h('span', { className: 'dock-icon' }, '✉️'),
        h('span', { className: 'dock-tooltip' }, 'contact@eventflow.vn')
      ),
      h('button', {
        type: 'button',
        className: 'dock-item zalo-item',
        onClick: () => navigate('/lien-he'),
        title: 'Tư vấn trực tuyến 24/7',
        'aria-label': 'Tư vấn trực tuyến 24/7'
      },
        h('span', { className: 'dock-icon' }, '💬'),
        h('span', { className: 'dock-tooltip' }, 'Tư vấn trực tuyến 24/7')
      )
    ),

    // EventFlow V3: Integrated AI Ticketing Concierge Widget
    h(AiConciergeWidget, {
      api: customer,
      user,
      onRequireAuth: () => { setAuthModalTab('login'); setAuthModalOpen(true); },
      onBooked: (id) => {
        setActiveBookingId(id);
        navigate('/dat-ve');
      }
    }),

    // Organizer Onboarding Modal
    h(OrganizerOnboardingModal, {
      isOpen: onboardingModalOpen,
      onClose: () => setOnboardingModalOpen(false),
      user,
      api: customer,
      onNavigateToOrganizer: () => navigate('/organizer')
    })
  );
}
