import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent,FormEvent} from 'react';
import {ApiError,readAttempt,saveAttempt,unknownOutcome,readPaymentAttempt,getStoredUser,getStoredToken} from '../api/client.ts';
import {Checkout} from './Checkout.ts';
import type {Api,Attempt,Availability,Booking,Event,Store,User} from '../api/client.ts';

const vnd=(value:number)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
const date=(value:string)=>new Date(value).toLocaleString('vi-VN');
const labels:Record<string,string>={PENDING_PAYMENT:'Đang chờ thanh toán',CONFIRMED:'Đã xác nhận',PAYMENT_FAILED:'Thanh toán thất bại',CANCELLED:'Đã hủy',EXPIRED:'Đã hết hạn',REFUND_REQUIRED:'Cần hoàn tiền'};

function getEventPoster(title:string):string{
  if(title.includes('Mơ Màng')||title.includes('Festival'))return 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80';
  if(title.includes('Giao Hưởng')||title.includes('Beethoven'))return 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&auto=format&fit=crop&q=80';
  if(title.includes('Tech')||title.includes('AI'))return 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&auto=format&fit=crop&q=80';
  if(title.includes('Vũ')||title.includes('Nuối Tiếc'))return 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80';
  return 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=800&auto=format&fit=crop&q=80';
}

function getEventCategory(title:string):string{
  if(title.includes('Mơ Màng')||title.includes('Festival')||title.includes('Vũ'))return 'Concert & Âm nhạc';
  if(title.includes('Giao Hưởng')||title.includes('Beethoven'))return 'Nhà hát & Thính phòng';
  if(title.includes('Tech')||title.includes('AI'))return 'Hội nghị & Công nghệ';
  return 'Sự kiện đặc biệt';
}

