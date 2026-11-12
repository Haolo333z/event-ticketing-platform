import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent,FormEvent} from 'react';
import {ApiError,readAttempt,saveAttempt,unknownOutcome} from '../api/client.ts';
import type {Api,Attempt,Availability,Booking,Event,Store} from '../api/client.ts';

const vnd=(value:number)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
const date=(value:string)=>new Date(value).toLocaleString('vi-VN');
const labels:Record<string,string>={PENDING_PAYMENT:'Đang chờ thanh toán',CONFIRMED:'Đã xác nhận',PAYMENT_FAILED:'Thanh toán thất bại',CANCELLED:'Đã hủy',EXPIRED:'Đã hết hạn',REFUND_REQUIRED:'Cần hoàn tiền'};
export function App({api,store}:{api:Api;store:Store}){
  const[pending,setPending]=useState<Attempt|null>(()=>readAttempt(store));
  const[events,setEvents]=useState<Event[]>([]);const[loading,setLoading]=useState(true);
  const[selected,setSelected]=useState(pending?.payload.eventId??'');const[detail,setDetail]=useState<Event|null>(null);
  const[typeId,setTypeId]=useState(pending?.payload.ticketTypeId??'');const[quantity,setQuantity]=useState(pending?.payload.quantity??1);
  const[availability,setAvailability]=useState<Availability|null>(null);const[booking,setBooking]=useState<Booking|null>(null);
  const[history,setHistory]=useState<Booking[]>([]);const[error,setError]=useState<ApiError|null>(null);
  const[notice,setNotice]=useState('');const[busy,setBusy]=useState(false);const submitting=useRef(false);
  const[catalogVersion,setCatalogVersion]=useState(0);
  const[completed,setCompleted]=useState('');
  function fail(value:unknown){setError(value instanceof ApiError?value:new ApiError('CLIENT_ERROR','Không thể tải dữ liệu. Hãy thử lại.'));}
  async function historyRefresh(){try{setHistory((await api.mine()).items);}catch(value){fail(value);}}
  useEffect(()=>{let active=true;setLoading(true);api.events().then(data=>{if(active){setEvents(data.items);setError(null);}}).catch(value=>{if(active)fail(value);}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[api,catalogVersion]);
  useEffect(()=>{void historyRefresh();},[api]);
  useEffect(()=>{
    let active=true;setDetail(null);setAvailability(null);
    if(selected)Promise.all([api.detail(selected),api.availability(selected)]).then(([event,snapshot])=>{
      if(!active)return;setDetail(event);setAvailability(snapshot);
      setTypeId(current=>event.ticketTypes.some(t=>t.id===current&&t.mode==='GA'&&t.active)?current:(event.ticketTypes.find(t=>t.mode==='GA'&&t.active)?.id??''));
    }).catch(value=>{if(active)fail(value);});return()=>{active=false;};
  },[api,selected,catalogVersion]);
  useEffect(()=>{
    if(!booking||booking.status!=='PENDING_PAYMENT')return;let active=true;const id=booking.bookingId;
    const timer=setInterval(()=>{api.booking(id).then(value=>{if(active)setBooking(value);}).catch(()=>{if(active)setNotice('Chưa cập nhật được trạng thái. Bạn có thể tải lại booking.');});},3000);
    return()=>{active=false;clearInterval(timer);};
  },[api,booking?.bookingId,booking?.status]);
  async function submit(event:FormEvent){
    event.preventDefault();if(submitting.current)return;submitting.current=true;setBusy(true);setError(null);setNotice('');let attempt=pending;
    try{
      if(!attempt){
        if(!selected||!typeId||!Number.isInteger(quantity)||quantity<1||quantity>6)throw new ApiError('VALIDATION_ERROR','Chọn từ 1 đến 6 vé GA.');
        attempt={key:crypto.randomUUID(),payload:{eventId:selected,ticketTypeId:typeId,quantity}};
        try{saveAttempt(store,attempt);}catch{throw new ApiError('STORAGE_ERROR','Không lưu được mã yêu cầu. Hãy cho phép session storage rồi thử lại.');}
        setPending(attempt);
      }
      const result=await api.create(attempt);setBooking(result);setCompleted(JSON.stringify(attempt.payload));saveAttempt(store,null);setPending(null);setNotice('Đã giữ vé. Giá và trạng thái được xác nhận bởi máy chủ.');
      await historyRefresh();try{setAvailability(await api.availability(attempt.payload.eventId));}catch{/* Keep the committed booking visible if refresh fails. */}
    }catch(value){
      const problem=value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa xác định được kết quả. Hãy thử lại cùng yêu cầu.');
      if(!unknownOutcome(problem)){saveAttempt(store,null);setPending(null);}fail(problem);
      if(problem.code==='INVENTORY_UNAVAILABLE'&&selected)try{setAvailability(await api.availability(selected));}catch{/* Preserve the original conflict message. */}
    }finally{setBusy(false);submitting.current=false;}
  }
  async function openBooking(id:string){try{setBooking(await api.booking(id));setError(null);setNotice('');}catch(value){fail(value);}}
  const type=detail?.ticketTypes.find(t=>t.id===typeId);const snapshot=availability?.ticketTypes.find(t=>t.ticketTypeId===typeId);
  const available=snapshot?.mode==='GA'?snapshot.available:null;
  const canBook=!!pending||!!(detail&&type&&type.mode==='GA'&&type.active&&quantity>=1&&quantity<=6&&Number.isInteger(quantity)&&available!==0&&completed!==JSON.stringify({eventId:selected,ticketTypeId:typeId,quantity}));
  return h('div',{className:'shell'},
    h('header',{className:'topbar'},h('a',{href:'/',className:'brand'},'EventFlow',h('span',null,'Vé sự kiện')),h('span',{className:'persona'},'Khách hàng demo')),
    h('main',null,
      h('section',{className:'intro'},h('p',{className:'eyebrow'},'KHÁM PHÁ · CHỌN VÉ · GIỮ CHỖ'),h('h1',null,'Một trải nghiệm mới đang chờ bạn.'),h('p',null,'Chọn vé vào cửa GA và giữ chỗ trực tiếp. Giá, tồn kho và booking được cập nhật từ máy chủ.')),
      error&&h('div',{role:'alert',className:'alert error'},h('strong',null,error.code==='INVENTORY_UNAVAILABLE'?'Không còn đủ vé':error.code==='BOOKING_BUSY'?'Hệ thống đang bận':error.message),h('p',null,error.message),h('small',null,'Mã lỗi: '+error.code),error.correlationId&&h('small',null,' · Mã tra cứu: '+error.correlationId)),
      notice&&h('div',{role:'status',className:'alert success'},notice),
      pending&&h('div',{className:'alert pending',role:'status'},'Có yêu cầu chưa xác định kết quả. Giữ nguyên lựa chọn và thử lại cùng yêu cầu để tránh giữ vé lần hai.'),
      h('div',{className:'workspace'},
        h('section',{'aria-labelledby':'catalog-heading',className:'catalog'},h('div',{className:'section-heading'},h('h2',{id:'catalog-heading'},'Sự kiện'),h('button',{type:'button',className:'quiet',disabled:busy,onClick:()=>setCatalogVersion(x=>x+1)},'Tải lại sự kiện')),
          loading?h('p',{role:'status'},'Đang tải sự kiện…'):events.length===0?h('p',null,'Chưa có sự kiện đang mở.'):h('div',{className:'event-grid'},...events.map(event=>h('article',{key:event.id,className:'event-card'+(selected===event.id?' selected':'')},
            h('div',{className:'event-mark'},event.ticketTypes.some(t=>t.mode==='GA')?'GA':'GHẾ CHỈ ĐỊNH'),h('h3',null,event.title),h('p',null,event.venue),h('p',{className:'muted'},date(event.startsAt)),h('button',{type:'button',disabled:busy||!!pending,onClick:()=>{setSelected(event.id);setQuantity(1);setError(null);}},selected===event.id?'Đang xem':'Xem sự kiện'))))),
        h('aside',{className:'booking-panel','aria-label':'Đặt vé GA'},h('p',{className:'eyebrow'},'LỰA CHỌN CỦA BẠN'),h('h2',null,detail?.title??'Chọn một sự kiện'),detail&&h('p',{className:'muted'},detail.description),
          detail&&!detail.ticketTypes.some(t=>t.mode==='GA'&&t.active)&&h('p',null,'Sự kiện này dùng ghế chỉ định. Phần chọn ghế sẽ được hỗ trợ sau.'),
          h('form',{onSubmit:submit},h('label',{htmlFor:'ticket-type'},'Loại vé GA'),h('select',{id:'ticket-type',value:typeId,disabled:busy||!!pending||!detail,onChange:(event:ChangeEvent<HTMLSelectElement>)=>setTypeId(event.target.value)},h('option',{value:''},'Chọn loại vé'),...(detail?.ticketTypes.filter(t=>t.mode==='GA'&&t.active)??[]).map(t=>h('option',{key:t.id,value:t.id},t.name))),
            type&&h('p',{className:'unit-price'},vnd(type.priceVnd),h('small',null,' / vé · giá từ máy chủ')),
            h('p',{'aria-live':'polite',className:available===0?'sold-out':'availability'},available===null?'Chưa có tồn kho tham khảo':available===0?'Hết vé':'Còn '+available+' vé tham khảo'),
            h('label',{htmlFor:'quantity'},'Số lượng vé'),h('input',{id:'quantity',type:'number',min:1,max:6,step:1,value:quantity,disabled:busy||!!pending,onChange:(event:ChangeEvent<HTMLInputElement>)=>setQuantity(Number(event.target.value))}),
            h('small',{className:'muted'},'Tối đa 6 vé mỗi booking. Tồn kho được kiểm tra lại khi gửi.'),h('button',{type:'submit',className:'primary',disabled:busy||!canBook},busy?'Đang gửi…':pending?'Thử lại cùng yêu cầu':'Giữ vé GA')),
          h('p',{className:'footnote'},'Bản demo hiện chưa hỗ trợ thanh toán. Booking không tự đổi trạng thái trên giao diện khi qua hạn.'))),
      booking&&h('section',{className:'result','aria-label':'Kết quả booking'},h('div',{className:'section-heading'},h('h2',null,'Booking của bạn'),h('span',{className:'status-pill'},labels[booking.status]??booking.status)),
        h('dl',null,h('dt',null,'Mã booking'),h('dd',{className:'booking-id'},booking.bookingId),h('dt',null,'Trạng thái máy chủ'),h('dd',null,booking.status),h('dt',null,'Tổng tiền máy chủ'),h('dd',null,vnd(booking.totalAmountVnd)),h('dt',null,'Hạn giữ chỗ'),h('dd',null,date(booking.expiresAt))),
        h('button',{type:'button',className:'quiet',onClick:()=>void openBooking(booking.bookingId)},'Cập nhật booking'),h('button',{type:'button',className:'quiet',disabled:busy||!!pending,onClick:()=>{setCompleted('');setBooking(null);setNotice('');}},'Đặt thêm vé'),booking.history&&h('ul',{className:'timeline'},...booking.history.map((item,i)=>h('li',{key:i},date(item.occurredAt)+' · '+item.toStatus)))),
      h('section',{className:'history','aria-labelledby':'history-heading'},h('div',{className:'section-heading'},h('h2',{id:'history-heading'},'Lịch sử của tôi'),h('button',{type:'button',className:'quiet',onClick:()=>void historyRefresh()},'Tải lại lịch sử')),
        history.length===0?h('p',{className:'muted'},'Chưa có booking nào.'):h('ul',{className:'history-list'},...history.map(item=>h('li',{key:item.bookingId},h('button',{type:'button',onClick:()=>void openBooking(item.bookingId)},h('span',{className:'booking-id'},item.bookingId),h('span',null,labels[item.status]??item.status),h('strong',null,vnd(item.totalAmountVnd)))))))));
}
