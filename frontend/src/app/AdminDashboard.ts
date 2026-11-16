import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent} from 'react';
import {ApiError} from '../api/client.ts';
import type {Booking,Status} from '../api/client.ts';
import type {AdminApi,AdminEvent,AdminInventory,CreateEvent,PatchEvent,Page,Outbox,AdminUser,ContactLeadItem,AdminRefundItem,PlatformSummary,EventReport} from '../api/admin.ts';
import {EventEditor} from './EventEditor.ts';
import {RecoveryView} from './RecoveryView.ts';

type Tab='events'|'users'|'leads'|'inventory'|'bookings'|'reports'|'outbox'|'recovery';
const states:Status[]=['PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED'];
const leadStatuses=['NEW','CONTACTED','CONVERTED','CLOSED'] as const;
const userRoles=['CUSTOMER','STAFF','ORGANIZER','ADMIN'] as const;

const message=(value:unknown)=>value instanceof ApiError?value.message+' · '+value.code+(value.correlationId?' · Mã tra cứu: '+value.correlationId:''):'Không tải được dữ liệu. Hãy thử lại.';

export function AdminDashboard({api}:{api:AdminApi}){
  const searchParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
  const initialTab = (searchParams?.get('adminTab') as Tab) || 'events';
  const initialEditor = searchParams?.get('openEditor') === 'create' ? 'create' : null;
  const[tab,setTab]=useState<Tab>(initialTab),[page,setPage]=useState(0),[filter,setFilter]=useState<Status|''>(''),[revision,setRevision]=useState(0);
  const[searchUser,setSearchUser]=useState(''),[userRoleFilter,setUserRoleFilter]=useState('');
  const[leadStatusFilter,setLeadStatusFilter]=useState('');
  const[data,setData]=useState<Page<AdminEvent>|Page<Booking>|Page<Outbox>|Page<AdminUser>|Page<ContactLeadItem>|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState('');
  const[selected,setSelected]=useState<AdminEvent|null>(null),[inventory,setInventory]=useState<AdminInventory|null>(null),[invError,setInvError]=useState('');
  const[inventoryRevision,setInventoryRevision]=useState(0);
  const[editor,setEditor]=useState<'create'|'edit'|null>(initialEditor),[detail,setDetail]=useState<Booking|null>(null),[notice,setNotice]=useState('');
  const[uncertain,setUncertain]=useState(false),[saving,setSaving]=useState(false);const savingRef=useRef(false),detailRequest=useRef(0);
  const editorPanel=useRef<HTMLElement>(null);
  const[recoveryEvent,setRecoveryEvent]=useState<string|undefined>();

  // Refunds & Reports state
  const[refunds,setRefunds]=useState<AdminRefundItem[]>([]);
  const[summary,setSummary]=useState<PlatformSummary|null>(null);
  const[eventReports,setEventReports]=useState<EventReport[]>([]);

  function revealEditor(){
    const panel=editorPanel.current;if(!panel)return;
    panel.scrollIntoView?.({behavior:'smooth',block:'start'});
    const firstField=panel.querySelector<HTMLInputElement>('#admin-title');
    (firstField??panel).focus({preventScroll:true});
  }
  useEffect(()=>{if(editor)revealEditor();},[editor,editor==='edit'?inventory:null,inventoryRevision]);

  useEffect(()=>{
    if(tab==='inventory'||tab==='recovery'){setLoading(false);return;}
    let active=true;setLoading(true);setData(null);setError('');
    let request:Promise<any>;
    if(tab==='events')request=api.events(page,20);
    else if(tab==='users')request=api.users?api.users(page,20,searchUser,userRoleFilter||undefined):Promise.resolve({items:[],page:0,size:20,totalElements:0});
    else if(tab==='leads')request=api.leads?api.leads(page,20,leadStatusFilter||undefined):Promise.resolve({items:[],page:0,size:20,totalElements:0});
    else if(tab==='bookings'){
      request=Promise.all([
        api.bookings(page,20,filter||undefined),
        api.refunds?api.refunds().catch(()=>[]):Promise.resolve([])
      ]).then(([bkgPage,refundItems])=>{
        if(active)setRefunds(refundItems||[]);
        return bkgPage;
      });
    }
    else if(tab==='reports'){
      request=Promise.all([
        api.reports?.summary?api.reports.summary():Promise.resolve(null),
        api.reports?.events?api.reports.events():Promise.resolve([])
      ]).then(([sumData,evReports])=>{
        if(active){
          setSummary(sumData);
          setEventReports(evReports||[]);
        }
        return {items:evReports||[],page:0,size:(evReports||[]).length,totalElements:(evReports||[]).length};
      });
    }
    else request=api.outbox(page,20);

    request.then(result=>{if(active)setData(result);}).catch(value=>{if(active)setError(message(value));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[api,tab,page,filter,revision,searchUser,userRoleFilter,leadStatusFilter]);

  useEffect(()=>{
    setInventory(null);setInvError('');if(!selected)return;let active=true;
    api.inventory(selected.id).then(result=>{if(active)setInventory(result);}).catch(value=>{if(active)setInvError(message(value));});return()=>{active=false;};
  },[api,selected?.id,revision,inventoryRevision]);

  function switchTab(value:Tab){if(value===tab)return;setData(null);setLoading(value!=='inventory');setTab(value);setPage(0);setError('');setDetail(null);detailRequest.current++;}

  async function save(body:CreateEvent|PatchEvent){
    if(savingRef.current||uncertain)return;savingRef.current=true;setSaving(true);setNotice('');
    try{
      const result=editor==='create'?await api.create(body as CreateEvent):await api.patch(selected!.id,body as PatchEvent);
      setData(null);setLoading(true);setSelected(result);setNotice(editor==='create'?'Đã tạo sự kiện theo xác nhận máy chủ.':'Đã lưu thay đổi. Giá mới chỉ áp dụng cho booking mới.');setEditor(null);setPage(0);setTab('events');setRevision(x=>x+1);
    }catch(value){
      if(editor==='create'&&(!(value instanceof ApiError)||value.status===0||value.status>=500))setUncertain(true);
      throw value;
    }finally{savingRef.current=false;setSaving(false);}
  }

  async function reviewCreate(){
    setTab('events');setData(null);setLoading(true);setError('');
    try{setData(await api.events(0,20));setPage(0);setUncertain(false);setNotice('Đã tải lại danh sách. Kiểm tra sự kiện vừa tạo trước khi gửi một lệnh tạo mới.');}
    catch(value){setError(message(value));}finally{setLoading(false);}
  }

  async function openBooking(id:string){const version=++detailRequest.current;setDetail(null);setError('');try{const result=await api.booking(id);if(version===detailRequest.current)setDetail(result);}catch(value){if(version===detailRequest.current)setError(message(value));}}
  function choose(event:AdminEvent,edit:boolean){setInventory(null);setInvError('');setInventoryRevision(x=>x+1);setSelected(event);setEditor(edit?'edit':null);if(!edit)switchTab('inventory');}

  async function handleRoleAction(userId:string,role:string,action:'GRANT'|'REVOKE'){
    if(!api.updateUserRole)return;setError('');setNotice('');
    try{
      await api.updateUserRole(userId,role,action);
      setNotice(action==='GRANT'?`Đã cấp quyền ${role} thành công.`:`Đã thu hồi quyền ${role} thành công.`);
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  async function handleStatusAction(userId:string,status:'ACTIVE'|'LOCKED'){
    if(!api.updateUserStatus)return;setError('');setNotice('');
    try{
      await api.updateUserStatus(userId,status);
      setNotice(status==='LOCKED'?'Đã khóa tài khoản người dùng thành công.':'Đã kích hoạt lại tài khoản thành công.');
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  async function handleProcessRefund(refundId:string,decision:'APPROVE'|'REJECT'){
    if(!api.processRefund)return;setError('');setNotice('');
    try{
      const res=await api.processRefund(refundId,decision);
      setNotice(res.message||(decision==='APPROVE'?'Đã duyệt hoàn tiền thành công.':'Đã từ chối hoàn tiền.'));
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  async function handleLeadStatusUpdate(leadId:string,status:'NEW'|'CONTACTED'|'CONVERTED'|'CLOSED'){
    if(!api.updateLeadStatus)return;setError('');setNotice('');
    try{
      await api.updateLeadStatus(leadId,status);
      setNotice(`Đã cập nhật trạng thái lead sang ${status}.`);
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  const table=(caption:string,columns:string[],rows:ReturnType<typeof h>[])=>h('div',{className:'table-scroll'},h('table',null,h('caption',null,caption),h('thead',null,h('tr',null,...columns.map(c=>h('th',{key:c,scope:'col'},c)))),h('tbody',null,...rows)));
  const empty=!loading&&!error&&data?.items.length===0;

  return h('main',{className:'admin-dashboard shell'},
    h('section',{className:'intro'},h('p',{className:'eyebrow'},'QUẢN TRỊ NỀN TẢNG EVENTFLOW'),h('h1',null,'Quản trị hệ thống & Vận hành sàn'),h('p',null,'Dữ liệu thực tế từ PostgreSQL 17 & API bảo mật. Kiểm toán Audit Log toàn diện cho mọi thao tác đặc quyền.')),
    h('nav',{'aria-label':'Mục quản trị',className:'admin-tabs'},...([
      ['events','Sự kiện'],
      ['users','Người dùng'],
      ['bookings','Booking'],
      ['reports','Báo cáo'],
      ['leads','Yêu cầu Doanh nghiệp'],
      ['inventory','Tồn kho'],
      ['outbox','Outbox'],
      ['recovery','Khôi phục']
    ] as const).map(([key,label])=>h('button',{key,type:'button','aria-pressed':tab===key,disabled:saving,onClick:()=>switchTab(key)},label))),
    error&&h('p',{role:'alert',className:'alert error'},error),notice&&h('p',{role:'status',className:'alert success'},notice),
    uncertain&&h('div',{role:'alert',className:'alert pending'},h('p',null,'Chưa rõ kết quả tạo sự kiện. Không gửi lại tự động. Tải và kiểm tra danh sách trước khi tạo tiếp.'),h('button',{type:'button',disabled:loading,onClick:()=>void reviewCreate()},'Tải danh sách để kiểm tra')),
    h('div',{className:'section-heading'},h('h2',null,{
      events:'Danh sách sự kiện',
      users:'Quản lý người dùng & Phân quyền',
      leads:'Khách hàng doanh nghiệp tư vấn',
      inventory:'Tồn kho theo sự kiện',
      bookings:'Danh sách booking & Duyệt hoàn tiền',
      reports:'Báo cáo doanh thu & Tỷ lệ lấp đầy',
      outbox:'Sự kiện outbox',
      recovery:'Khôi phục sự kiện'
    }[tab]),h('button',{type:'button',className:'quiet',disabled:loading||saving,onClick:()=>setRevision(x=>x+1)},'Làm mới dữ liệu')),
    tab==='recovery'&&h(RecoveryView,{api,initialEventId:recoveryEvent,revision}),

    tab==='bookings'&&h('div',{className:'filter-field'},
      h('label',{htmlFor:'booking-status-filter'},'Lọc trạng thái booking'),
      h('select',{id:'booking-status-filter',value:filter,onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setFilter(e.target.value as Status|'');setPage(0);setDetail(null);detailRequest.current++;}},
        h('option',{value:''},'Tất cả trạng thái'),...states.map(s=>h('option',{key:s,value:s},s))
      )
    ),

    tab==='users'&&h('div',{className:'filter-field user-filter-grid'},
      h('div',null,
        h('label',{htmlFor:'user-search'},'Tìm kiếm người dùng (Tên, Email, Số điện thoại)'),
        h('input',{
          id:'user-search',
          type:'search',
          placeholder:'🔍 Nhập từ khóa tìm kiếm người dùng...',
          value:searchUser,
          onChange:(e:ChangeEvent<HTMLInputElement>)=>{setSearchUser(e.target.value);setPage(0);}
        })
      ),
      h('div',null,
        h('label',{htmlFor:'user-role-filter'},'Lọc theo vai trò'),
        h('select',{
          id:'user-role-filter',
          value:userRoleFilter,
          onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setUserRoleFilter(e.target.value);setPage(0);}
        },
          h('option',{value:''},'Tất cả vai trò'),
          ...userRoles.map(r=>h('option',{key:r,value:r},r))
        )
      )
    ),

    tab==='leads'&&h('div',{className:'filter-field'},
      h('label',{htmlFor:'lead-status-filter'},'Lọc trạng thái lead tư vấn'),
      h('select',{
        id:'lead-status-filter',
        value:leadStatusFilter,
        onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setLeadStatusFilter(e.target.value);setPage(0);}
      },
        h('option',{value:''},'Tất cả trạng thái'),
        ...leadStatuses.map(s=>h('option',{key:s,value:s},s))
      )
    ),

    tab==='events'&&h('button',{type:'button',className:'primary',disabled:saving||uncertain,'aria-expanded':editor==='create','aria-controls':'event-editor-panel',onClick:()=>{setEditor('create');setError('');if(editor==='create')revealEditor();}},'Tạo sự kiện mới'),
    editor&&h('section',{id:'event-editor-panel',ref:editorPanel,className:'editor-panel',tabIndex:-1,'aria-labelledby':'event-editor-heading'},
      h('div',{className:'section-heading'},h('h2',{id:'event-editor-heading'},editor==='create'?'Tạo sự kiện':'Sửa sự kiện'),h('button',{type:'button',disabled:saving,onClick:()=>setEditor(null)},'Đóng biểu mẫu')),
      editor==='edit'&&invError?h('p',{role:'alert'},invError):editor==='edit'&&!inventory?h('p',{role:'status'},'Đang tải cấu hình tồn kho…'):h(EventEditor,{key:editor==='create'?'new':selected!.id+':'+revision,event:editor==='create'?null:selected,inventory,blocked:uncertain,onSave:save,onCancel:()=>setEditor(null)})),

    loading&&h('p',{role:'status'},'Đang tải dữ liệu quản trị…'),empty&&h('p',{className:'empty-state'},'Không có dữ liệu cho trang hoặc bộ lọc này.'),

    !loading&&!error&&data&&tab==='events'&&table('Sự kiện gồm bản nháp, đang công bố và đã đóng',['Sự kiện','Trạng thái','Loại vé','Thao tác'],(data.items as AdminEvent[]).map(event=>h('tr',{key:event.id},h('td',null,h('strong',null,event.title),h('small',{className:'muted'},event.venue)),h('td',null,event.status),h('td',null,event.ticketTypes.map(t=>t.name+' · '+t.mode).join(', ')),h('td',{className:'actions'},h('button',{type:'button',onClick:()=>choose(event,false)},'Tồn kho '+event.title),h('button',{type:'button',disabled:uncertain,onClick:()=>choose(event,true)},'Sửa '+event.title))))),

    !loading&&!error&&data&&tab==='users'&&table('Danh sách người dùng đăng ký trong hệ thống & Phân quyền',['Họ và tên','Email / SĐT','Trạng thái','Vai trò','Phân quyền & Thao tác'],(data.items as AdminUser[]).map(u=>{
      const rolesList = (u.roles && u.roles.length>0) ? u.roles : (u.role ? [u.role] : ['CUSTOMER']);
      const isLocked = u.status === 'LOCKED';
      return h('tr',{key:u.id},
        h('td',null,h('strong',null,u.displayName),h('br',null),h('small',{className:'muted'},u.id)),
        h('td',null,h('div',null,u.email||h('em',{className:'muted'},'Chưa có email')),h('small',{className:'muted'},u.phone||'')),
        h('td',null,h('span',{className:'status-badge '+(isLocked?'badge-locked':'badge-active')},isLocked?'🔒 ĐÃ KHÓA':'🟢 HOẠT ĐỘNG')),
        h('td',null,h('div',{className:'roles-tags-container'},...rolesList.map(r=>h('span',{key:r,className:'user-role-badge role-'+r.toLowerCase()},r)))),
        h('td',{className:'actions-cell'},
          h('div',{className:'action-buttons-group'},
            // Quick role toggles
            !rolesList.includes('STAFF')&&h('button',{type:'button',className:'btn-role-action',onClick:()=>void handleRoleAction(u.id,'STAFF','GRANT')},'+ STAFF'),
            rolesList.includes('STAFF')&&h('button',{type:'button',className:'btn-role-action revoke',onClick:()=>void handleRoleAction(u.id,'STAFF','REVOKE')},'- STAFF'),
            !rolesList.includes('ORGANIZER')&&h('button',{type:'button',className:'btn-role-action',onClick:()=>void handleRoleAction(u.id,'ORGANIZER','GRANT')},'+ ORGANIZER'),
            rolesList.includes('ORGANIZER')&&h('button',{type:'button',className:'btn-role-action revoke',onClick:()=>void handleRoleAction(u.id,'ORGANIZER','REVOKE')},'- ORGANIZER'),
            !rolesList.includes('ADMIN')&&h('button',{type:'button',className:'btn-role-action',onClick:()=>void handleRoleAction(u.id,'ADMIN','GRANT')},'+ ADMIN'),
            rolesList.includes('ADMIN')&&h('button',{type:'button',className:'btn-role-action revoke',onClick:()=>void handleRoleAction(u.id,'ADMIN','REVOKE')},'- ADMIN'),
            // Lock / Unlock toggle
            isLocked?h('button',{type:'button',className:'btn-status-toggle unlock',onClick:()=>void handleStatusAction(u.id,'ACTIVE')},'Mở khóa'):h('button',{type:'button',className:'btn-status-toggle lock',onClick:()=>void handleStatusAction(u.id,'LOCKED')},'Khóa')
          )
        )
      );
    })),

    !loading&&!error&&data&&tab==='leads'&&table('Danh sách khách hàng doanh nghiệp yêu cầu tư vấn',['Họ và tên','Email / SĐT','Doanh nghiệp','Nội dung yêu cầu','Trạng thái','Thời gian','Thao tác'],(data.items as ContactLeadItem[]).map(l=>h('tr',{key:l.id},
      h('td',null,h('strong',null,l.fullName)),
      h('td',null,h('div',null,l.email),h('small',{className:'muted'},l.phone)),
      h('td',null,h('div',null,l.companyName||h('em',{className:'muted'},'Cá nhân')),h('small',{className:'muted'},l.eventScale||'')),
      h('td',{className:'lead-message-cell'},l.message||'—',l.notes?h('p',{className:'lead-notes'},'Ghi chú: '+l.notes):null),
      h('td',null,h('span',{className:'lead-status-badge lead-'+(l.status||'NEW').toLowerCase()},(l.status||'NEW'))),
      h('td',null,l.createdAt?new Date(l.createdAt).toLocaleString('vi-VN'):'—'),
      h('td',null,
        h('select',{
          className:'select-lead-status',
          value:l.status||'NEW',
          onChange:(e:ChangeEvent<HTMLSelectElement>)=>void handleLeadStatusUpdate(l.id,e.target.value as any)
        },
          ...leadStatuses.map(s=>h('option',{key:s,value:s},s))
        )
      )
    ))),

    tab==='inventory'&&(!selected?h('p',{className:'empty-state'},'Chọn sự kiện ở mục Sự kiện để xem tồn kho.'):h('section',{'aria-label':'Chi tiết tồn kho'},h('h3',null,selected.title),invError?h('p',{role:'alert',className:'alert error'},invError):!inventory?h('p',{role:'status'},'Đang tải tồn kho…'):h('div',null,h('p',{className:'muted'},'Snapshot lúc '+new Date(inventory.asOf).toLocaleString('vi-VN')+'. Làm mới để xem thay đổi; đây không phải thao tác giữ vé.'),
      ...inventory.ticketTypes.map(stock=>h('section',{key:stock.ticketTypeId,className:'inventory-type'},h('h3',null,selected.ticketTypes.find(t=>t.id===stock.ticketTypeId)?.name??stock.ticketTypeId),stock.mode==='GA'?table('Tồn kho GA',['Sức chứa','Đang giữ','Đã bán','Còn trống'],[h('tr',{key:stock.ticketTypeId},h('td',null,stock.capacity),h('td',null,stock.held),h('td',null,stock.sold),h('td',null,stock.available))]):h('div',null,h('p',null,'Ghế chỉ định · '+stock.seats.length+' ghế'),h('ul',{className:'admin-seat-list'},...stock.seats.map(seat=>h('li',{key:seat.id,className:seat.status.toLowerCase()},h('strong',null,seat.label),' · '+seat.status))))))))),

    !loading&&!error&&data&&tab==='bookings'&&h('section',{className:'bookings-section'},
      table('Booking theo trạng thái đã chọn',['Mã booking','Trạng thái','Tổng tiền','Hạn giữ','Chi tiết'],(data.items as Booking[]).map(b=>h('tr',{key:b.bookingId},h('td',{className:'booking-id'},b.bookingId),h('td',null,b.status),h('td',null,b.totalAmountVnd.toLocaleString('vi-VN')+' ₫'),h('td',null,new Date(b.expiresAt).toLocaleString('vi-VN')),h('td',null,h('button',{type:'button',onClick:()=>void openBooking(b.bookingId)},'Xem '+b.bookingId))))),
      // Refund section
      h('div',{className:'refund-management-section'},
        h('h3',null,'Quản lý yêu cầu Hoàn tiền (Refund Requests)'),
        refunds.length===0?h('p',{className:'empty-state'},'Hiện không có yêu cầu hoàn tiền nào cần xử lý.'):
        table('Danh sách yêu cầu hoàn tiền từ cổng thanh toán',['Mã hoàn tiền','Sự kiện / Khách hàng','Số tiền','Lý do','Trạng thái','Thao tác'],refunds.map(r=>{
          const isPending = r.status==='REQUESTED'||r.status==='PROCESSING';
          return h('tr',{key:r.id},
            h('td',{className:'booking-id'},r.id,h('br',null),h('small',{className:'muted'},'Booking: '+r.bookingId)),
            h('td',null,h('strong',null,r.eventTitle||'Sự kiện'),h('br',null),h('small',{className:'muted'},r.customerName+' ('+r.customerEmail+')')),
            h('td',null,h('strong',null,r.amountVnd.toLocaleString('vi-VN')+' ₫')),
            h('td',null,r.reason||'Khách yêu cầu hủy/hoàn vé'),
            h('td',null,h('span',{className:'refund-badge refund-'+r.status.toLowerCase()},r.status)),
            h('td',{className:'actions'},
              isPending?h('div',{className:'refund-actions-group'},
                h('button',{type:'button',className:'btn-refund-approve',onClick:()=>void handleProcessRefund(r.id,'APPROVE')},'Duyệt hoàn tiền'),
                h('button',{type:'button',className:'btn-refund-reject',onClick:()=>void handleProcessRefund(r.id,'REJECT')},'Từ chối')
              ):h('span',{className:'muted'},r.settledAt?'Đã xử lý '+new Date(r.settledAt).toLocaleDateString('vi-VN'):'Đã kết thúc')
            )
          );
        }))
      )
    ),

    detail&&tab==='bookings'&&h('section',{className:'result','aria-label':'Chi tiết booking quản trị'},h('h3',null,detail.bookingId),h('p',null,detail.status),h('ul',{className:'timeline'},...(detail.history??[]).map((item,i)=>h('li',{key:i},item.toStatus+' · '+new Date(item.occurredAt).toLocaleString('vi-VN'))))),

    !loading&&!error&&tab==='reports'&&h('section',{className:'reports-dashboard-section'},
      // KPI summary cards
      summary&&h('div',{className:'kpi-cards-grid'},
        h('div',{className:'kpi-card'},h('p',{className:'kpi-label'},'💰 Tổng doanh thu toàn sàn'),h('h3',{className:'kpi-value'},summary.totalRevenueVnd.toLocaleString('vi-VN')+' ₫')),
        h('div',{className:'kpi-card'},h('p',{className:'kpi-label'},'🎟️ Tổng vé đã bán'),h('h3',{className:'kpi-value'},summary.totalTicketsSold.toLocaleString('vi-VN'))),
        h('div',{className:'kpi-card'},h('p',{className:'kpi-label'},'📋 Đơn hàng thành công'),h('h3',{className:'kpi-value'},summary.totalConfirmedBookings.toLocaleString('vi-VN'))),
        h('div',{className:'kpi-card'},h('p',{className:'kpi-label'},'🚪 Lượt soát vé check-in'),h('h3',{className:'kpi-value'},summary.totalCheckIns.toLocaleString('vi-VN'))),
        h('div',{className:'kpi-card highlight'},h('p',{className:'kpi-label'},'📈 Tỷ lệ lấp đầy bình quân'),h('h3',{className:'kpi-value'},summary.averageOccupancyPercent+'%'))
      ),
      // Export CSV banner
      h('div',{className:'csv-export-banner'},
        h('div',null,
          h('h3',null,'Xuất Báo Cáo Tài Chính & Doanh Thu Sự Kiện'),
          h('p',{className:'muted'},'File CSV mã hóa UTF-8 BOM chuẩn RFC 4180 và tự động escape Anti-CSV Formula Injection an toàn tuyệt đối cho Microsoft Excel và LibreOffice.')
        ),
        h('a',{
          className:'btn-export-csv primary',
          href:api.reports?.exportCsvUrl?api.reports.exportCsvUrl():'http://127.0.0.1:8080/api/admin/reports/export-csv',
          target:'_blank',
          rel:'noopener noreferrer',
          download:'eventflow-revenue-report.csv'
        },'📥 Xuất Báo Cáo CSV (Excel-Safe)')
      ),
      // Events report table
      eventReports.length>0?table('Doanh thu & Tỷ lệ lấp đầy chi tiết theo sự kiện',['Tên sự kiện','Địa điểm','Thời gian','Sức chứa','Vé GA bán','Vé Ghế bán','Tổng vé bán','Doanh thu (VNĐ)','Tỷ lệ lấp đầy'],eventReports.map(e=>h('tr',{key:e.eventId},
        h('td',null,h('strong',null,e.title),h('br',null),h('small',{className:'muted'},e.eventId)),
        h('td',null,e.venue),
        h('td',null,e.startsAt?new Date(e.startsAt).toLocaleString('vi-VN'):'—'),
        h('td',null,e.totalCapacity.toLocaleString('vi-VN')),
        h('td',null,e.soldGa.toLocaleString('vi-VN')),
        h('td',null,e.soldSeated.toLocaleString('vi-VN')),
        h('td',null,h('strong',null,e.totalSold.toLocaleString('vi-VN'))),
        h('td',null,h('strong',null,e.revenueVnd.toLocaleString('vi-VN')+' ₫')),
        h('td',null,h('span',{className:'occupancy-badge'},e.occupancyPercent+'%'))
      ))):h('p',{className:'empty-state'},'Chưa có dữ liệu sự kiện để kết xuất báo cáo.')
    ),

    !loading&&!error&&data&&tab==='outbox'&&table('Outbox sau commit nghiệp vụ',['Event ID / loại','Booking','Version','Trạng thái','Số lần gửi','Chi tiết'],(data.items as Outbox[]).map(e=>h('tr',{key:e.eventId},h('td',{className:'booking-id'},e.eventId,h('small',null,e.eventType)),h('td',{className:'booking-id'},e.bookingId),h('td',null,e.aggregateVersion),h('td',null,e.status),h('td',null,e.attempts),h('td',null,h('button',{type:'button',onClick:()=>{setRecoveryEvent(e.eventId);switchTab('recovery');}},'Chi tiết event '+e.eventId))))),
    tab==='outbox'&&h('p',{className:'muted'},'PENDING: đang chờ; PUBLISHED: Kafka đã xác nhận; FAILED: hết ngân sách gửi. Chọn một event để xem audit/replay có kiểm soát.'),
    data&&tab!=='inventory'&&tab!=='reports'&&h('nav',{className:'pagination','aria-label':'Phân trang quản trị'},h('button',{type:'button',disabled:loading||page===0,onClick:()=>setPage(p=>p-1)},'Trang trước'),h('span',{'aria-live':'polite'},'Trang '+(page+1)+' · '+data.totalElements+' bản ghi'),h('button',{type:'button',disabled:loading||(page+1)*data.size>=data.totalElements,onClick:()=>setPage(p=>p+1)},'Trang sau')),
  );
}