function scrollToSection(id: string) {
  const el = document.getElementById(id);
  if (el && typeof el.scrollIntoView === 'function') {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

export function App({api,store,user,embedded=false,onRequireAuth,activeBookingId,currentPath,onNavigate}:{api:Api;store:Store;user?:User|null;embedded?:boolean;onRequireAuth?:(resumeAction:()=>void)=>void;activeBookingId?:string|null;currentPath?:string;onNavigate?:(path:string)=>void}){
  function nav(targetPath: string) {
    if (onNavigate) {
      onNavigate(targetPath);
    } else {
      if (targetPath === '/dat-ve' || targetPath === '/events') scrollToSection('events');
      else if (targetPath === '/lien-he') scrollToSection('contact');
      else if (targetPath === '/bang-gia') scrollToSection('pricing');
      else if (targetPath === '/giai-phap') scrollToSection('features');
    }
  }
  const[pending,setPending]=useState<Attempt|null>(()=>readAttempt(store));
  const[events,setEvents]=useState<Event[]>([]);const[loading,setLoading]=useState(true);
  const initialSelected = pending?.payload.eventId ?? (typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('eventId') ?? '' : '');
  const[selected,setSelected]=useState(initialSelected);const[detail,setDetail]=useState<Event|null>(null);
  const[typeId,setTypeId]=useState(pending?.payload.ticketTypeId??'');const[quantity,setQuantity]=useState(pending?.payload.quantity??1);
  const[seatIds,setSeatIds]=useState<string[]>(pending?.payload.seatIds??[]);
  const[now,setNow]=useState(Date.now);
  const[availability,setAvailability]=useState<Availability|null>(null);const[booking,setBooking]=useState<Booking|null>(null);
  const[history,setHistory]=useState<Booking[]>([]);const[error,setError]=useState<ApiError|null>(null);
  const[notice,setNotice]=useState('');const[busy,setBusy]=useState(false);const submitting=useRef(false);
  const[catalogVersion,setCatalogVersion]=useState(0);
  const[catalogFailed,setCatalogFailed]=useState(false),[detailLoading,setDetailLoading]=useState(false),[detailFailed,setDetailFailed]=useState(false);
  const[historyLoading,setHistoryLoading]=useState(true),[historyFailed,setHistoryFailed]=useState(false),[availabilityStale,setAvailabilityStale]=useState(false);
  const[completed,setCompleted]=useState('');
  
  // Client-side search and category filtering
  const[searchQuery,setSearchQuery]=useState('');
  const[categoryFilter,setCategoryFilter]=useState('ALL');
  const[priceFilter,setPriceFilter]=useState<'ALL'|'UNDER_500K'|'MID'|'OVER_1500K'>('ALL');
  const[faqOpen,setFaqOpen]=useState<number|null>(null);

  // V3 Real-time SSE & Best Contiguous Seats
  const[sseLive,setSseLive]=useState(false);
  const[bestSeatQty,setBestSeatQty]=useState(2);
  const[bestSeatBusy,setBestSeatBusy]=useState(false);
  const[bestSeatNote,setBestSeatNote]=useState('');
  const[waitlistBusy,setWaitlistBusy]=useState(false);
  const[waitlistMsg,setWaitlistMsg]=useState('');
  const[previewSeatView,setPreviewSeatView]=useState<import('../api/client.ts').SeatView|null>(null);
  const[reminderNotice,setReminderNotice]=useState<string|null>(null);
  const[reminderBusy,setReminderBusy]=useState(false);

  // V4 Fan-to-Fan Resale Marketplace
  const[resaleListings,setResaleListings]=useState<import('../api/client.ts').ResaleListingItem[]>([]);
  const[resaleLoading,setResaleLoading]=useState(false);
  const[activeResalePurchase,setActiveResalePurchase]=useState<import('../api/client.ts').ResaleListingItem|null>(null);
  const[resaleReserveResult,setResaleReserveResult]=useState<import('../api/client.ts').ReserveResaleResult|null>(null);
  const[resaleSettleSuccess,setResaleSettleSuccess]=useState<import('../api/client.ts').SettleResaleResult|null>(null);
  const[resaleActionBusy,setResaleActionBusy]=useState(false);
  const[resaleActionError,setResaleActionError]=useState<string|null>(null);

  async function handleBuyResale(item:import('../api/client.ts').ResaleListingItem){
    if(!user){
      if(onRequireAuth)onRequireAuth(()=>void handleBuyResale(item));
      return;
    }
    setActiveResalePurchase(item);
    setResaleActionBusy(true);
    setResaleActionError(null);
    setResaleReserveResult(null);
    setResaleSettleSuccess(null);
    try{
      const res=await api.resale!.reserve(item.id);
      setResaleReserveResult(res);
    }catch(err:any){
      setResaleActionError(err?.message||'Không thể giữ chỗ vé sang nhượng');
    }finally{
      setResaleActionBusy(false);
    }
  }

  async function handleConfirmResalePayment(){
    if(!activeResalePurchase||!api.resale?.settle)return;
    setResaleActionBusy(true);
    setResaleActionError(null);
    try{
      const res=await api.resale.settle(activeResalePurchase.id);
      setResaleSettleSuccess(res);
      if(selected&&api.resale.listForEvent){
        api.resale.listForEvent(selected).then(r=>setResaleListings(r.items)).catch(()=>{});
      }
    }catch(err:any){
      setResaleActionError(err?.message||'Thanh toán vé sang nhượng thất bại');
    }finally{
      setResaleActionBusy(false);
    }
  }

  async function viewSeatPerspective(seatId:string){
    if(!selected||!api.seats?.getView)return;
    try{
      const view=await api.seats.getView(selected,seatId);
      setPreviewSeatView(view);
    }catch{/* preview fallback */}
  }

  async function registerEventReminder(minutes=15){
    if(!selected||!api.reminders?.register)return;
    setReminderBusy(true);
    setReminderNotice(null);
    try{
      const res=await api.reminders.register(selected,minutes);
      setReminderNotice(res.message);
    }catch(err:any){
      fail(err);
    }finally{
      setReminderBusy(false);
    }
  }

  // Commercial Consultation Form State
  const[contactForm,setContactForm]=useState({fullName:'',orgName:'',email:'',phone:'',eventType:'CONCERT',message:''});
  const[contactSubmitted,setContactSubmitted]=useState(false);
  const[contactLoading,setContactLoading]=useState(false);
  const[contactError,setContactError]=useState<string|null>(null);

  function fail(value:unknown){setError(value instanceof ApiError?value:new ApiError('CLIENT_ERROR','Không thể tải dữ liệu. Hãy thử lại.'));}
  async function historyRefresh(){setHistoryLoading(true);try{setHistory((await api.mine()).items);setHistoryFailed(false);}catch(value){setHistoryFailed(true);fail(value);}finally{setHistoryLoading(false);}}
  useEffect(()=>{let active=true;setLoading(true);setCatalogFailed(false);api.events().then(data=>{if(active){setEvents(data.items);setError(null);}}).catch(value=>{if(active){setCatalogFailed(true);fail(value);}}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[api,catalogVersion]);
  useEffect(()=>{void historyRefresh();},[api]);
  useEffect(()=>{let active=true;const payment=readPaymentAttempt(store);if(payment)api.booking(payment.bookingId).then(value=>{if(active)setBooking(value);}).catch(value=>{if(active)fail(value);});return()=>{active=false;};},[api,store]);
  useEffect(()=>{
    let active=true;setDetail(null);setAvailability(null);setDetailLoading(!!selected);setDetailFailed(false);setAvailabilityStale(false);
    if(selected)Promise.all([api.detail(selected),api.availability(selected)]).then(([event,snapshot])=>{
      if(!active)return;setDetail(event);setAvailability(snapshot);
      setTypeId(current=>event.ticketTypes.some(t=>t.id===current&&t.active)?current:(event.ticketTypes.find(t=>t.active)?.id??''));
    }).catch(value=>{if(active){setDetailFailed(true);fail(value);}}).finally(()=>{if(active)setDetailLoading(false);});return()=>{active=false;};
  },[api,selected,catalogVersion]);
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
  useEffect(()=>{
    if(!selected)return;let active=true;
    const timer=setInterval(()=>{api.availability(selected).then(value=>{if(active){setAvailability(value);setAvailabilityStale(false);}}).catch(()=>{if(active)setAvailabilityStale(true);});},3000);
    return()=>{active=false;clearInterval(timer);};
  },[api,selected]);
  useEffect(()=>{
    if(!selected||!api.resale?.listForEvent){
      setResaleListings([]);
      return;
    }
    let active=true;
    setResaleLoading(true);
    api.resale.listForEvent(selected)
      .then(res=>{if(active)setResaleListings(res.items);})
      .catch(()=>{if(active)setResaleListings([]);})
      .finally(()=>{if(active)setResaleLoading(false);});
    return()=>{active=false;};
  },[api,selected]);
  useEffect(()=>{
    setSseLive(false);setBestSeatNote('');
    if(!selected||typeof EventSource==='undefined'||!api.v3?.streamUrl)return;
    let active=true;
    let es:EventSource|null=null;
    try{
      es=new EventSource(api.v3.streamUrl(selected));
      es.addEventListener('connected',()=>{if(active)setSseLive(true);});
      es.addEventListener('inventory-updated',()=>{
        if(!active)return;
        api.availability(selected).then(value=>{if(active){setAvailability(value);setAvailabilityStale(false);}}).catch(()=>{});
      });
      es.onerror=()=>{if(active)setSseLive(false);};
    }catch{/* SSE optional in non-browser/test environments */}
    return()=>{active=false;setSseLive(false);if(es)es.close();};
  },[api,selected]);
  async function pickBestContiguousSeats(){
    if(!selected||!typeId||!api.v3?.bestSeats||bestSeatBusy)return;
    setBestSeatBusy(true);setBestSeatNote('');setError(null);
    try{
      const res=await api.v3.bestSeats(selected,typeId,bestSeatQty);
      setSeatIds(res.seats.map(s=>s.id));
      setBestSeatNote(res.explanation);
    }catch(value){
      fail(value);
    }finally{
      setBestSeatBusy(false);
    }
  }
  async function joinSmartWaitlist(){
    if(!selected||!api.v3?.joinWaitlist||waitlistBusy)return;
    setWaitlistBusy(true);setWaitlistMsg('');setError(null);
    try{
      const entry=await api.v3.joinWaitlist(selected,{ticketTypeId:typeId||undefined,desiredQuantity:Math.min(6,Math.max(1,quantity||1))});
      setWaitlistMsg('🔔 Đã vào Danh sách chờ thông minh (vị trí #' + entry.queuePosition + '). Hệ thống sẽ tự báo ngay khi Expiry Worker nhả vé!');
    }catch(value){
      fail(value);
    }finally{
      setWaitlistBusy(false);
    }
  }
  useEffect(()=>{
    if(!booking||!['PENDING_PAYMENT','EXPIRED'].includes(booking.status))return;let active=true;const id=booking.bookingId;
    const started=Date.now(),budget=booking.status==='EXPIRED'?90000:Math.max(90000,Date.parse(booking.expiresAt)-started+90000);
    const timer=setInterval(()=>{if(Date.now()-started>budget){clearInterval(timer);if(active)setNotice('Đã dừng tự cập nhật. Bạn có thể bấm Cập nhật booking.');return;}api.booking(id).then(value=>{if(active){setBooking(value);if(value.status!=='PENDING_PAYMENT'){void historyRefresh();if(selected)void api.availability(selected).then(snapshot=>{if(active)setAvailability(snapshot);}).catch(()=>{});}}}).catch(()=>{if(active)setNotice('Chưa cập nhật được trạng thái. Bạn có thể tải lại booking.');});},3000);
    return()=>{active=false;clearInterval(timer);};
  },[api,booking?.bookingId,booking?.status,selected]);
  useEffect(()=>{
    if(activeBookingId){
      void openBooking(activeBookingId);
      setTimeout(()=>scrollToSection('booking-result-section'),100);
    }
  },[activeBookingId]);
  async function executeBooking(){
    if(submitting.current)return;submitting.current=true;setBusy(true);setError(null);setNotice('');let attempt=pending;
    try{
      if(!attempt){
        const mode=detail?.ticketTypes.find(t=>t.id===typeId)?.mode;
        if(!selected||!typeId||!mode||mode==='GA'&&(!Number.isInteger(quantity)||quantity<1||quantity>6)||mode==='SEATED'&&(seatIds.length<1||seatIds.length>6))throw new ApiError('VALIDATION_ERROR','Chọn từ 1 đến 6 vé hoặc ghế.');
        attempt={key:crypto.randomUUID(),payload:mode==='GA'?{eventId:selected,ticketTypeId:typeId,quantity}:{eventId:selected,ticketTypeId:typeId,seatIds:[...seatIds].sort()}};
        try{saveAttempt(store,attempt);}catch{throw new ApiError('STORAGE_ERROR','Không lưu được mã yêu cầu. Hãy cho phép session storage rồi thử lại.');}
        setPending(attempt);
      }
      const result=await api.create(attempt);setBooking(result);setCompleted(JSON.stringify(attempt.payload));saveAttempt(store,null);setPending(null);setNotice('Đã giữ vé. Giá và trạng thái được xác nhận bởi máy chủ.');
      await historyRefresh();try{setAvailability(await api.availability(attempt.payload.eventId));}catch{/* Keep the committed booking visible if refresh fails. */}
    }catch(value){
      const problem=value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa xác định được kết quả. Hãy thử lại cùng yêu cầu.');
      if(!unknownOutcome(problem)){saveAttempt(store,null);setPending(null);}fail(problem);
      if(['INVENTORY_UNAVAILABLE','HOLD_EXPIRY_PROCESSING'].includes(problem.code)&&selected){setSeatIds([]);try{setAvailability(await api.availability(selected));}catch{/* Preserve the original conflict message. */}}
    }finally{setBusy(false);submitting.current=false;}
  }
  async function submit(event:FormEvent){
    event.preventDefault();
    if(submitting.current)return;
    const currentUser=user??getStoredUser()??(getStoredToken()?.startsWith('demo-')?{id:'830eb6e6-5d10-48dc-9f71-80f6dec72679',displayName:'Khách hàng demo',email:'customer@eventflow.vn',role:'CUSTOMER',roles:['CUSTOMER']}:null);
    if(!pending&&!currentUser&&onRequireAuth){
      onRequireAuth(()=>{
        void executeBooking();
      });
      return;
    }
    await executeBooking();
  }
  async function openBooking(id:string){try{setBooking(await api.booking(id));setError(null);setNotice('');}catch(value){fail(value);}}
  const type=detail?.ticketTypes.find(t=>t.id===typeId);const snapshot=availability?.ticketTypes.find(t=>t.ticketTypeId===typeId);
  const available=snapshot?.mode==='GA'?snapshot.available:null;
  const seatMap=snapshot?.mode==='SEATED'?[...snapshot.seats].sort((a,b)=>a.label.localeCompare(b.label,'vi',{numeric:true})):[];
  const currentPayload=type?.mode==='SEATED'?{eventId:selected,ticketTypeId:typeId,seatIds:[...seatIds].sort()}:{eventId:selected,ticketTypeId:typeId,quantity};
  const validSelection=type?.mode==='SEATED'?seatIds.length>=1&&seatIds.length<=6&&seatIds.every(id=>seatMap.some(s=>s.id===id&&s.status==='AVAILABLE')):quantity>=1&&quantity<=6&&Number.isInteger(quantity)&&available!==null&&available!==0;
  const canBook=!!pending||!!(detail&&type&&type.active&&validSelection&&completed!==JSON.stringify(currentPayload));
  const remaining=booking?Math.max(0,Math.ceil((Date.parse(booking.expiresAt)-now)/1000)):0;

  // Filtered events
  const filteredEvents=events.filter(e=>{
    const matchesSearch=!searchQuery||e.title.toLowerCase().includes(searchQuery.toLowerCase())||e.venue.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory=categoryFilter==='ALL'||(categoryFilter==='GA'&&e.ticketTypes.some(t=>t.mode==='GA'))||(categoryFilter==='SEATED'&&e.ticketTypes.some(t=>t.mode==='SEATED'));
    const minP=e.ticketTypes.length?Math.min(...e.ticketTypes.map(t=>t.priceVnd)):0;
    const matchesPrice=priceFilter==='ALL'
      ||(priceFilter==='UNDER_500K'&&minP<500000)
      ||(priceFilter==='MID'&&minP>=500000&&minP<=1500000)
      ||(priceFilter==='OVER_1500K'&&minP>1500000);
    return matchesSearch&&matchesCategory&&matchesPrice;
  });

  return h('div',{className:'shell eventflow-consumer-root'},
    !embedded&&h('header',{className:'topbar'},
      h('div',{className:'brand-container'},
        h('a',{href:'/',className:'brand'},
          'EventFlow',
          h('span',null,'Enterprise Event Platform')
        ),
        h('span',{className:'system-status-indicator'},
          h('span',{className:'status-dot pulse'}),
          'Live PostgreSQL & Kafka EDA'
        )
      ),
      h('nav',{className:'corporate-nav-links'},
        h('a',{href:'#events',className:'nav-link',onClick:(e)=>{e.preventDefault();scrollToSection('events');}},'Sự kiện'),
        h('a',{href:'#features',className:'nav-link',onClick:(e)=>{e.preventDefault();scrollToSection('features');}},'Công nghệ'),
        h('a',{href:'#pricing',className:'nav-link',onClick:(e)=>{e.preventDefault();scrollToSection('pricing');}},'Bảng giá'),
        h('a',{href:'#contact',className:'nav-link',onClick:(e)=>{e.preventDefault();scrollToSection('contact');}},'Liên hệ')
      ),
      h('button',{type:'button',className:'topbar-cta-btn',onClick:()=>scrollToSection('contact')},'Tổ chức sự kiện ngay'),
      h('span',{className:'persona'},'Khách hàng demo')
    ),
    h('main',null,
      // 1. HIGH-IMPACT HERO SECTION (SAOKIM BRANDING ENTERPRISE EDITION)
      (currentPath === '/dat-ve' || currentPath === '/events') ? h('section', { className: 'intro hero-banner full-bleed-hero page-hero ticketing-hero' },
        h('div', { className: 'hero-content-wrapper shell' },
          h('nav', { className: 'page-breadcrumb', 'aria-label': 'Breadcrumb' },
            h('button', { type: 'button', className: 'breadcrumb-link', onClick: () => nav('/') }, 'Trang chủ'),
            h('span', { className: 'breadcrumb-separator' }, ' / '),
            h('span', { className: 'breadcrumb-current' }, 'Đặt vé & Sơ đồ ghế')
          ),
          h('div', { className: 'hero-badge' }, '🎟️ TRUNG TÂM PHÂN PHỐI VÉ & SƠ ĐỒ GHẾ KHÁN PHÒNG'),
          h('h1', null, 'ĐẶT VÉ SỰ KIỆN TRỰC TUYẾN & CHỌN CHỖ NGỒI SÂN KHẤU'),
          h('p', null, 'Lựa chọn sự kiện bạn yêu thích, chọn vé GA hoặc chọn vị trí ghế ngồi trực quan trên sơ đồ Sân khấu chính. Giữ chỗ tức thì được bảo vệ 100% bằng cơ chế khóa hàng ACID PostgreSQL.'),
          h('div', { className: 'hero-cta-group' },
            h('button', { type: 'button', className: 'hero-cta-coral', onClick: () => scrollToSection('events') }, 'Chọn vé và ghế ngồi ngay ↓'),
            h('button', { type: 'button', className: 'hero-cta-secondary', onClick: () => nav('/giai-phap') }, 'Xem giải pháp công nghệ →')
          )
        )
      ) : h('section', { className: 'intro hero-banner full-bleed-hero' },
        h('div', { className: 'hero-content-wrapper shell' },
          h('div', { className: 'hero-badge' }, '★ NỀN TẢNG PHÂN PHỐI VÉ & VẬN HÀNH SỰ KIỆN DOANH NGHIỆP HÀNG ĐẦU'),
          h('h1', null, 'GIẢI PHÁP PHÂN PHỐI VÉ TỐC ĐỘ CAO & PHÂN BỔ CHỖ NGỒI ĐỘC QUYỀN'),
          h('p', null, 'Hạ tầng phân tán hướng sự kiện (EDA) cam kết 0% bán lố vé (Zero Oversell), xử lý tức thì hàng chục ngàn lượt mua đồng thời với độ trễ dưới 50ms. Được tin cậy bởi các đơn vị tổ chức biểu diễn hàng đầu.'),
          
          // Quick Action Buttons
          h('div', { className: 'hero-cta-group' },
            h('button', { type: 'button', className: 'hero-cta-coral', onClick: () => nav('/lien-he') }, 'Đặt lịch tư vấn ngay →'),
            h('button', { type: 'button', className: 'hero-cta-secondary', onClick: () => nav('/dat-ve') }, 'Khám phá sự kiện mở bán ↓')
          ),

          // Commercial Stats Bar
          h('div', { className: 'hero-trust-bar' },
            h('div', { className: 'trust-item' },
              h('strong', null, '100.000+'),
              h('span', null, 'Vé Đã Phân Phối')
            ),
            h('div', { className: 'trust-item' },
              h('strong', null, '0%'),
              h('span', null, 'Tỷ Lệ Bán Lố (Zero Oversell)')
            ),
            h('div', { className: 'trust-item' },
              h('strong', null, '99.99%'),
              h('span', null, 'Độ Sẵn Sàng Hệ Thống')
            ),
            h('div', { className: 'trust-item' },
              h('strong', null, '< 50ms'),
              h('span', null, 'Độ Trễ Giao Dịch')
            )
          )
        )
      ),

      // ALERTS
      error&&h('div',{role:'alert',className:'alert error'},
        h('strong',null,error.code==='INVENTORY_UNAVAILABLE'?'Không còn đủ vé':error.code==='BOOKING_BUSY'?'Hệ thống đang bận':error.message),
        h('p',null,error.message),
        h('small',null,'Mã lỗi: '+error.code),
        error.correlationId&&h('small',null,' · Mã tra cứu: '+error.correlationId)
      ),
      notice&&h('div',{role:'status',className:'alert success'},notice),
      pending&&h('div',{className:'alert pending',role:'status'},'Có yêu cầu chưa xác định kết quả. Giữ nguyên lựa chọn và thử lại cùng yêu cầu để tránh giữ vé lần hai.'),

      // 2. LIVE TICKETING & SEAT MAP SHOWCASE (#events)
      h('div',{id:'events',className:'workspace'},
        // CATALOG / DISCOVERY SECTION
        h('section',{'aria-labelledby':'catalog-heading',className:'catalog'},
          h('div',{className:'section-heading'},
            h('div',null,
              h('h2',{id:'catalog-heading'},'Sự kiện nổi bật'),
              h('p',{className:'section-sub'},'Lựa chọn sự kiện bạn yêu thích và bắt đầu trải nghiệm giữ vé tức thì.')
            ),
            h('button',{type:'button',className:'quiet',disabled:busy,onClick:()=>setCatalogVersion(x=>x+1)},'Tải lại sự kiện')
          ),

          // Search & Filter Bar
          events.length>0&&h('div',{className:'discovery-filter-bar'},
            h('div',{className:'search-box'},
              h('input',{
                type:'search',
                placeholder:'🔍 Tìm kiếm sự kiện, nghệ sĩ, địa điểm…',
                value:searchQuery,
                onChange:(e:ChangeEvent<HTMLInputElement>)=>setSearchQuery(e.target.value),
                className:'search-input'
              })
            ),
            h('div',{className:'category-tabs'},
              h('button',{
                type:'button',
                className:'tab-btn'+(categoryFilter==='ALL'?' active':''),
                onClick:()=>setCategoryFilter('ALL')
              },'Tất cả'),
              h('button',{
                type:'button',
                className:'tab-btn'+(categoryFilter==='GA'?' active':''),
                onClick:()=>setCategoryFilter('GA')
              },'🎟️ Vé GA đứng'),
              h('button',{
                type:'button',
                className:'tab-btn'+(categoryFilter==='SEATED'?' active':''),
                onClick:()=>setCategoryFilter('SEATED')
              },'💺 Ghế chỉ định'),
              h('select',{
                'aria-label':'Lọc theo khoảng giá',
                className:'tab-btn price-select',
                value:priceFilter,
                onChange:(e:ChangeEvent<HTMLSelectElement>)=>setPriceFilter(e.target.value as 'ALL'|'UNDER_500K'|'MID'|'OVER_1500K')
              },
                h('option',{value:'ALL'},'💰 Mọi mức giá'),
                h('option',{value:'UNDER_500K'},'Dưới 500.000đ'),
                h('option',{value:'MID'},'500.000đ – 1.500.000đ'),
                h('option',{value:'OVER_1500K'},'Trên 1.500.000đ')
              )
            )
          ),

          loading?h('p',{role:'status'},'Đang tải sự kiện…'):
          catalogFailed?h('p',null,'Không tải được danh sách. Bấm Tải lại sự kiện để thử lại.'):
          events.length===0?h('p',null,'Chưa có sự kiện đang mở.'):
          filteredEvents.length===0?h('p',{className:'muted'},'Không tìm thấy sự kiện phù hợp với tìm kiếm.'):
          h('div',{className:'event-grid'},...filteredEvents.map(event=>h('article',{key:event.id,className:'event-card'+(selected===event.id?' selected':'')},
            h('div',{className:'event-image-wrap'},
              h('img',{src:getEventPoster(event.title),alt:event.title,className:'event-poster',loading:'lazy'}),
              h('div',{className:'event-mark'},event.ticketTypes.some(t=>t.mode==='GA')?'🎟️ VÉ GA':'💺 GHẾ CHỈ ĐỊNH'),
              h('div',{className:'event-category-tag'},getEventCategory(event.title))
            ),
            h('div',{className:'event-card-body'},
              h('h3',null,event.title),
              h('p',{className:'event-venue'},'📍 ',event.venue),
              h('p',{className:'muted event-date'},'🗓️ ',date(event.startsAt)),
              h('div',{className:'event-price-line'},
                h('span',{className:'price-caption'},'Giá từ: '),
                h('strong',{className:'price-val'},vnd(Math.min(...event.ticketTypes.map(t=>t.priceVnd))))
              ),
              h('button',{
                type:'button',
                className:'event-btn'+(selected===event.id?' active':''),
                disabled:busy||!!pending,
                onClick:()=>{
                  setSelected(event.id);
                  setQuantity(1);
                  setSeatIds([]);
                  setError(null);
                  const panel = document.querySelector('.booking-panel');
                  if (panel && typeof panel.scrollIntoView === 'function') {
                    panel.scrollIntoView({behavior:'smooth',block:'start'});
                  }
                }
              },selected===event.id?'Đang xem':'Xem sự kiện')
            )
          )))
        ),

        // BOOKING ASIDE PANEL
        h('aside',{className:'booking-panel','aria-label':'Đặt vé'},
          h('p',{className:'eyebrow'},'LỰA CHỌN CỦA BẠN'),
          h('h2',null,detail?.title??'Chọn một sự kiện'),
          detail&&h('p',{className:'muted'},detail.description),
          detailLoading&&h('p',{role:'status'},'Đang tải chi tiết và tồn kho…'),
          detailFailed&&h('p',null,'Chưa tải được chi tiết. Chọn lại hoặc tải lại sự kiện.'),
          availability&&h('p',{className:'snapshot-note'},
            'Tồn kho tham khảo lúc '+date(availability.asOf),
            sseLive&&h('span',{className:'sse-live-badge'},' · 🟢 Đồng bộ trực tiếp (SSE)')
          ),
          availabilityStale&&h('p',{role:'status',className:'alert pending'},'Chưa cập nhật được tồn kho. Snapshot có thể đã cũ; máy chủ sẽ kiểm tra lại khi đặt.'),
          detail&&detail.ticketTypes.every(t=>!t.active)&&h('p',{className:'empty-state'},'Sự kiện chưa có loại vé đang mở.'),
          h('form',{onSubmit:submit},
            h('label',{htmlFor:'ticket-type'},'Loại vé'),
            h('select',{
              id:'ticket-type',
              value:typeId,
              disabled:busy||!!pending||!detail,
              onChange:(event:ChangeEvent<HTMLSelectElement>)=>{setTypeId(event.target.value);setSeatIds([]);}
            },
              h('option',{value:''},'Chọn loại vé'),
              ...(detail?.ticketTypes.filter(t=>t.active)??[]).map(t=>h('option',{key:t.id,value:t.id},t.name))
            ),
            type&&h('p',{className:'unit-price'},vnd(type.priceVnd),h('small',null,' / vé · giá từ máy chủ')),
            api.reminders&&h('div',{style:{margin:'6px 0 12px'}},
              h('button',{
                type:'button',
                className:'reminder-action-btn',
                disabled:reminderBusy,
                onClick:()=>void registerEventReminder(15)
              },reminderBusy?'Đang đăng ký...':'🔔 Nhắc tôi trước giờ mở bán (15 phút)'),
              reminderNotice&&h('p',{className:'reminder-toast'},'✅ ',reminderNotice)
            ),
            type?.mode==='SEATED'?h('div',{className:'seat-section'},
              h('div',{className:'stage-container'},
                h('div',{className:'stage-screen'},'🎭 SÂN KHẤU CHÍNH / STAGE'),
                h('p',{className:'stage-hint'},'Khu vực khán giả hướng về sân khấu')
              ),
              h('div',{className:'seat-legend-box'},
                h('span',{className:'legend-item'},h('span',{className:'dot dot-available'}),'🟢 Trống'),
                h('span',{className:'legend-item'},h('span',{className:'dot dot-chosen'}),'🟣 Đang chọn'),
                h('span',{className:'legend-item'},h('span',{className:'dot dot-held'}),'🟡 Đang giữ'),
                h('span',{className:'legend-item'},h('span',{className:'dot dot-sold'}),'⚪ Đã bán')
              ),
              api.v3?.bestSeats&&h('div',{className:'best-seats-bar'},
                h('span',{className:'best-seats-label'},'✨ Gợi ý cụm ghế liền kề:'),
                h('select',{
                  'aria-label':'Số ghế liền kề muốn gợi ý',
                  className:'best-seats-select',
                  value:String(bestSeatQty),
                  disabled:busy||!!pending||bestSeatBusy,
                  onChange:(e:ChangeEvent<HTMLSelectElement>)=>setBestSeatQty(Number(e.target.value))
                },
                  ...[2,3,4,5,6].map(n=>h('option',{key:n,value:String(n)},n+' ghế cạnh nhau'))
                ),
                h('button',{
                  type:'button',
                  className:'best-seats-btn',
                  disabled:busy||!!pending||bestSeatBusy,
                  onClick:()=>void pickBestContiguousSeats()
                },bestSeatBusy?'Đang tìm…':'Chọn cụm tốt nhất')
              ),
              bestSeatNote&&h('p',{role:'status',className:'best-seats-note'},'💡 ',bestSeatNote),
              h('div',{className:'seat-map','aria-label':'Sơ đồ ghế'},...seatMap.map(seat=>h('button',{
                key:seat.id,
                type:'button',
                'data-seat-id':seat.id,
                'aria-label':'Ghế '+seat.label+': '+(seat.status==='AVAILABLE'?'Trống':seat.status==='HELD'?'Đang giữ':'Không khả dụng'),
                'aria-pressed':seatIds.includes(seat.id),
                className:'seat '+seat.status.toLowerCase()+(seatIds.includes(seat.id)?' chosen':''),
                disabled:busy||!!pending||seat.status!=='AVAILABLE'||seatIds.length>=6&&!seatIds.includes(seat.id),
                onClick:()=>{
                  setSeatIds(current=>current.includes(seat.id)?current.filter(id=>id!==seat.id):[...current,seat.id]);
                  if(api.seats?.getView)void viewSeatPerspective(seat.id);
                }
              },
                h('strong',null,seat.label),
                h('small',null,seat.status==='AVAILABLE'?'Trống':seat.status==='HELD'?'Đang giữ':'Không khả dụng')
              ))),
              previewSeatView&&h('div',{className:'seat-preview-card'},
                h('div',{className:'seat-preview-header'},
                  h('h4',null,'👁️ Góc nhìn 3D từ ghế '+previewSeatView.seatLabel+' ('+previewSeatView.ticketTypeName+')'),
                  h('button',{type:'button',className:'seat-preview-close',onClick:()=>setPreviewSeatView(null)},'✕')
                ),
                h('div',{className:'seat-preview-svg-container',dangerouslySetInnerHTML:{__html:previewSeatView.perspectiveSvg}}),
                h('div',{className:'seat-preview-details'},
                  h('span',{className:'preview-badge score'},'⭐ Tầm nhìn: '+previewSeatView.viewQualityScore+'/100'),
                  h('span',{className:'preview-badge dist'},'📏 Cự ly: '+previewSeatView.distanceMeters+'m'),
                  h('span',{className:'preview-badge elev'},'📐 Độ dốc: '+previewSeatView.elevationAngle+'°')
                ),
                h('p',{className:'seat-preview-notes'},'💡 '+previewSeatView.viewNotes)
              ),
              h('p',{'aria-live':'polite',className:'selected-seats-count'},'Đã chọn '+seatIds.length+' ghế (Tối đa 6 ghế)')
            ):h('div',null,
              h('p',{'aria-live':'polite',className:available===0?'sold-out':'availability'},available===null?'Chưa có tồn kho tham khảo':available===0?'Hết vé':'Còn '+available+' vé tham khảo'),
              h('label',{htmlFor:'quantity'},'Số lượng vé'),
              h('input',{
                id:'quantity',
                type:'number',
                min:1,
                max:6,
                step:1,
                value:quantity,
                disabled:busy||!!pending,
                onChange:(event:ChangeEvent<HTMLInputElement>)=>setQuantity(Number(event.target.value))
              })
            ),
            h('small',{className:'muted'},'Tối đa 6 vé mỗi booking. Tồn kho được kiểm tra lại khi gửi.'),
            h('button',{
              type:'submit',
              className:'primary',
              disabled:busy||!canBook
            },busy?'Đang gửi…':pending?'Thử lại cùng yêu cầu':type?.mode==='SEATED'?'Giữ ghế đã chọn':'Giữ vé GA')
          ),
          api.v3?.joinWaitlist && selected && ((type?.mode==='GA' && available===0) || (type?.mode==='SEATED' && seatMap.length>0 && seatMap.every(s=>s.status!=='AVAILABLE'))) && h('div',{className:'smart-waitlist-card'},
            h('strong',null,'🔔 Sự kiện hiện đang hết vé hoặc đang được giữ hết'),
            h('p',{className:'muted'},'Đăng ký Smart Waitlist qua Kafka — hệ thống sẽ tự động ưu tiên thông báo cho bạn ngay khi có đơn hết hạn giữ chỗ nhả vé!'),
            h('button',{
              type:'button',
              className:'best-seats-btn',
              disabled:waitlistBusy,
              onClick:()=>void joinSmartWaitlist()
            },waitlistBusy?'Đang đăng ký…':'Đăng ký Danh sách chờ thông minh'),
            waitlistMsg && h('p',{role:'status',className:'best-seats-note'},waitlistMsg)
          ),
          api.resale?.listForEvent && selected && h('div',{className:'resale-marketplace-card',style:{background:'#f8fafc',border:'1px solid #cbd5e1',borderRadius:'16px',padding:'1.25rem',marginTop:'1.5rem'}},
            h('div',{style:{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:'0.75rem'}},
              h('div',null,
                h('span',{style:{background:'#e0e7ff',color:'#4338ca',padding:'3px 8px',borderRadius:'6px',fontSize:'0.75rem',fontWeight:700}},'🏷️ FAN-TO-FAN RESALE'),
                h('h4',{style:{margin:'4px 0 0',fontSize:'1.05rem',color:'#0f172a'}},'Chợ Vé Sang Nhượng Chính Chủ'),
                h('p',{className:'muted',style:{margin:0,fontSize:'0.82rem'}},'Bảo đảm 100% không vé giả: vé cũ bị hủy, cấp vé mới tức thì kèm mã QR ký số.')
              ),
              h('span',{style:{fontSize:'0.82rem',color:'#059669',fontWeight:600}},'🛡️ Cam kết ≤ Giá gốc')
            ),
            resaleLoading ? h('p',{className:'muted',style:{fontSize:'0.85rem'}},'Đang tải vé sang nhượng…') :
            resaleListings.length === 0 ? h('div',{style:{background:'#ffffff',padding:'1rem',borderRadius:'10px',textAlign:'center',color:'#64748b',fontSize:'0.85rem'}},
              'Hiện chưa có khán giả nào đăng bán lại vé cho sự kiện này.'
            ) : h('div',{style:{display:'flex',flexDirection:'column',gap:'8px'}},
              resaleListings.map(rsl => h('div',{
                key:rsl.id,
                style:{display:'flex',justifyContent:'space-between',alignItems:'center',background:'#ffffff',border:'1px solid #e2e8f0',borderRadius:'10px',padding:'10px 14px'}
              },
                h('div',null,
                  h('strong',{style:{fontSize:'0.9rem',color:'#1e293b'}},rsl.ticketTypeTitle + (rsl.seatLabel ? ' — Ghế ' + rsl.seatLabel : ' — Vé GA')),
                  h('div',{style:{fontSize:'0.78rem',color:'#64748b'}},'Bởi: ' + rsl.sellerName + ' • Giá gốc: ' + vnd(rsl.originalPriceVnd))
                ),
                h('div',{style:{display:'flex',alignItems:'center',gap:'10px'}},
                  h('div',{style:{textAlign:'right'}},
                    h('strong',{style:{color:'#4338ca',fontSize:'1rem'}},vnd(rsl.askingPriceVnd)),
                    rsl.askingPriceVnd < rsl.originalPriceVnd && h('span',{style:{display:'block',fontSize:'0.7rem',color:'#059669',fontWeight:700}},
                      'Tiết kiệm ' + Math.round((1 - rsl.askingPriceVnd / rsl.originalPriceVnd) * 100) + '%'
                    )
                  ),
                  h('button',{
                    type:'button',
                    className:'btn-primary',
                    style:{fontSize:'0.82rem',padding:'6px 12px'},
                    onClick:()=>void handleBuyResale(rsl)
                  },'Mua ngay')
                )
              ))
            )
          ),
          h('p',{className:'footnote'},'Thanh toán chỉ mô phỏng, không thu tiền thật. Ghế chỉ trống trở lại sau khi máy chủ nhả giữ chỗ.')
        )
      ),

      // BOOKING RESULT & CHECKOUT
      booking&&h('section',{id:'booking-result-section',className:'result','aria-label':'Kết quả booking'},
        h('div',{className:'section-heading'},
          h('h2',null,'Booking của bạn'),
          h('span',{className:'status-pill'},labels[booking.status]??booking.status)
        ),
        h('dl',null,
          h('dt',null,'Mã booking'),h('dd',{className:'booking-id'},booking.bookingId),
          h('dt',null,'Trạng thái máy chủ'),h('dd',null,booking.status),
          h('dt',null,'Tổng tiền máy chủ'),h('dd',null,vnd(booking.totalAmountVnd)),
          h('dt',null,'Hạn giữ chỗ'),h('dd',null,date(booking.expiresAt))
        ),
        booking.status==='PENDING_PAYMENT'&&h('p',{className:'hold-countdown','data-countdown':true},remaining>0?'Còn '+remaining+' giây giữ chỗ':'Đã đến hạn. Đang chờ máy chủ xác nhận và nhả vé.'),
        booking.status==='EXPIRED'&&h('p',{className:'alert pending'},'Máy chủ xác nhận giữ chỗ đã hết hạn. Booking này không có vé; bạn có thể chọn đặt lại theo tồn kho mới.'),
        h('div',{className:'booking-action-buttons'},
          h('button',{type:'button',className:'quiet',onClick:()=>void openBooking(booking.bookingId)},'Cập nhật booking'),
          h('button',{type:'button',className:'quiet',disabled:busy||!!pending,onClick:()=>{setCompleted('');setBooking(null);setNotice('');}},'Đặt thêm vé')
        ),
        booking.history&&h('ul',{className:'timeline'},...booking.history.map((item,i)=>h('li',{key:i},date(item.occurredAt)+' · '+item.toStatus))),
        h(Checkout,{key:booking.bookingId,api,store,booking,onChange:(value:Booking)=>{setBooking(value);void historyRefresh();if(selected)void api.availability(selected).then(setAvailability).catch(()=>{});}})
      ),

      // MY BOOKINGS & HISTORY
      h('section',{className:'history','aria-labelledby':'history-heading'},
        h('div',{className:'section-heading'},
          h('div',null,
            h('h2',{id:'history-heading'},'Lịch sử đặt vé của tôi'),
            h('p',{className:'section-sub'},'Toàn bộ đơn đặt vé đã thực hiện được đồng bộ trực tiếp từ máy chủ.')
          ),
          h('button',{type:'button',className:'quiet',onClick:()=>void historyRefresh()},'Tải lại lịch sử')
        ),
        historyLoading?h('p',{role:'status'},'Đang tải lịch sử…'):
        historyFailed?h('p',null,'Không tải được lịch sử. Dùng nút Tải lại lịch sử để thử lại.'):
        history.length===0?h('p',{className:'muted'},'Chưa có booking nào.'):
        h('ul',{className:'history-list'},...history.map(item=>h('li',{key:item.bookingId},
          h('button',{type:'button',onClick:()=>void openBooking(item.bookingId)},
            h('span',{className:'booking-id'},item.bookingId),
            h('span',{className:'booking-status-tag status-'+item.status.toLowerCase()},labels[item.status]??item.status),
            h('strong',null,vnd(item.totalAmountVnd))
          )
        )))
      ),

      // 3. CORE TECH HIGHLIGHTS (#features)
      h('section',{id:'features',className:'editorial-section pillars-section'},
        h('div',{className:'editorial-header text-center'},
          h('span',{className:'eyebrow-badge'},'🛡️ CÔNG NGHỆ VƯỢT TRỘI'),
          h('h2',null,'Giải Pháp Công Nghệ Độc Quyền Của EventFlow'),
          h('p',{className:'editorial-sub'},'Thiết kế kiến trúc hướng sự kiện (EDA) và ranh giới Transaction chuẩn ACID giải quyết triệt để vấn đề tranh chấp vé lưu lượng lớn.')
        ),
        h('div',{className:'pillars-grid'},
          h('div',{className:'pillar-card'},
            h('div',{className:'pillar-icon'},'🛡️'),
            h('h3',null,'Khóa Hàng Nguyên Tử (Row-level Locking)'),
            h('p',null,'Khóa cơ sở dữ liệu PostgreSQL ở mức hàng (Pessimistic Row Lock) đảm bảo một ghế hoặc vé GA chỉ thuộc về một khách hàng duy nhất. Loại bỏ 100% tình trạng bán vượt sức chứa (Zero Oversell).')
          ),
          h('div',{className:'pillar-card'},
            h('div',{className:'pillar-icon'},'⚡'),
            h('h3',null,'Kiến Trúc Hướng Sự Kiện (Kafka & Outbox)'),
            h('p',null,'Áp dụng mẫu Transactional Outbox ghi nhận sự kiện ngay trong transaction tạo vé, sau đó phân phối qua Apache Kafka. Đảm bảo tính nhất quán tuyệt đối giữa thanh toán và xuất vé.')
          ),
          h('div',{className:'pillar-card'},
            h('div',{className:'pillar-icon'},'⏱️'),
            h('h3',null,'Tự Động Thu Hồi Chỗ Quá Hạn (Smart Expiry)'),
            h('p',null,'Worker quét độc lập kiểm tra thời gian hết hạn giữ chỗ theo đồng hồ cơ sở dữ liệu. Nhả tài nguyên đã quá hạn an toàn, không block luồng mua vé của khách hàng mới.')
          ),
          h('div',{className:'pillar-card'},
            h('div',{className:'pillar-icon'},'🎟️'),
            h('h3',null,'Vé Điện Tử Định Danh (Secure E-Ticket)'),
            h('p',null,'Mỗi vé phát hành mang mã code duy nhất và vị trí ghế chính xác, đi kèm mã QR SVG mã hóa sẵn sàng cho hệ thống cổng quét soát vé tốc độ cao tại sự kiện.')
          )
        ),
        h('div',{style:{textAlign:'center',marginTop:'2rem'}},
          h('button',{
            type:'button',
            className:'hero-cta-secondary',
            onClick:()=>nav('/giai-phap')
          },'Tìm hiểu giải pháp công nghệ chuyên sâu theo ngành →')
        )
      ),

      // 4. COMMERCIAL PRICING PLANS (#pricing)
      h('section',{id:'pricing',className:'editorial-section pricing-section'},
        h('div',{className:'editorial-header text-center'},
          h('span',{className:'eyebrow-badge'},'💎 BẢNG GIÁ DỊCH VỤ MINH BẠCH'),
          h('h2',null,'Giải Pháp Phân Phối Vé Dành Cho Nhà Tổ Chức'),
          h('p',{className:'editorial-sub'},'Linh hoạt theo quy mô và mô hình sự kiện. Cam kết không phí ẩn, hỗ trợ kỹ thuật chuyên sâu.')
        ),
        h('div',{className:'pricing-grid'},
          // Plan 1: Starter
          h('div',{className:'pricing-card'},
            h('div',{className:'plan-header'},
              h('h3',null,'Gói Khởi Nghiệp (Starter)'),
              h('p',{className:'plan-desc'},'Phù hợp hội thảo, talkshow, sự kiện vừa và nhỏ.'),
              h('div',{className:'plan-price'},
                h('span',{className:'price-num'},'4.5%'),
                h('span',{className:'price-unit'},'/ vé bán ra')
              )
            ),
            h('ul',{className:'plan-features'},
              h('li',null,'✓ Quy mô dưới 500 khách'),
              h('li',null,'✓ Phân phối vé GA không chỉ định chỗ'),
              h('li',null,'✓ Báo cáo doanh thu & tồn kho Realtime'),
              h('li',null,'✓ Cổng soát vé ứng dụng điện thoại'),
              h('li',null,'✓ Hỗ trợ kỹ thuật qua email & chat')
            ),
            h('button',{
              type:'button',
              className:'pricing-btn',
              onClick:()=>nav('/lien-he')
            },'Chọn gói Khởi Nghiệp')
          ),

          // Plan 2: Professional (Featured)
          h('div',{className:'pricing-card featured'},
            h('div',{className:'popular-tag'},'🔥 PHỔ BIẾN NHẤT'),
            h('div',{className:'plan-header'},
              h('h3',null,'Gói Chuyên Nghiệp (Pro)'),
              h('p',{className:'plan-desc'},'Dành cho Concert, Lễ hội âm nhạc & Đêm diễn lớn.'),
              h('div',{className:'plan-price'},
                h('span',{className:'price-num'},'3.2%'),
                h('span',{className:'price-unit'},'/ vé bán ra')
              )
            ),
            h('ul',{className:'plan-features'},
              h('li',null,'✓ Quy mô từ 1.000 đến 10.000+ khách'),
              h('li',null,'✓ Sơ đồ ghế tương tác đa tầng chuẩn Rạp hát'),
              h('li',null,'✓ Cam kết Zero Oversell chống bán lố 100%'),
              h('li',null,'✓ Xử lý tải cao 10.000 req/s lúc mở bán'),
              h('li',null,'✓ Xuất vé điện tử QR tốc độ < 0.1s'),
              h('li',null,'✓ Quản lý check-in nhiều cổng đồng thời'),
              h('li',null,'✓ Chuyên viên kỹ thuật trực tuyến 24/7')
            ),
            h('button',{
              type:'button',
              className:'pricing-btn primary',
              onClick:()=>nav('/lien-he')
            },'Đăng ký gói Chuyên Nghiệp')
          ),

          // Plan 3: Enterprise
          h('div',{className:'pricing-card'},
            h('div',{className:'plan-header'},
              h('h3',null,'Gói Doanh Nghiệp (Enterprise)'),
              h('p',{className:'plan-desc'},'Sự kiện quy mô Quốc gia, SVĐ lớn > 20.000 khán giả.'),
              h('div',{className:'plan-price'},
                h('span',{className:'price-num'},'Liên hệ'),
                h('span',{className:'price-unit'},'báo giá riêng')
              )
            ),
            h('ul',{className:'plan-features'},
              h('li',null,'✓ Hạ tầng phân tán dedicated riêng biệt'),
              h('li',null,'✓ Tùy biến thương hiệu & tên miền riêng 100%'),
              h('li',null,'✓ Đội ngũ kỹ sư hỗ trợ trực tiếp On-site'),
              h('li',null,'✓ Tích hợp hệ thống kiểm soát vé Barrier cứng'),
              h('li',null,'✓ Cam kết SLA 99.99% độ sẵn sàng hệ thống'),
              h('li',null,'✓ Hợp đồng bảo mật & bảo hiểm giao dịch')
            ),
            h('button',{
              type:'button',
              className:'pricing-btn',
              onClick:()=>nav('/lien-he')
            },'Liên hệ tư vấn VIP')
          )
        ),
        h('div',{style:{textAlign:'center',marginTop:'2.5rem'}},
          h('button',{
            type:'button',
            className:'hero-cta-secondary',
            onClick:()=>nav('/bang-gia')
          },'Xem bảng so sánh chi tiết 12 tiêu chí tính năng giữa các gói →')
        )
      ),

      // 5. PARTNER & TRUST BADGES
      h('section',{className:'trust-badges-section'},
        h('div',{className:'trust-badges-wrap'},
          h('span',{className:'trust-badge-item'},'🏛️ PostgreSQL ACID Certified'),
          h('span',{className:'trust-badge-item'},'⚡ Apache Kafka 4.2 EDA'),
          h('span',{className:'trust-badge-item'},'🔒 256-Bit SSL/TLS Security'),
          h('span',{className:'trust-badge-item'},'📋 OpenAPI 3.1 Contract Tested'),
          h('span',{className:'trust-badge-item'},'🎟️ Anti-Fraud QR Check-in')
        )
      ),

      // 6. CONSULTATION & CONTACT FORM (#contact)
      h('section',{id:'contact',className:'editorial-section contact-section'},
        h('div',{className:'contact-container'},
          h('div',{className:'contact-intro'},
            h('span',{className:'eyebrow-badge'},'HỢP TÁC TỔ CHỨC SỰ KIỆN'),
            h('h2',null,'Tư Vấn Giải Pháp Phân Phối Vé Toàn Diện'),
            h('p',null,'Bạn là đơn vị tổ chức biểu diễn, doanh nghiệp hay nhà sản xuất âm nhạc? Hãy để lại thông tin, đội ngũ chuyên gia EventFlow sẽ liên hệ trong vòng 15 phút để tư vấn giải pháp tối ưu nhất.'),
            h('div',{className:'contact-highlights'},
              h('div',{className:'c-item'},h('strong',null,'⚡ Triển khai thần tốc:'),h('span',null,' Mở bán vé chỉ sau 2 giờ ký kết.')),
              h('div',{className:'c-item'},h('strong',null,'🎯 Đối soát minh bạch:'),h('span',null,' Báo cáo dòng tiền tức thời theo giao dịch.')),
              h('div',{className:'c-item'},h('strong',null,'📞 Hotline hỗ trợ:'),h('span',null,' 1900 8888 (24/7 kể cả ngày lễ).'))
            )
          ),
          h('div',{className:'contact-form-wrap'},
            contactSubmitted?h('div',{className:'contact-success-box'},
              h('div',{className:'success-icon'},'🎉'),
              h('h3',null,'Cảm ơn Quý đối tác!'),
              h('p',null,'Chúng tôi đã tiếp nhận yêu cầu tư vấn sự kiện của bạn. Chuyên viên EventFlow sẽ liên lạc qua số điện thoại '+contactForm.phone+' trong vòng 15 phút để trao đổi chi tiết và gửi bản đề xuất.'),
              h('button',{
                type:'button',
                className:'quiet',
                onClick:()=>setContactSubmitted(false)
              },'Gửi yêu cầu sự kiện khác')
            ):h('form',{
              className:'commercial-contact-form',
              onSubmit:async(e)=>{
                e.preventDefault();
                setContactLoading(true);
                setContactError(null);
                try{
                  const emailToSubmit=contactForm.email.trim()||('lead.'+contactForm.phone.replace(/\D/g,'')+'@contact.eventflow.vn');
                  if(api.contact&&typeof api.contact.submitLead==='function'){
                    await api.contact.submitLead({
                      fullName:contactForm.fullName.trim(),
                      email:emailToSubmit,
                      phone:contactForm.phone.trim(),
                      companyName:contactForm.orgName.trim(),
                      eventScale:contactForm.eventType,
                      message:contactForm.message.trim()
                    });
                  }
                  setContactSubmitted(true);
                }catch(err:any){
                  setContactError(err?.message||'Không thể gửi thông tin liên hệ. Vui lòng thử lại.');
                }finally{
                  setContactLoading(false);
                }
              }
            },
              h('h3',null,'Đăng Ký Tư Vấn Miễn Phí'),
              contactError&&h('div',{className:'contact-error-alert',role:'alert'},'⚠️ '+contactError),
              h('div',{className:'form-row'},
                h('div',{className:'form-group'},
                  h('label',{htmlFor:'c-name'},'Họ và tên người liên hệ *'),
                  h('input',{
                    id:'c-name',
                    type:'text',
                    required:true,
                    placeholder:'Nguyễn Văn A',
                    value:contactForm.fullName,
                    onChange:(e:ChangeEvent<HTMLInputElement>)=>setContactForm(prev=>({...prev,fullName:e.target.value}))
                  })
                ),
                h('div',{className:'form-group'},
                  h('label',{htmlFor:'c-phone'},'Số điện thoại *'),
                  h('input',{
                    id:'c-phone',
                    type:'tel',
                    required:true,
                    placeholder:'0901 234 567',
                    value:contactForm.phone,
                    onChange:(e:ChangeEvent<HTMLInputElement>)=>setContactForm(prev=>({...prev,phone:e.target.value}))
                  })
                )
              ),
              h('div',{className:'form-group'},
                h('label',{htmlFor:'c-email'},'Email liên hệ *'),
                h('input',{
                  id:'c-email',
                  type:'email',
                  required:true,
                  placeholder:'contact@company.com',
                  value:contactForm.email,
                  onChange:(e:ChangeEvent<HTMLInputElement>)=>setContactForm(prev=>({...prev,email:e.target.value}))
                })
              ),
              h('div',{className:'form-group'},
                h('label',{htmlFor:'c-org'},'Tên doanh nghiệp / Đơn vị tổ chức *'),
                h('input',{
                  id:'c-org',
                  type:'text',
                  required:true,
                  placeholder:'Công ty Cổ phần Giải trí ABC',
                  value:contactForm.orgName,
                  onChange:(e:ChangeEvent<HTMLInputElement>)=>setContactForm(prev=>({...prev,orgName:e.target.value}))
                })
              ),
              h('div',{className:'form-group'},
                h('label',{htmlFor:'c-type'},'Loại hình sự kiện *'),
                h('select',{
                  id:'c-type',
                  value:contactForm.eventType,
                  onChange:(e:ChangeEvent<HTMLSelectElement>)=>setContactForm(prev=>({...prev,eventType:e.target.value}))
                },
                  h('option',{value:'CONCERT'},'Live Concert & Đại nhạc hội'),
                  h('option',{value:'THEATER'},'Nhà hát & Đêm nhạc Thính phòng'),
                  h('option',{value:'CONFERENCE'},'Hội nghị, Diễn đàn & Triển lãm Công nghệ'),
                  h('option',{value:'SPORTS'},'Giải thi đấu Thể thao'),
                  h('option',{value:'OTHER'},'Loại hình sự kiện khác')
                )
              ),
              h('div',{className:'form-group'},
                h('label',{htmlFor:'c-msg'},'Quy mô dự kiến & Lời nhắn thêm'),
                h('textarea',{
                  id:'c-msg',
                  rows:3,
                  placeholder:'Ví dụ: Dự kiến 3.000 khách vào tháng 12/2026 tại Hà Nội, cần sơ đồ ghế VIP...',
                  value:contactForm.message,
                  onChange:(e:ChangeEvent<HTMLTextAreaElement>)=>setContactForm(prev=>({...prev,message:e.target.value}))
                })
              ),
              h('button',{type:'submit',className:'primary contact-submit-btn',disabled:contactLoading},contactLoading?'Đang gửi yêu cầu...':'Gửi Yêu Cầu Tư Vấn Ngay')
            )
          )
        )
      ),

      // 7. FAQ ACCORDION
      h('section',{className:'editorial-section faq-section'},
        h('div',{className:'editorial-header text-center'},
          h('span',{className:'eyebrow-badge'},'HỎI ĐÁP & HỖ TRỢ'),
          h('h2',null,'Những câu hỏi thường gặp về EventFlow'),
          h('p',{className:'editorial-sub'},'Giải đáp rõ ràng mọi thắc mắc về cơ chế giữ vé, thanh toán và bảo đảm an toàn.')
        ),
        h('div',{className:'faq-accordion'},
          [
            {q:'Cơ chế giữ chỗ 10 phút hoạt động như thế nào?',a:'Khi bạn bấm "Giữ vé GA" hoặc "Giữ ghế đã chọn", server sẽ tạo một transaction ghi nhận trạng thái PENDING_PAYMENT và khóa tài nguyên trên cơ sở dữ liệu. Bạn có đúng 10 phút để hoàn tất thanh toán trước khi worker tự động thu hồi và mở lại cho khán giả khác.'},
            {q:'Nếu thanh toán mô phỏng sau khi hết hạn giữ chỗ thì sao?',a:'Hệ thống áp dụng state machine nghiêm ngặt: nếu kết quả thanh toán đến muộn sau khi vé đã hết hạn (EXPIRED), hệ thống chuyển sang REFUND_REQUIRED (nghĩa vụ hoàn tiền) và tuyệt đối không phát vé khống, bảo vệ tính toàn vẹn 100% của khán phòng.'},
            {q:'Tôi có thể đặt tối đa bao nhiêu vé trong một lần?',a:'Nhằm đảm bảo tính công bằng và ngăn chặn hiện tượng gom vé tự động, mỗi phiên đặt vé giới hạn tối đa 6 vé (cho cả hình thức GA và Ghế ngồi chỉ định).'},
            {q:'Tôi có thể xem lại vé đã mua ở đâu?',a:'Bạn có thể xem lại toàn bộ các booking đã hoàn tất ở mục "Lịch sử đặt vé của tôi" phía trên, bấm vào mã booking để mở lại thẻ vé điện tử E-Ticket với mã QR check-in bất cứ lúc nào.'},
            {q:'Hệ thống đảm bảo không bao giờ bị oversell bằng cách nào?',a:'EventFlow sử dụng PostgreSQL với ràng buộc khóa hàng (Row-level Locking) và điều kiện kiểm tra nguyên tử (Atomic Check-and-Update) tại tầng dữ liệu, thay vì phụ thuộc vào bộ nhớ tạm. Đây là giải pháp tiêu chuẩn ngân hàng cho các bài toán phân phối tài nguyên giới hạn.'}
          ].map((item,idx)=>h('div',{key:idx,className:'faq-item'+(faqOpen===idx?' open':'')},
            h('button',{
              type:'button',
              className:'faq-question-btn',
              'aria-expanded':faqOpen===idx,
              onClick:()=>setFaqOpen(faqOpen===idx?null:idx)
            },
              h('span',{className:'faq-q-text'},item.q),
              h('span',{className:'faq-icon'},faqOpen===idx?'−':'+')
            ),
            faqOpen===idx&&h('div',{className:'faq-answer'},h('p',null,item.a))
          ))
        )
      ),

      // 8. CORPORATE FOOTER
      h('footer',{className:'site-footer corporate-footer'},
        h('div',{className:'footer-grid'},
          h('div',{className:'footer-brand-col'},
            h('div',{className:'brand footer-brand'},'EventFlow'),
            h('p',{className:'footer-desc'},'EventFlow Corporation — Nền tảng công nghệ quản trị sự kiện & phân phối vé trực tuyến đa hình thức tốc độ cao. Đảm bảo tính nhất quán dữ liệu, chống oversell và phát hành vé điện tử tức thì.'),
            h('div',{className:'footer-compliance'},'Đồ án tốt nghiệp Kỹ thuật Phần mềm · Modular Monolith & EDA')
          ),
          h('div',{className:'footer-col'},
            h('h4',null,'SẢN PHẨM & GIẢI PHÁP'),
            h('ul',null,
              h('li',null,h('a',{href:'#events',onClick:(e)=>{e.preventDefault();scrollToSection('events');}},'Đặt vé GA & Ghế chỉ định')),
              h('li',null,h('a',{href:'#features',onClick:(e)=>{e.preventDefault();scrollToSection('features');}},'Kiến trúc chống bán lố (Zero Oversell)')),
              h('li',null,h('a',{href:'#pricing',onClick:(e)=>{e.preventDefault();scrollToSection('pricing');}},'Bảng giá dịch vụ cho Nhà tổ chức')),
              h('li',null,h('a',{href:'#contact',onClick:(e)=>{e.preventDefault();scrollToSection('contact');}},'Đăng ký đối tác phân phối'))
            )
          ),
          h('div',{className:'footer-col'},
            h('h4',null,'VĂN PHÒNG & LIÊN HỆ'),
            h('ul',null,
              h('li',null,'📍 Trụ sở chính: Tầng 12, Tòa nhà Công nghệ, Cầu Giấy, Hà Nội'),
              h('li',null,'📍 Chi nhánh: Tòa nhà Bitexco, Quận 1, TP. Hồ Chí Minh'),
              h('li',null,'📞 Hotline: 1900 8888 (24/7)'),
              h('li',null,'✉️ Email: contact@eventflow.vn')
            )
          ),
          h('div',{className:'footer-col'},
            h('h4',null,'CHÍNH SÁCH & BẢO MẬT'),
            h('ul',null,
              h('li',null,'Chính sách giữ vé 10 phút'),
              h('li',null,'Quy chế xử lý bù trừ hoàn tiền'),
              h('li',null,'Bảo mật thông tin giao dịch SSL'),
              h('li',null,'Demo Profile: Không thu tiền thật')
            )
          )
        ),
        h('div',{className:'footer-bottom'},
          h('p',null,'© 2026 EventFlow Technology Platform. Bản quyền thuộc về Đồ án Tốt nghiệp Kỹ thuật Phần mềm.'),
          h('p',{className:'footer-note'},'Hệ thống mẫu chuẩn hóa kỹ thuật theo tài liệu PRD & Architecture.')
        )
      )
    ),

    activeResalePurchase && h('div',{className:'auth-modal-backdrop',onClick:(e)=>{if(e.target===e.currentTarget)setActiveResalePurchase(null);}},
      h('div',{className:'auth-modal-card',style:{maxWidth:'500px'}},
        h('div',{className:'auth-modal-header'},
          h('div',null,
            h('h3',{style:{margin:0,fontSize:'1.15rem',color:'#0f172a'}},'🏷️ Mua vé sang nhượng chính chủ'),
            h('p',{className:'muted',style:{margin:'4px 0 0',fontSize:'0.82rem'}},detail?.title)
          ),
          h('button',{type:'button',className:'auth-close-btn',onClick:()=>setActiveResalePurchase(null)},'×')
        ),
        resaleActionError && h('div',{className:'auth-error-alert'},'⚠️ ',resaleActionError),
        resaleSettleSuccess ? h('div',{style:{padding:'16px 0',display:'flex',flexDirection:'column',gap:'12px'}},
          h('div',{style:{background:'#ecfdf5',border:'1px solid #a7f3d0',borderRadius:'10px',padding:'14px',color:'#065f46'}},
            h('strong',{style:{fontSize:'1rem'}},'🎉 ' + resaleSettleSuccess.message),
            h('p',{style:{margin:'8px 0 0',fontSize:'0.85rem'}},
              'Vé mới đã được phát hành và lưu trực tiếp vào Ví vé điện tử của bạn. Mã vé: '
            ),
            h('div',{style:{background:'#ffffff',padding:'8px 12px',borderRadius:'8px',border:'1px dashed #059669',marginTop:'8px',display:'inline-block'}},
              h('strong',{style:{color:'#047857',letterSpacing:'1px'}},resaleSettleSuccess.newTicketCode)
            )
          ),
          h('div',{style:{display:'flex',gap:'8px',justifyContent:'flex-end',marginTop:'8px'}},
            h('button',{type:'button',className:'hero-cta-coral',onClick:()=>{setActiveResalePurchase(null);nav('/ve-cua-toi');}},'🎟️ Vào Ví vé của tôi →')
          )
        ) : h('div',{style:{display:'flex',flexDirection:'column',gap:'12px',marginTop:'12px'}},
          h('div',{style:{background:'#f8fafc',borderRadius:'10px',border:'1px solid #e2e8f0',padding:'12px'}},
            h('div',{style:{display:'flex',justifyContent:'space-between',marginBottom:'6px'}},
              h('span',{className:'muted',style:{fontSize:'0.85rem'}},'Hạng vé / Vị trí:'),
              h('strong',{style:{fontSize:'0.85rem'}},activeResalePurchase.ticketTypeTitle + (activeResalePurchase.seatLabel ? ' (Ghế ' + activeResalePurchase.seatLabel + ')' : ' (Vé GA)'))
            ),
            h('div',{style:{display:'flex',justifyContent:'space-between',marginBottom:'6px'}},
              h('span',{className:'muted',style:{fontSize:'0.85rem'}},'Người đăng bán:'),
              h('span',{style:{fontSize:'0.85rem'}},activeResalePurchase.sellerName)
            ),
            h('div',{style:{display:'flex',justifyContent:'space-between',marginBottom:'6px'}},
              h('span',{className:'muted',style:{fontSize:'0.85rem'}},'Giá gốc:'),
              h('span',{style:{textDecoration:'line-through',color:'#94a3b8',fontSize:'0.85rem'}},vnd(activeResalePurchase.originalPriceVnd))
            ),
            h('div',{style:{display:'flex',justifyContent:'space-between',borderTop:'1px solid #e2e8f0',paddingTop:'6px',marginTop:'6px'}},
              h('strong',{style:{fontSize:'0.95rem',color:'#0f172a'}},'Giá thanh toán:'),
              h('strong',{style:{fontSize:'1.1rem',color:'#4338ca'}},vnd(activeResalePurchase.askingPriceVnd))
            )
          ),
          h('div',{style:{background:'#eff6ff',border:'1px solid #bfdbfe',borderRadius:'8px',padding:'10px',fontSize:'0.78rem',color:'#1e40af'}},
            '🛡️ Cơ chế bảo mật EventFlow: Khi bạn xác nhận, vé cũ của người bán sẽ lập tức bị HỦY (REVOKED) và hệ thống cấp mã vé mới mang tên bạn kèm mã QR ký số HMAC-SHA256.'
          ),
          h('div',{style:{display:'flex',gap:'8px',justifyContent:'flex-end',marginTop:'8px'}},
            h('button',{type:'button',className:'quiet',disabled:resaleActionBusy,onClick:()=>setActiveResalePurchase(null)},'Hủy'),
            h('button',{
              type:'button',
              className:'hero-cta-coral',
              disabled:resaleActionBusy,
              onClick:()=>void handleConfirmResalePayment()
            },resaleActionBusy ? 'Đang giao dịch…' : 'Xác nhận thanh toán ' + vnd(activeResalePurchase.askingPriceVnd))
          )
        )
      )
    )
  );
}
