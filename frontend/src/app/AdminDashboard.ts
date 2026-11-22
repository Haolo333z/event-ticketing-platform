import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent} from 'react';
import {ApiError, getStoredUser} from '../api/client.ts';
import type {Booking,Status} from '../api/client.ts';
import type {AdminApi,AdminEvent,AdminInventory,CreateEvent,PatchEvent,Page,Outbox,AdminUser,ContactLeadItem,AdminRefundItem,PlatformSummary,EventReport,CircuitBreakerStatus,ReconciliationRunSummary,ReconciliationRunDetail,ScalpingAlert,SellOutForecast} from '../api/admin.ts';
import {EventEditor} from './EventEditor.ts';
import {RecoveryView} from './RecoveryView.ts';

type Tab='events'|'users'|'bookings'|'payouts'|'reports'|'reconciliation'|'leads'|'inventory'|'outbox'|'recovery'|'ai-ops';
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

  // V4 Financial Reconciliation & Circuit Breaker state
  const todayStr=new Date().toISOString().slice(0,10);
  const[circuitBreaker,setCircuitBreaker]=useState<CircuitBreakerStatus|null>(null);
  const[reconcileRuns,setReconcileRuns]=useState<ReconciliationRunSummary[]>([]);
  const[selectedRunDetail,setSelectedRunDetail]=useState<ReconciliationRunDetail|null>(null);
  const[reconcileDate,setReconcileDate]=useState(todayStr);
  const[reconcileMode,setReconcileMode]=useState<'CLEAN'|'DISCREPANCY_DEMO'>('CLEAN');
  const[reconcileBusy,setReconcileBusy]=useState(false);

  // V4 Organizer Payouts state
  const[payoutsList,setPayoutsList]=useState<import('../api/client.ts').OrganizerPayoutItem[]>([]);
  const[payoutStatusFilter,setPayoutStatusFilter]=useState<string>('ALL');
  const[payoutModal,setPayoutModal]=useState<{payout:import('../api/client.ts').OrganizerPayoutItem;decision:'APPROVE'|'PAID'|'REJECT'}|null>(null);
  const[payoutNote,setPayoutNote]=useState('');
  const[payoutProcessing,setPayoutProcessing]=useState(false);

  // V3 AI Ops Diagnostics state
  const[aiDiagBusy,setAiDiagBusy]=useState(false);
  const[aiDiag,setAiDiag]=useState<{adapterUsed:string;severity:string;summaryVi:string;rootCause:string;recommendedAction:string;safeToReplay:boolean}|null>(null);

  async function runAiDiagnostics(bookingId?:string){
    if(!api.aiDiagnose||aiDiagBusy)return;
    setAiDiagBusy(true);setError('');
    try{
      const res=await api.aiDiagnose(bookingId?{bookingId}:{});
      setAiDiag(res);
    }catch(v){
      setError(message(v));
    }finally{
      setAiDiagBusy(false);
    }
  }

  // V4 AI Ops & Scalping Alerts state
  const[scalpingAlerts,setScalpingAlerts]=useState<ScalpingAlert[]>([]);
  const[scalpingFilter,setScalpingFilter]=useState<string>('ALL');
  const[forecastEvents,setForecastEvents]=useState<AdminEvent[]>([]);
  const[selectedForecastId,setSelectedForecastId]=useState<string>('');
  const[forecast,setForecast]=useState<SellOutForecast|null>(null);
  const[forecastLoading,setForecastLoading]=useState<boolean>(false);
  const[scalpingBusy,setScalpingBusy]=useState<boolean>(false);

  async function loadForecast(eventId:string){
    if(!api.sellOutForecast||!eventId)return;
    setForecastLoading(true);
    try{
      const f=await api.sellOutForecast(eventId);
      setForecast(f);
    }catch(err){
      setError(message(err));
    }finally{
      setForecastLoading(false);
    }
  }

  async function handleScalpingAction(alertId:string,action:'LOCK_ACCOUNT'|'DISMISS'){
    if(!api.actionScalpingAlert||scalpingBusy)return;
    setScalpingBusy(true);setError('');setNotice('');
    try{
      const res=await api.actionScalpingAlert(alertId,action);
      setNotice(res.messageVi||'Đã xử lý cảnh báo thành công.');
      if(api.scalpingAlerts){
        const list=await api.scalpingAlerts(scalpingFilter==='ALL'?undefined:scalpingFilter);
        setScalpingAlerts(list);
      }
    }catch(err){
      setError(message(err));
    }finally{
      setScalpingBusy(false);
    }
  }

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
    else if(tab==='reconciliation'){
      request=Promise.all([
        api.circuitBreaker?.getStatus?api.circuitBreaker.getStatus():Promise.resolve(null),
        api.reconciliation?.getRuns?api.reconciliation.getRuns(page,20):Promise.resolve([])
      ]).then(([cbData,runsData])=>{
        if(active){
          setCircuitBreaker(cbData);
          setReconcileRuns(runsData||[]);
        }
        return {items:runsData||[],page,size:20,totalElements:(runsData||[]).length};
      });
    }
    else if(tab==='payouts'){
      request=(api.payouts?api.payouts(payoutStatusFilter==='ALL'?undefined:payoutStatusFilter):Promise.resolve([]))
        .then(items=>{
          if(active)setPayoutsList(items||[]);
          return {items:items||[],page:0,size:(items||[]).length,totalElements:(items||[]).length};
        });
    }
    else if(tab==='ai-ops'){
      request=Promise.all([
        api.scalpingAlerts?api.scalpingAlerts(scalpingFilter==='ALL'?undefined:scalpingFilter):Promise.resolve([]),
        api.events(0,50)
      ]).then(([alerts,evtsPage])=>{
        if(active){
          setScalpingAlerts(alerts||[]);
          setForecastEvents(evtsPage.items||[]);
          const fId=selectedForecastId||evtsPage.items[0]?.id;
          if(fId&&api.sellOutForecast){
            setSelectedForecastId(fId);
            api.sellOutForecast(fId).then(f=>{if(active)setForecast(f);}).catch(()=>{});
          }
        }
        return {items:(alerts||[]) as any,page:0,size:20,totalElements:(alerts||[]).length};
      });
    }
    else request=api.outbox(page,20);

    request.then(result=>{if(active)setData(result);}).catch(value=>{if(active)setError(message(value));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[api,tab,page,filter,revision,searchUser,userRoleFilter,leadStatusFilter,payoutStatusFilter,scalpingFilter]);

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
    const currentAdmin = getStoredUser();
    if(currentAdmin && currentAdmin.id === userId && role === 'ADMIN' && action === 'REVOKE'){
      setError('Không thể tự hạ quyền Admin của chính mình.');
      return;
    }
    try{
      await api.updateUserRole(userId,role,action);
      setNotice(action==='GRANT'?`Đã cấp quyền ${role} thành công.`:`Đã thu hồi quyền ${role} thành công.`);
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  async function handleStatusAction(userId:string,status:'ACTIVE'|'LOCKED'){
    if(!api.updateUserStatus)return;setError('');setNotice('');
    const currentAdmin = getStoredUser();
    if(currentAdmin && currentAdmin.id === userId && status === 'LOCKED'){
      setError('Không thể tự khóa tài khoản Admin đang đăng nhập của chính mình.');
      return;
    }
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

  async function handleConvertToOrganizer(leadId:string){
    if(!api.convertToOrganizer)return;setError('');setNotice('');
    try{
      const res=await api.convertToOrganizer(leadId);
      setNotice(`Đã duyệt đối tác thành công! Cấp quyền ORGANIZER cho ${res.organizationName} (${res.email}) với gói ${res.saasPlan}.`);
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
  }

  async function handleConfirmProcessPayout(){
    if(!api.processPayout||!payoutModal||payoutProcessing)return;
    setPayoutProcessing(true);setError('');setNotice('');
    try{
      const res=await api.processPayout(payoutModal.payout.id,payoutModal.decision,payoutNote||undefined);
      setNotice(res.message||`Đã xử lý yêu cầu rút tiền thành công (Trạng thái: ${res.status}).`);
      setPayoutModal(null);
      setPayoutNote('');
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
    finally{setPayoutProcessing(false);}
  }

  async function handleResetCircuitBreaker(){
    if(!api.circuitBreaker)return;setError('');setNotice('');
    try{
      const res=await api.circuitBreaker.reset();
      setCircuitBreaker(res);
      setNotice('Đã khôi phục mạch cổng thanh toán về trạng thái CLOSED (Hoạt động bình thường).');
    }catch(e){setError(message(e));}
  }

  async function handleTripCircuitBreaker(){
    if(!api.circuitBreaker)return;setError('');setNotice('');
    try{
      const res=await api.circuitBreaker.trip('Thử nghiệm ngắt mạch thủ công từ Admin');
      setCircuitBreaker(res);
      setNotice('Đã ngắt mạch cổng thanh toán sang trạng thái OPEN (Chặn giao dịch gửi tới VNPAY).');
    }catch(e){setError(message(e));}
  }

  async function handleRunReconciliation(){
    if(!api.reconciliation||reconcileBusy)return;setError('');setNotice('');setReconcileBusy(true);
    try{
      const detail=await api.reconciliation.run(reconcileDate,reconcileMode);
      setSelectedRunDetail(detail);
      setNotice(`Đối soát hoàn tất: ${detail.summary.matchedCount} khớp, ${detail.summary.discrepancyCount} bất thường.`);
      setRevision(x=>x+1);
    }catch(e){setError(message(e));}
    finally{setReconcileBusy(false);}
  }

  async function handleViewRunDetail(runId:string){
    if(!api.reconciliation)return;setError('');
    try{
      const detail=await api.reconciliation.getRunDetail(runId);
      setSelectedRunDetail(detail);
    }catch(e){setError(message(e));}
  }

  async function handleUpdateDiscrepancyStatus(id:string,status:string){
    if(!api.reconciliation)return;setError('');setNotice('');
    try{
      await api.reconciliation.updateStatus(id,status,'Xử lý bởi Quản trị viên');
      setNotice(`Đã cập nhật trạng thái bất thường sang ${status}.`);
      if(selectedRunDetail){
        const updatedDetail=await api.reconciliation.getRunDetail(selectedRunDetail.summary.id);
        setSelectedRunDetail(updatedDetail);
      }
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
      ['payouts','Duyệt Rút Tiền'],
      ['reports','Báo cáo'],
      ['reconciliation','Đối soát & Cổng TT'],
      ['leads','Yêu cầu Doanh nghiệp'],
      ['ai-ops','🤖 AI Ops & Chống Bot'],
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
      payouts:'Quản lý & Phê duyệt Yêu cầu Rút tiền Nhà tổ chức',
      reports:'Báo cáo doanh thu & Tỷ lệ lấp đầy',
      reconciliation:'Đối soát tài chính & Trạng thái Cổng thanh toán',
      'ai-ops':'AI Ops — Dự báo Cháy vé & Phát hiện Cụm Đầu cơ',
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

    tab==='payouts'&&h('div',{className:'filter-field'},
      h('label',{htmlFor:'payout-status-filter'},'Lọc trạng thái yêu cầu rút tiền'),
      h('select',{
        id:'payout-status-filter',
        value:payoutStatusFilter,
        onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setPayoutStatusFilter(e.target.value);setPage(0);}
      },
        h('option',{value:'ALL'},'Tất cả trạng thái'),
        h('option',{value:'REQUESTED'},'Đang chờ duyệt (REQUESTED)'),
        h('option',{value:'APPROVED'},'Đã duyệt chi, chờ chuyển tiền (APPROVED)'),
        h('option',{value:'PAID'},'Đã thanh toán (PAID)'),
        h('option',{value:'REJECTED'},'Đã từ chối (REJECTED)')
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
      const currentAdmin = getStoredUser();
      const isSelf = !!(currentAdmin && (currentAdmin.id === u.id || (currentAdmin.email && u.email && currentAdmin.email.toLowerCase() === u.email.toLowerCase())));
      return h('tr',{key:u.id},
        h('td',null,h('strong',null,u.displayName),isSelf&&h('span',{className:'badge-self',style:{marginLeft:'0.4rem',fontSize:'0.75rem',padding:'0.1rem 0.4rem',borderRadius:'4px',background:'#4f46e5',color:'#fff'}},'Bạn'),h('br',null),h('small',{className:'muted'},u.id)),
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
            rolesList.includes('ADMIN')&&h('button',{type:'button',className:'btn-role-action revoke',disabled:isSelf,title:isSelf?'Không thể tự hạ quyền Admin của chính mình':undefined,style:isSelf?{opacity:0.5,cursor:'not-allowed'}:undefined,onClick:()=>void handleRoleAction(u.id,'ADMIN','REVOKE')},'- ADMIN'),
            // Lock / Unlock toggle
            isLocked?h('button',{type:'button',className:'btn-status-toggle unlock',disabled:isSelf,title:isSelf?'Không thể tự thao tác tài khoản của chính mình':undefined,style:isSelf?{opacity:0.5,cursor:'not-allowed'}:undefined,onClick:()=>void handleStatusAction(u.id,'ACTIVE')},'Mở khóa'):h('button',{type:'button',className:'btn-status-toggle lock',disabled:isSelf,title:isSelf?'Không thể tự khóa tài khoản của chính mình':undefined,style:isSelf?{opacity:0.5,cursor:'not-allowed'}:undefined,onClick:()=>void handleStatusAction(u.id,'LOCKED')},'Khóa')
          )
        )
      );
    })),

    !loading&&!error&&data&&tab==='leads'&&table('Danh sách khách hàng doanh nghiệp yêu cầu tư vấn',['Họ và tên','Email / SĐT','Doanh nghiệp','Gói giải pháp','Nội dung yêu cầu','Trạng thái','Thời gian','Thao tác'],(data.items as ContactLeadItem[]).map(l=>h('tr',{key:l.id},
      h('td',null,h('strong',null,l.fullName)),
      h('td',null,h('div',null,l.email),h('small',{className:'muted'},l.phone)),
      h('td',null,h('div',null,l.companyName||h('em',{className:'muted'},'Cá nhân')),h('small',{className:'muted'},l.eventScale||'')),
      h('td',null,h('span',{className:'badge badge-plan',style:{fontWeight:'bold',padding:'0.2rem 0.5rem',borderRadius:'4px',background:'#e0e7ff',color:'#3730a3'}},l.saasPlan||'STANDARD')),
      h('td',{className:'lead-message-cell'},l.message||'—',l.notes?h('p',{className:'lead-notes'},'Ghi chú: '+l.notes):null),
      h('td',null,h('span',{className:'lead-status-badge lead-'+(l.status||'NEW').toLowerCase()},(l.status||'NEW'))),
      h('td',null,l.createdAt?new Date(l.createdAt).toLocaleString('vi-VN'):'—'),
      h('td',null,
        h('div',{style:{display:'flex',gap:'0.5rem',alignItems:'center',flexWrap:'wrap'}},
          h('select',{
            className:'select-lead-status',
            value:l.status||'NEW',
            onChange:(e:ChangeEvent<HTMLSelectElement>)=>void handleLeadStatusUpdate(l.id,e.target.value as any)
          },
            ...leadStatuses.map(s=>h('option',{key:s,value:s},s))
          ),
          l.status!=='CONVERTED'&&h('button',{
            type:'button',
            className:'btn-convert-organizer',
            style:{background:'#059669',color:'#fff',border:'none',borderRadius:'4px',padding:'0.3rem 0.6rem',fontSize:'0.8rem',cursor:'pointer',fontWeight:'bold'},
            onClick:()=>void handleConvertToOrganizer(l.id)
          },'⚡ Duyệt thành Nhà tổ chức')
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

    !loading&&!error&&tab==='payouts'&&h('section',{className:'payouts-section'},
      payoutsList.length===0?h('p',{className:'empty-state'},'Không có yêu cầu rút tiền nào phù hợp với bộ lọc.'):
      table('Danh sách yêu cầu rút tiền từ Nhà tổ chức',['Mã yêu cầu','Nhà tổ chức','Doanh thu gộp','Phí sàn','Thực nhận (₫)','Ngân hàng thụ hưởng','Trạng thái','Ngày tạo','Thao tác'],payoutsList.map(p=>{
        const isRequested = p.status === 'REQUESTED';
        const isApproved = p.status === 'APPROVED';
        return h('tr',{key:p.id},
          h('td',{className:'booking-id'},p.id,p.adminNotes?h('p',{className:'muted',style:{fontSize:'0.8rem',margin:'0.25rem 0 0 0'}},'Ghi chú: '+p.adminNotes):null),
          h('td',null,h('small',{className:'muted'},p.organizerId)),
          h('td',null,p.grossRevenueVnd.toLocaleString('vi-VN')+' ₫'),
          h('td',null,h('span',{style:{color:'#64748b'}},`${p.platformFeePercent}% (${p.platformFeeVnd.toLocaleString('vi-VN')} ₫)`)),
          h('td',null,h('strong',{style:{color:'#059669',fontSize:'1.05rem'}},p.netPayoutVnd.toLocaleString('vi-VN')+' ₫')),
          h('td',null,
            h('div',{style:{fontWeight:'bold'}},p.bankName),
            h('div',{style:{fontFamily:'monospace'}},p.bankAccountNumber),
            h('small',{className:'muted'},p.bankAccountHolder)
          ),
          h('td',null,
            h('span',{
              className:`badge badge-payout-${p.status.toLowerCase()}`,
              style:{
                padding:'0.25rem 0.5rem',borderRadius:'4px',fontWeight:'bold',fontSize:'0.8rem',
                background:p.status==='PAID'?'#d1fae5':p.status==='APPROVED'?'#e0e7ff':p.status==='REQUESTED'?'#fef3c7':'#fee2e2',
                color:p.status==='PAID'?'#065f46':p.status==='APPROVED'?'#3730a3':p.status==='REQUESTED'?'#92400e':'#991b1b'
              }
            },p.status)
          ),
          h('td',null,p.requestedAt?new Date(p.requestedAt).toLocaleString('vi-VN'):'—'),
          h('td',{className:'actions'},
            isRequested&&h('div',{style:{display:'flex',gap:'0.3rem',flexWrap:'wrap'}},
              h('button',{type:'button',className:'btn-refund-approve',style:{fontSize:'0.8rem',padding:'0.25rem 0.5rem'},onClick:()=>setPayoutModal({payout:p,decision:'APPROVE'})},'Duyệt chi'),
              h('button',{type:'button',className:'btn-refund-reject',style:{fontSize:'0.8rem',padding:'0.25rem 0.5rem'},onClick:()=>setPayoutModal({payout:p,decision:'REJECT'})},'Từ chối')
            ),
            isApproved&&h('div',{style:{display:'flex',gap:'0.3rem',flexWrap:'wrap'}},
              h('button',{type:'button',className:'btn-refund-approve',style:{background:'#0284c7',borderColor:'#0284c7',fontSize:'0.8rem',padding:'0.25rem 0.5rem'},onClick:()=>setPayoutModal({payout:p,decision:'PAID'})},'Xác nhận đã chuyển'),
              h('button',{type:'button',className:'btn-refund-reject',style:{fontSize:'0.8rem',padding:'0.25rem 0.5rem'},onClick:()=>setPayoutModal({payout:p,decision:'REJECT'})},'Từ chối')
            ),
            p.status==='PAID'&&h('span',{className:'muted',style:{fontSize:'0.85rem'}},'Đã chuyển tiền'+(p.processedAt?' · '+new Date(p.processedAt).toLocaleDateString('vi-VN'):'')),
            p.status==='REJECTED'&&h('span',{style:{color:'#b91c1c',fontSize:'0.85rem'}},p.adminNotes||'Đã từ chối')
          )
        );
      })),

      // Payout action modal
      payoutModal&&h('div',{className:'modal-backdrop',style:{position:'fixed',top:0,left:0,right:0,bottom:0,background:'rgba(0,0,0,0.5)',display:'flex',alignItems:'center',justifyContent:'center',zIndex:999}},
        h('div',{className:'modal-content',style:{background:'#fff',padding:'1.5rem',borderRadius:'0.75rem',maxWidth:'500px',width:'90%',boxShadow:'0 20px 25px -5px rgba(0,0,0,0.1)'}},
          h('h3',null,payoutModal.decision==='APPROVE'?'Phê duyệt yêu cầu rút tiền':payoutModal.decision==='PAID'?'Xác nhận đã chuyển khoản thành công':'Từ chối yêu cầu rút tiền'),
          h('p',{style:{marginTop:'0.5rem'}},
            h('strong',null,'Số tiền thực nhận: '),
            h('span',{style:{color:'#059669',fontSize:'1.1rem',fontWeight:'bold'}},payoutModal.payout.netPayoutVnd.toLocaleString('vi-VN')+' ₫')
          ),
          h('p',null,
            h('strong',null,'Ngân hàng: '),`${payoutModal.payout.bankName} - STK: ${payoutModal.payout.bankAccountNumber} (${payoutModal.payout.bankAccountHolder})`
          ),
          h('div',{style:{marginTop:'1rem'}},
            h('label',{htmlFor:'payout-note-input',style:{display:'block',fontWeight:'bold',marginBottom:'0.25rem'}},
              payoutModal.decision==='PAID'?'Mã giao dịch ngân hàng / Tham chiếu chuyển tiền':payoutModal.decision==='REJECT'?'Lý do từ chối (bắt buộc thông báo cho đối tác)':'Ghi chú nội bộ quản trị viên'
            ),
            h('textarea',{
              id:'payout-note-input',
              style:{width:'100%',minHeight:'80px',padding:'0.5rem',borderRadius:'4px',border:'1px solid #cbd5e1'},
              placeholder:payoutModal.decision==='PAID'?'Ví dụ: FT260348719283 - Vietcombank chuyển thành công':'Nhập lý do hoặc ghi chú...',
              value:payoutNote,
              onChange:(e:ChangeEvent<HTMLTextAreaElement>)=>setPayoutNote(e.target.value)
            })
          ),
          h('div',{style:{display:'flex',justifyContent:'flex-end',gap:'0.5rem',marginTop:'1.25rem'}},
            h('button',{type:'button',className:'quiet',disabled:payoutProcessing,onClick:()=>{setPayoutModal(null);setPayoutNote('');}},'Hủy bỏ'),
            h('button',{
              type:'button',
              className:'primary',
              style:{background:payoutModal.decision==='REJECT'?'#ef4444':payoutModal.decision==='PAID'?'#0284c7':'#059669'},
              disabled:payoutProcessing,
              onClick:()=>void handleConfirmProcessPayout()
            },payoutProcessing?'Đang xử lý…':payoutModal.decision==='APPROVE'?'Xác nhận Duyệt chi':payoutModal.decision==='PAID'?'Xác nhận Đã chuyển':'Xác nhận Từ chối')
          )
        )
      )
    ),

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

    !loading&&!error&&tab==='reconciliation'&&h('section',{className:'reconciliation-section'},
      // 1. Circuit Breaker Control Card
      circuitBreaker&&h('div',{className:'kpi-cards-grid',style:{marginBottom:'1.5rem'}},
        h('div',{className:'kpi-card',style:{borderLeft:`4px solid ${circuitBreaker.state==='CLOSED'?'#10b981':circuitBreaker.state==='HALF_OPEN'?'#f59e0b':'#ef4444'}`}},
          h('p',{className:'kpi-label'},'⚡ Trạng thái Circuit Breaker (VNPAY)'),
          h('h3',{className:'kpi-value'},
            h('span',{className:`badge cb-${circuitBreaker.state.toLowerCase()}`,style:{fontSize:'1.1rem',padding:'0.25rem 0.75rem',borderRadius:'9999px',background:circuitBreaker.state==='CLOSED'?'#d1fae5':circuitBreaker.state==='HALF_OPEN'?'#fef3c7':'#fee2e2',color:circuitBreaker.state==='CLOSED'?'#065f46':circuitBreaker.state==='HALF_OPEN'?'#92400e':'#991b1b'}},
              circuitBreaker.state==='CLOSED'?'🟢 CLOSED (Bình thường)':circuitBreaker.state==='HALF_OPEN'?'🟡 HALF-OPEN (Thử nghiệm)':'🔴 OPEN (Ngắt mạch)'
            )
          ),
          h('p',{className:'muted',style:{fontSize:'0.85rem',marginTop:'0.5rem'}},
            `Lỗi liên tiếp: ${circuitBreaker.failureCount}/${circuitBreaker.failureThreshold} · Thời gian chờ: ${circuitBreaker.resetTimeoutSeconds}s ${circuitBreaker.remainingCooldownSeconds>0?`· Còn lại: ${circuitBreaker.remainingCooldownSeconds}s`:''}`
          ),
          circuitBreaker.lastFailureReason&&h('p',{style:{fontSize:'0.85rem',color:'#b91c1c',marginTop:'0.25rem'}},'Lý do: '+circuitBreaker.lastFailureReason),
          h('div',{style:{display:'flex',gap:'0.5rem',marginTop:'1rem'}},
            h('button',{type:'button',className:'quiet btn-cb-reset',onClick:()=>void handleResetCircuitBreaker()},'🔄 Reset mạch (CLOSED)'),
            h('button',{type:'button',className:'quiet btn-cb-trip',style:{color:'#b91c1c'},onClick:()=>void handleTripCircuitBreaker()},'⚠️ Ngắt mạch (Trip OPEN)')
          )
        ),
        // 2. Reconciliation Trigger Card
        h('div',{className:'kpi-card'},
          h('p',{className:'kpi-label'},'📅 Kích hoạt đối soát sổ cái'),
          h('div',{style:{display:'flex',gap:'0.5rem',alignItems:'center',marginTop:'0.5rem',flexWrap:'wrap'}},
            h('input',{type:'date',id:'reconcile-date',className:'date-input',value:reconcileDate,onChange:(e:ChangeEvent<HTMLInputElement>)=>setReconcileDate(e.target.value)}),
            h('select',{id:'reconcile-mode',className:'select-input',value:reconcileMode,onChange:(e:ChangeEvent<HTMLSelectElement>)=>setReconcileMode(e.target.value as 'CLEAN'|'DISCREPANCY_DEMO')},
              h('option',{value:'CLEAN'},'Khớp 100% (Clean)'),
              h('option',{value:'DISCREPANCY_DEMO'},'Mô phỏng Lệch tiền & Thiếu GD (Demo)')
            ),
            h('button',{type:'button',className:'best-seats-btn btn-run-reconciliation',disabled:reconcileBusy,onClick:()=>void handleRunReconciliation()},reconcileBusy?'Đang đối soát…':'⚡ Chạy đối soát ngay')
          ),
          h('p',{className:'muted',style:{fontSize:'0.85rem',marginTop:'0.75rem'}},'Tự động so khớp 2 chiều giữa đơn hàng thành công trong PostgreSQL và sao kê VNPAY Sandbox.')
        )
      ),

      // 3. Selected Run Details Banner (if active)
      selectedRunDetail&&h('div',{className:'reconcile-detail-banner',style:{background:'#f8fafc',padding:'1.25rem',borderRadius:'0.75rem',border:'1px solid #e2e8f0',marginBottom:'1.5rem'}},
        h('div',{style:{display:'flex',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap',gap:'0.5rem'}},
          h('div',null,
            h('h3',{style:{margin:0}},`Chi tiết đối soát ngày ${selectedRunDetail.summary.reconciliationDate} (${selectedRunDetail.summary.status})`),
            h('p',{className:'muted',style:{margin:'0.25rem 0 0 0',fontSize:'0.9rem'}},
              `Tổng GD: ${selectedRunDetail.summary.totalTransactions} · Khớp: ${selectedRunDetail.summary.matchedCount} · Bất thường: ${selectedRunDetail.summary.discrepancyCount} · Tiền DB: ${selectedRunDetail.summary.totalSystemAmountVnd.toLocaleString('vi-VN')} ₫ · Tiền Cổng: ${selectedRunDetail.summary.totalGatewayAmountVnd.toLocaleString('vi-VN')} ₫ · Chênh lệch: ${selectedRunDetail.summary.differenceAmountVnd.toLocaleString('vi-VN')} ₫`
            )
          ),
          h('a',{
            className:'btn-export-csv primary',
            href:api.reconciliation?.exportCsvUrl?api.reconciliation.exportCsvUrl(selectedRunDetail.summary.id):'#',
            target:'_blank',
            rel:'noopener noreferrer',
            download:`eventflow-reconciliation-${selectedRunDetail.summary.id}.csv`
          },'📥 Xuất CSV (Excel-Safe)')
        ),
        selectedRunDetail.discrepancies.length>0?table('Danh sách bất thường cần xử lý',['Loại bất thường','Mã đơn Merchant','Mã GD Cổng','Tiền DB (₫)','Tiền Cổng (₫)','Chênh lệch (₫)','Trạng thái','Xử lý'],selectedRunDetail.discrepancies.map(d=>h('tr',{key:d.id},
          h('td',null,h('span',{className:`badge badge-${d.discrepancyType.toLowerCase()}`,style:{padding:'0.2rem 0.5rem',borderRadius:'4px',fontWeight:'bold',fontSize:'0.8rem',background:d.discrepancyType==='AMOUNT_MISMATCH'?'#ffedd5':d.discrepancyType==='MISSING_IN_GATEWAY'?'#fee2e2':'#f3e8ff',color:d.discrepancyType==='AMOUNT_MISMATCH'?'#c2410c':d.discrepancyType==='MISSING_IN_GATEWAY'?'#b91c1c':'#6b21a8'}},d.discrepancyType)),
          h('td',null,h('code',null,d.merchantRef||'—')),
          h('td',null,h('small',null,d.providerTxRef||'—')),
          h('td',null,d.systemAmountVnd.toLocaleString('vi-VN')),
          h('td',null,d.gatewayAmountVnd.toLocaleString('vi-VN')),
          h('td',{style:{color:d.differenceVnd!==0?'#b91c1c':'inherit',fontWeight:'bold'}},(d.differenceVnd>0?'+':'')+d.differenceVnd.toLocaleString('vi-VN')),
          h('td',null,h('span',{className:'status-pill'},d.status)),
          h('td',null,
            d.status!=='RESOLVED'&&h('div',{style:{display:'flex',gap:'0.25rem'}},
              h('button',{type:'button',className:'quiet',onClick:()=>void handleUpdateDiscrepancyStatus(d.id,'INVESTIGATING')},'Điều tra'),
              h('button',{type:'button',className:'btn-refund-approve',style:{fontSize:'0.75rem',padding:'0.2rem 0.4rem'},onClick:()=>void handleUpdateDiscrepancyStatus(d.id,'RESOLVED')},'Khớp')
            )
          )
        ))):h('p',{style:{color:'#059669',marginTop:'1rem',fontWeight:'bold'}},'✅ Phiên đối soát này khớp 100%, không phát hiện bất kỳ sai lệch nào.')
      ),

      // 4. Reconciliation Runs History Table
      reconcileRuns.length>0?table('Lịch sử các phiên đối soát tài chính',['Ngày đối soát','Trạng thái','Tổng GD','Khớp','Bất thường','Tiền hệ thống (₫)','Tiền Cổng (₫)','Chênh lệch (₫)','Thao tác'],reconcileRuns.map(r=>h('tr',{key:r.id},
        h('td',null,h('strong',null,r.reconciliationDate),h('br',null),h('small',{className:'muted'},new Date(r.startedAt).toLocaleTimeString('vi-VN'))),
        h('td',null,h('span',{className:`badge badge-${r.status.toLowerCase()}`,style:{padding:'0.2rem 0.5rem',borderRadius:'4px',fontWeight:'bold',fontSize:'0.8rem',background:r.status==='COMPLETED'?'#d1fae5':r.status==='DISCREPANCY_FOUND'?'#fee2e2':'#f1f5f9',color:r.status==='COMPLETED'?'#065f46':r.status==='DISCREPANCY_FOUND'?'#991b1b':'#475569'}},r.status)),
        h('td',null,r.totalTransactions),
        h('td',{style:{color:'#059669',fontWeight:'bold'}},r.matchedCount),
        h('td',{style:{color:r.discrepancyCount>0?'#b91c1c':'inherit',fontWeight:r.discrepancyCount>0?'bold':'normal'}},r.discrepancyCount),
        h('td',null,r.totalSystemAmountVnd.toLocaleString('vi-VN')),
        h('td',null,r.totalGatewayAmountVnd.toLocaleString('vi-VN')),
        h('td',{style:{color:r.differenceAmountVnd!==0?'#b91c1c':'inherit',fontWeight:'bold'}},(r.differenceAmountVnd>0?'+':'')+r.differenceAmountVnd.toLocaleString('vi-VN')),
        h('td',null,
          h('button',{type:'button',className:'quiet btn-view-run-detail',onClick:()=>void handleViewRunDetail(r.id)},'Xem chi tiết'),
          api.reconciliation?.exportCsvUrl&&h('a',{className:'quiet',style:{marginLeft:'0.5rem'},href:api.reconciliation.exportCsvUrl(r.id),target:'_blank',rel:'noopener noreferrer',download:`eventflow-reconciliation-${r.id}.csv`},'CSV')
        )
      ))):h('p',{className:'empty-state'},'Chưa có dữ liệu phiên đối soát tài chính nào. Hãy nhấn "Chạy đối soát ngay" ở trên.')
    ),

    !loading&&!error&&tab==='ai-ops'&&h('div',{className:'ai-ops-view'},
      // 1. Sell-out & Demand Forecasting Section
      h('div',{className:'report-card',style:{marginBottom:'2rem',background:'#ffffff',padding:'1.5rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
        h('div',{style:{display:'flex',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap',gap:'1rem',marginBottom:'1rem'}},
          h('div',null,
            h('h3',{style:{margin:0,display:'flex',alignItems:'center',gap:'0.5rem',fontSize:'1.15rem'}},'🚀 Dự Báo Cháy Vé & Nhu Cầu Mở Bán (Sell-Out Velocity & Demand)'),
            h('p',{className:'muted',style:{margin:'0.25rem 0 0 0',fontSize:'0.9rem'}},'Phân tích thời gian thực tốc độ bán vé, % giữ chỗ, và khuyến nghị kích hoạt Virtual Waiting Room.')
          ),
          h('div',{style:{display:'flex',alignItems:'center',gap:'0.5rem'}},
            h('label',{htmlFor:'forecast-event-select',className:'muted',style:{fontSize:'0.9rem'}},'Chọn sự kiện:'),
            h('select',{
              id:'forecast-event-select',
              value:selectedForecastId,
              disabled:forecastLoading,
              onChange:(e:ChangeEvent<HTMLSelectElement>)=>{
                const id=e.target.value;
                setSelectedForecastId(id);
                void loadForecast(id);
              }
            },...forecastEvents.map(ev=>h('option',{key:ev.id,value:ev.id},ev.title))),
            h('button',{type:'button',className:'quiet',disabled:forecastLoading||!selectedForecastId,onClick:()=>void loadForecast(selectedForecastId)},forecastLoading?'Đang tính…':'🔄 Dự báo lại')
          )
        ),
        forecast?h('div',null,
          h('div',{style:{display:'grid',gridTemplateColumns:'repeat(auto-fit, minmax(200px, 1fr))',gap:'1rem',marginBottom:'1.5rem'}},
            h('div',{style:{background:'#f8fafc',padding:'1rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
              h('div',{className:'muted',style:{fontSize:'0.85rem'}},'VẬN TỐC BÁN VÉ'),
              h('div',{style:{fontSize:'1.5rem',fontWeight:'bold',color:'#4338ca',marginTop:'0.25rem'}},forecast.salesVelocityPerHour+' vé/giờ'),
              h('small',{className:'muted'},'Bán '+forecast.soldLast24Hours+' vé / 24h qua')
            ),
            h('div',{style:{background:'#f8fafc',padding:'1rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
              h('div',{className:'muted',style:{fontSize:'0.85rem'}},'TỶ LỆ GIỮ CHỖ (HOLD RATE)'),
              h('div',{style:{fontSize:'1.5rem',fontWeight:'bold',color:forecast.heldPercentage>=30?'#b45309':'#0f172a',marginTop:'0.25rem'}},forecast.heldPercentage+'%'),
              h('small',{className:'muted'},forecast.heldTickets+' vé đang giữ chỗ')
            ),
            h('div',{style:{background:'#f8fafc',padding:'1rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
              h('div',{className:'muted',style:{fontSize:'0.85rem'}},'TỶ LỆ LẤP ĐẦY'),
              h('div',{style:{fontSize:'1.5rem',fontWeight:'bold',color:'#059669',marginTop:'0.25rem'}},forecast.occupancyPercentage+'%'),
              h('small',{className:'muted'},(forecast.soldTickets+forecast.heldTickets)+' / '+forecast.totalCapacity+' tổng chỗ')
            ),
            h('div',{style:{background:'#f8fafc',padding:'1rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
              h('div',{className:'muted',style:{fontSize:'0.85rem'}},'DỰ KIẾN CHÁY VÉ'),
              h('div',{style:{fontSize:'1.5rem',fontWeight:'bold',color:forecast.sellOutStatus==='SELL_OUT_IMMINENT'?'#dc2626':'#0f172a',marginTop:'0.25rem'}},
                forecast.estimatedHoursToSellOut!==null?(forecast.estimatedHoursToSellOut===0?'ĐÃ HẾT VÉ':forecast.estimatedHoursToSellOut+' giờ'):'Không có nguy cơ'
              ),
              h('small',{className:'muted'},'Smart Waitlist: '+forecast.waitlistCount+' khách')
            )
          ),
          h('div',{style:{background:forecast.sellOutStatus==='SELL_OUT_IMMINENT'?'#fef2f2':'#eef2ff',border:'1px solid '+(forecast.sellOutStatus==='SELL_OUT_IMMINENT'?'#fecaca':'#c7d2fe'),borderRadius:'8px',padding:'1rem'}},
            h('div',{style:{fontWeight:'bold',color:forecast.sellOutStatus==='SELL_OUT_IMMINENT'?'#991b1b':'#3730a3',marginBottom:'0.25rem',display:'flex',alignItems:'center',gap:'0.5rem'}},
              '🤖 Khuyến Nghị AI ('+forecast.adapterUsed+'):'
            ),
            h('p',{style:{margin:0,color:forecast.sellOutStatus==='SELL_OUT_IMMINENT'?'#b91c1c':'#1e1b4b',lineHeight:1.5}},forecast.recommendationVi),
            forecast.recommendVirtualWaitingRoom&&h('div',{style:{marginTop:'0.75rem',padding:'0.5rem 0.75rem',background:'#fee2e2',color:'#991b1b',borderRadius:'6px',fontSize:'0.9rem',fontWeight:'bold',display:'flex',alignItems:'center',gap:'0.5rem'}},
              '⚡ Khuyến nghị: Bật Virtual Waiting Room cho sự kiện này để phòng ngừa quá tải!'
            )
          )
        ):h('p',{className:'muted'},'Chưa có dữ liệu dự báo cho sự kiện được chọn.')
      ),

      // 2. Anti-Scalping Anomaly Detection Table
      h('div',{className:'report-card',style:{background:'#ffffff',padding:'1.5rem',borderRadius:'8px',border:'1px solid #e2e8f0'}},
        h('div',{style:{display:'flex',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap',gap:'1rem',marginBottom:'1rem'}},
          h('div',null,
            h('h3',{style:{margin:0,fontSize:'1.15rem'}},'🛡️ Cảnh Báo Phát Hiện Cụm Đầu Cơ & Gom Vé (Anti-Scalping Anomaly Alerts)'),
            h('p',{className:'muted',style:{margin:'0.25rem 0 0 0',fontSize:'0.9rem'}},'Tự động phát hiện botnet gom vé cùng IP (MASS_HOLD_SAME_IP) hoặc găm giữ rồi nhả cọc liên tiếp (HOARDING_ABANDON_BURST).')
          ),
          h('div',{style:{display:'flex',alignItems:'center',gap:'0.5rem'}},
            h('label',{htmlFor:'scalping-filter',className:'muted',style:{fontSize:'0.9rem'}},'Trạng thái:'),
            h('select',{
              id:'scalping-filter',
              value:scalpingFilter,
              onChange:(e:ChangeEvent<HTMLSelectElement>)=>{
                setScalpingFilter(e.target.value);
                setRevision(x=>x+1);
              }
            },
              h('option',{value:'ALL'},'Tất cả trạng thái'),
              h('option',{value:'NEW'},'Mới phát hiện (NEW)'),
              h('option',{value:'ACTION_TAKEN'},'Đã xử lý (ACTION_TAKEN)'),
              h('option',{value:'DISMISSED'},'Đã bỏ qua (DISMISSED)')
            )
          )
        ),
        scalpingAlerts.length>0?table('Danh sách cảnh báo đầu cơ & gom vé bất thường',['Thời gian','Loại vi phạm','Mức rủi ro','Đối tượng (Email/IP)','Diễn giải chi tiết','Trạng thái','Thao tác 1 chạm'],scalpingAlerts.map(a=>h('tr',{key:a.id},
          h('td',null,new Date(a.createdAt).toLocaleTimeString('vi-VN')+' '+new Date(a.createdAt).toLocaleDateString('vi-VN')),
          h('td',null,h('span',{style:{fontFamily:'monospace',fontWeight:'bold',fontSize:'0.85rem'}},a.alertType)),
          h('td',null,h('span',{style:{
            padding:'0.2rem 0.5rem',
            borderRadius:'4px',
            fontSize:'0.75rem',
            fontWeight:'bold',
            background:a.riskLevel==='HIGH'?'#fee2e2':a.riskLevel==='MEDIUM'?'#fef3c7':'#e0f2fe',
            color:a.riskLevel==='HIGH'?'#991b1b':a.riskLevel==='MEDIUM'?'#92400e':'#0369a1',
            border:'1px solid '+(a.riskLevel==='HIGH'?'#f87171':a.riskLevel==='MEDIUM'?'#fcd34d':'#7dd3fc')
          }},a.riskLevel)),
          h('td',null,
            a.targetAccountEmail!=='N/A'&&h('div',null,h('strong',null,a.targetAccountEmail)),
            a.clientIp!=='N/A'&&h('small',{className:'muted'},'IP: '+a.clientIp)
          ),
          h('td',{style:{maxWidth:'320px',fontSize:'0.85rem',lineHeight:1.4}},a.explanationVi),
          h('td',null,h('span',{style:{
            padding:'0.2rem 0.5rem',
            borderRadius:'4px',
            fontSize:'0.8rem',
            fontWeight:'bold',
            background:a.status==='NEW'?'#fee2e2':a.status==='ACTION_TAKEN'?'#d1fae5':'#f1f5f9',
            color:a.status==='NEW'?'#991b1b':a.status==='ACTION_TAKEN'?'#065f46':'#475569'
          }},a.status)),
          h('td',null,
            a.status==='NEW'?h('div',{style:{display:'flex',gap:'0.35rem'}},
              h('button',{
                type:'button',
                style:{background:'#dc2626',color:'#fff',borderColor:'#b91c1c',fontSize:'0.8rem',padding:'0.25rem 0.5rem'},
                disabled:scalpingBusy,
                onClick:()=>void handleScalpingAction(a.id,'LOCK_ACCOUNT')
              },'🔒 Khóa tài khoản'),
              h('button',{
                type:'button',
                className:'quiet',
                style:{fontSize:'0.8rem',padding:'0.25rem 0.5rem'},
                disabled:scalpingBusy,
                onClick:()=>void handleScalpingAction(a.id,'DISMISS')
              },'Bỏ qua')
            ):h('span',{className:'muted',style:{fontSize:'0.8rem'}},a.resolvedAt?'Đã chốt':'-')
          )
        ))):h('p',{className:'empty-state'},'Không phát hiện bất kỳ hành vi đầu cơ hoặc gom vé botnet nào. Hệ thống an toàn 100%.')
      )
    ),

    !loading&&!error&&data&&tab==='outbox'&&api.aiDiagnose&&h('div',{className:'ai-ops-bar'},
      h('div',null,
        h('strong',null,'🤖 AI Ops Diagnostics (Phân tích DLQ / Outbox & Hoàn tiền muộn)'),
        h('p',{className:'muted'},'Quét tự động bảng outbox_events, message_failures và refund_attempts để tóm tắt nguyên nhân và khuyến nghị hành động.')
      ),
      h('button',{type:'button',className:'best-seats-btn',disabled:aiDiagBusy,onClick:()=>void runAiDiagnostics()},aiDiagBusy?'Đang chẩn đoán…':'✨ Chẩn đoán hệ thống bằng AI')
    ),
    aiDiag&&(tab==='outbox'||tab==='bookings')&&h('div',{role:'status',className:'ai-ops-result severity-'+aiDiag.severity.toLowerCase()},
      h('strong',null,'🤖 Kết quả chẩn đoán AI ('+aiDiag.adapterUsed+') · Mức độ: '+aiDiag.severity),
      h('p',null,'Nguyên nhân: '+aiDiag.rootCause),
      h('p',null,'Khuyến nghị: '+aiDiag.recommendedAction)
    ),
    !loading&&!error&&data&&tab==='outbox'&&table('Outbox sau commit nghiệp vụ',['Event ID / loại','Booking','Version','Trạng thái','Số lần gửi','Chi tiết'],(data.items as Outbox[]).map(e=>h('tr',{key:e.eventId},h('td',{className:'booking-id'},e.eventId,h('small',null,e.eventType)),h('td',{className:'booking-id'},e.bookingId),h('td',null,e.aggregateVersion),h('td',null,e.status),h('td',null,e.attempts),h('td',null,h('button',{type:'button',onClick:()=>{setRecoveryEvent(e.eventId);switchTab('recovery');}},'Chi tiết event '+e.eventId))))),
    tab==='outbox'&&h('p',{className:'muted'},'PENDING: đang chờ; PUBLISHED: Kafka đã xác nhận; FAILED: hết ngân sách gửi. Chọn một event để xem audit/replay có kiểm soát.'),
    data&&tab!=='inventory'&&tab!=='reports'&&tab!=='reconciliation'&&tab!=='payouts'&&tab!=='ai-ops'&&h('nav',{className:'pagination','aria-label':'Phân trang quản trị'},h('button',{type:'button',disabled:loading||page===0,onClick:()=>setPage(p=>p-1)},'Trang trước'),h('span',{'aria-live':'polite'},'Trang '+(page+1)+' · '+data.totalElements+' bản ghi'),h('button',{type:'button',disabled:loading||(page+1)*data.size>=data.totalElements,onClick:()=>setPage(p=>p+1)},'Trang sau')),
  );
}
