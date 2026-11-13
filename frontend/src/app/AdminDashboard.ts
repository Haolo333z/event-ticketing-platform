import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent} from 'react';
import {ApiError} from '../api/client.ts';
import type {Booking,Status} from '../api/client.ts';
import type {AdminApi,AdminEvent,AdminInventory,CreateEvent,PatchEvent,Page,Outbox} from '../api/admin.ts';
import {EventEditor} from './EventEditor.ts';

type Tab='events'|'inventory'|'bookings'|'outbox';
const states:Status[]=['PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED'];
const message=(value:unknown)=>value instanceof ApiError?value.message+' · '+value.code+(value.correlationId?' · Mã tra cứu: '+value.correlationId:''):'Không tải được dữ liệu. Hãy thử lại.';
export function AdminDashboard({api}:{api:AdminApi}){
  const[tab,setTab]=useState<Tab>('events'),[page,setPage]=useState(0),[filter,setFilter]=useState<Status|''>(''),[revision,setRevision]=useState(0);
  const[data,setData]=useState<Page<AdminEvent>|Page<Booking>|Page<Outbox>|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState('');
  const[selected,setSelected]=useState<AdminEvent|null>(null),[inventory,setInventory]=useState<AdminInventory|null>(null),[invError,setInvError]=useState('');
  const[inventoryRevision,setInventoryRevision]=useState(0);
  const[editor,setEditor]=useState<'create'|'edit'|null>(null),[detail,setDetail]=useState<Booking|null>(null),[notice,setNotice]=useState('');
  const[uncertain,setUncertain]=useState(false),[saving,setSaving]=useState(false);const savingRef=useRef(false),detailRequest=useRef(0);
  const editorPanel=useRef<HTMLElement>(null);
  function revealEditor(){
    const panel=editorPanel.current;if(!panel)return;
    panel.scrollIntoView?.({behavior:'smooth',block:'start'});
    const firstField=panel.querySelector<HTMLInputElement>('#admin-title');
    (firstField??panel).focus({preventScroll:true});
  }
  useEffect(()=>{if(editor)revealEditor();},[editor,editor==='edit'?inventory:null,inventoryRevision]);
  useEffect(()=>{
    if(tab==='inventory'){setLoading(false);return;}let active=true;setLoading(true);setData(null);setError('');
    const request=tab==='events'?api.events(page,20):tab==='bookings'?api.bookings(page,20,filter||undefined):api.outbox(page,20);
    request.then(result=>{if(active)setData(result);}).catch(value=>{if(active)setError(message(value));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[api,tab,page,filter,revision]);
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
  const table=(caption:string,columns:string[],rows:ReturnType<typeof h>[])=>h('div',{className:'table-scroll'},h('table',null,h('caption',null,caption),h('thead',null,h('tr',null,...columns.map(c=>h('th',{key:c,scope:'col'},c)))),h('tbody',null,...rows)));
  const empty=!loading&&!error&&data?.items.length===0;
  return h('main',{className:'admin-dashboard shell'},
    h('section',{className:'intro'},h('p',{className:'eyebrow'},'QUẢN TRỊ DEMO'),h('h1',null,'Sự kiện và vận hành'),h('p',null,'Dữ liệu từ API. Máy chủ kiểm tra quyền, giá và tồn kho cho mọi thay đổi.')),
    h('nav',{'aria-label':'Mục quản trị',className:'admin-tabs'},...([['events','Sự kiện'],['inventory','Tồn kho'],['bookings','Booking'],['outbox','Outbox']] as const).map(([key,label])=>h('button',{key,type:'button','aria-pressed':tab===key,disabled:saving,onClick:()=>switchTab(key)},label))),
    error&&h('p',{role:'alert',className:'alert error'},error),notice&&h('p',{role:'status',className:'alert success'},notice),
    uncertain&&h('div',{role:'alert',className:'alert pending'},h('p',null,'Chưa rõ kết quả tạo sự kiện. Không gửi lại tự động. Tải và kiểm tra danh sách trước khi tạo tiếp.'),h('button',{type:'button',disabled:loading,onClick:()=>void reviewCreate()},'Tải danh sách để kiểm tra')),
    h('div',{className:'section-heading'},h('h2',null,{events:'Danh sách sự kiện',inventory:'Tồn kho theo sự kiện',bookings:'Danh sách booking',outbox:'Sự kiện outbox'}[tab]),h('button',{type:'button',className:'quiet',disabled:loading||saving,onClick:()=>setRevision(x=>x+1)},'Làm mới dữ liệu')),
    tab==='bookings'&&h('div',{className:'filter-field'},h('label',{htmlFor:'booking-status-filter'},'Lọc trạng thái booking'),h('select',{id:'booking-status-filter',value:filter,onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setFilter(e.target.value as Status|'');setPage(0);setDetail(null);detailRequest.current++;}},h('option',{value:''},'Tất cả trạng thái'),...states.map(s=>h('option',{key:s,value:s},s)))),
    tab==='events'&&h('button',{type:'button',className:'primary',disabled:saving||uncertain,'aria-expanded':editor==='create','aria-controls':'event-editor-panel',onClick:()=>{setEditor('create');setError('');if(editor==='create')revealEditor();}},'Tạo sự kiện mới'),
    editor&&h('section',{id:'event-editor-panel',ref:editorPanel,className:'editor-panel',tabIndex:-1,'aria-labelledby':'event-editor-heading'},
      h('div',{className:'section-heading'},h('h2',{id:'event-editor-heading'},editor==='create'?'Tạo sự kiện':'Sửa sự kiện'),h('button',{type:'button',disabled:saving,onClick:()=>setEditor(null)},'Đóng biểu mẫu')),
      editor==='edit'&&invError?h('p',{role:'alert'},invError):editor==='edit'&&!inventory?h('p',{role:'status'},'Đang tải cấu hình tồn kho…'):h(EventEditor,{key:editor==='create'?'new':selected!.id+':'+revision,event:editor==='create'?null:selected,inventory,blocked:uncertain,onSave:save,onCancel:()=>setEditor(null)})),
    loading&&h('p',{role:'status'},'Đang tải dữ liệu quản trị…'),empty&&h('p',{className:'empty-state'},'Không có dữ liệu cho trang hoặc bộ lọc này.'),
    !loading&&!error&&data&&tab==='events'&&table('Sự kiện gồm bản nháp, đang công bố và đã đóng',['Sự kiện','Trạng thái','Loại vé','Thao tác'],(data.items as AdminEvent[]).map(event=>h('tr',{key:event.id},h('td',null,h('strong',null,event.title),h('small',{className:'muted'},event.venue)),h('td',null,event.status),h('td',null,event.ticketTypes.map(t=>t.name+' · '+t.mode).join(', ')),h('td',{className:'actions'},h('button',{type:'button',onClick:()=>choose(event,false)},'Tồn kho '+event.title),h('button',{type:'button',disabled:uncertain,onClick:()=>choose(event,true)},'Sửa '+event.title))))),
    tab==='inventory'&&(!selected?h('p',{className:'empty-state'},'Chọn sự kiện ở mục Sự kiện để xem tồn kho.'):h('section',{'aria-label':'Chi tiết tồn kho'},h('h3',null,selected.title),invError?h('p',{role:'alert',className:'alert error'},invError):!inventory?h('p',{role:'status'},'Đang tải tồn kho…'):h('div',null,h('p',{className:'muted'},'Snapshot lúc '+new Date(inventory.asOf).toLocaleString('vi-VN')+'. Làm mới để xem thay đổi; đây không phải thao tác giữ vé.'),
      ...inventory.ticketTypes.map(stock=>h('section',{key:stock.ticketTypeId,className:'inventory-type'},h('h3',null,selected.ticketTypes.find(t=>t.id===stock.ticketTypeId)?.name??stock.ticketTypeId),stock.mode==='GA'?table('Tồn kho GA',['Sức chứa','Đang giữ','Đã bán','Còn trống'],[h('tr',{key:stock.ticketTypeId},h('td',null,stock.capacity),h('td',null,stock.held),h('td',null,stock.sold),h('td',null,stock.available))]):h('div',null,h('p',null,'Ghế chỉ định · '+stock.seats.length+' ghế'),h('ul',{className:'admin-seat-list'},...stock.seats.map(seat=>h('li',{key:seat.id,className:seat.status.toLowerCase()},h('strong',null,seat.label),' · '+seat.status))))))))),
    !loading&&!error&&data&&tab==='bookings'&&table('Booking theo trạng thái đã chọn',['Mã booking','Trạng thái','Tổng tiền','Hạn giữ','Chi tiết'],(data.items as Booking[]).map(b=>h('tr',{key:b.bookingId},h('td',{className:'booking-id'},b.bookingId),h('td',null,b.status),h('td',null,b.totalAmountVnd.toLocaleString('vi-VN')+' ₫'),h('td',null,new Date(b.expiresAt).toLocaleString('vi-VN')),h('td',null,h('button',{type:'button',onClick:()=>void openBooking(b.bookingId)},'Xem '+b.bookingId))))),
    detail&&tab==='bookings'&&h('section',{className:'result','aria-label':'Chi tiết booking quản trị'},h('h3',null,detail.bookingId),h('p',null,detail.status),h('ul',{className:'timeline'},...(detail.history??[]).map((item,i)=>h('li',{key:i},item.toStatus+' · '+new Date(item.occurredAt).toLocaleString('vi-VN'))))),
    !loading&&!error&&data&&tab==='outbox'&&table('Outbox sau commit nghiệp vụ',['Event ID / loại','Booking','Version','Trạng thái','Số lần gửi'],(data.items as Outbox[]).map(e=>h('tr',{key:e.eventId},h('td',{className:'booking-id'},e.eventId,h('small',null,e.eventType)),h('td',{className:'booking-id'},e.bookingId),h('td',null,e.aggregateVersion),h('td',null,e.status),h('td',null,e.attempts)))),
    tab==='outbox'&&h('p',{className:'muted'},'PENDING: đang chờ; PUBLISHED: Kafka đã xác nhận; FAILED: hết ngân sách gửi. Màn hình này chỉ xem dữ liệu.'),
    data&&tab!=='inventory'&&h('nav',{className:'pagination','aria-label':'Phân trang quản trị'},h('button',{type:'button',disabled:loading||page===0,onClick:()=>setPage(p=>p-1)},'Trang trước'),h('span',{'aria-live':'polite'},'Trang '+(page+1)+' · '+data.totalElements+' bản ghi'),h('button',{type:'button',disabled:loading||(page+1)*data.size>=data.totalElements,onClick:()=>setPage(p=>p+1)},'Trang sau')),
    );
}
