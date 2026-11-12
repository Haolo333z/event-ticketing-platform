import {createElement as h,useEffect,useRef,useState} from 'react';
import {ApiError,readPaymentAttempt,savePaymentAttempt,unknownOutcome} from '../api/client.ts';
import type {Api,Booking,PaymentAttempt,Scenario,Store,Ticket} from '../api/client.ts';

export function Checkout({api,store,booking,onChange}:{api:Api;store:Store;booking:Booking;onChange:(value:Booking)=>void}){
  const[pending,setPending]=useState<PaymentAttempt|null>(()=>readPaymentAttempt(store));
  const[busy,setBusy]=useState(false),[error,setError]=useState<ApiError|null>(null),[notice,setNotice]=useState('');
  const[tickets,setTickets]=useState<Ticket[]>([]),[accepted,setAccepted]=useState(false);
  const sending=useRef(false);
  useEffect(()=>{
    let active=true;setTickets([]);
    if(booking.status==='CONFIRMED')api.tickets(booking.bookingId).then(value=>{if(active)setTickets(value);}).catch(value=>{if(active)setError(value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa tải được vé. Hãy tải lại.'));});
    return()=>{active=false;};
  },[api,booking.bookingId,booking.status]);
  async function refresh(id=booking.bookingId){const current=await api.booking(id);onChange(current);if(current.status==='CONFIRMED')setTickets(await api.tickets(id));}
  async function pay(scenario:Scenario){
    if(sending.current)return;sending.current=true;setBusy(true);setError(null);setNotice('');
    let command=pending;
    try{
      if(!command){
        command={bookingId:booking.bookingId,scenario,requestId:crypto.randomUUID()};
        try{savePaymentAttempt(store,command);}catch{throw new ApiError('STORAGE_ERROR','Không lưu được mã thanh toán thử. Hãy cho phép session storage.');}
        setPending(command);
      }
      await api.simulate(command);
      savePaymentAttempt(store,null);setPending(null);setAccepted(true);setNotice('Máy chủ đã nhận mô phỏng. Trạng thái và vé lấy từ API.');
      try{await refresh(command.bookingId);}catch{setNotice('Đã nhận mô phỏng. Chưa tải được trạng thái; hãy cập nhật booking.');}
    }catch(value){
      const problem=value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa rõ kết quả mô phỏng. Thử lại cùng yêu cầu.');
      if(!unknownOutcome(problem)){try{savePaymentAttempt(store,null);}catch{}setPending(null);}
      setError(problem);
    }finally{sending.current=false;setBusy(false);}
  }
  async function cancel(){
    if(sending.current||pending)return;sending.current=true;setBusy(true);setError(null);
    try{onChange(await api.cancel(booking.bookingId));setNotice('Đã hủy giữ chỗ theo xác nhận của máy chủ.');}
    catch(value){setError(value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa rõ kết quả hủy; có thể thử lại.'));try{await refresh();}catch{}}
    finally{sending.current=false;setBusy(false);}
  }
  const canSimulate=booking.status!=='CONFIRMED'&&booking.status!=='REFUND_REQUIRED'&&!(accepted&&booking.status==='PENDING_PAYMENT');
  return h('section',{className:'checkout','aria-label':'Thanh toán mô phỏng'},
    h('h3',null,'Thanh toán mô phỏng'),h('p',{className:'demo-warning'},'Demo — không thu tiền thật. Không nhập thông tin thẻ.'),
    error&&h('div',{role:'alert',className:'alert error'},error.message,h('small',null,' · Mã lỗi: '+error.code),error.correlationId&&h('small',null,' · Mã tra cứu: '+error.correlationId)),
    notice&&h('p',{role:'status'},notice),
    pending&&h('div',{className:'alert pending'},'Yêu cầu mô phỏng chưa rõ kết quả cho booking '+pending.bookingId+'. Giữ nguyên requestId và kịch bản khi thử lại.',h('button',{type:'button',disabled:busy,onClick:()=>void pay(pending.scenario)},'Thử lại thanh toán cùng yêu cầu')),
    h('div',{className:'payment-actions'},...([
      ['SUCCESS','Mô phỏng thành công'],['FAILURE','Mô phỏng thất bại'],['DELAY_TIMEOUT','Mô phỏng kết quả đến muộn']
    ] as const).map(([scenario,label])=>h('button',{key:scenario,type:'button',disabled:busy||!!pending||!canSimulate,onClick:()=>void pay(scenario)},label)),
      booking.status==='PENDING_PAYMENT'&&h('button',{type:'button',className:'quiet',disabled:busy||!!pending,onClick:()=>void cancel()},'Hủy giữ chỗ')),
    booking.status==='REFUND_REQUIRED'&&h('p',{className:'alert pending'},'Kết quả thành công đến sau khi mất quyền giữ chỗ. Không phát vé; đã ghi nghĩa vụ bù trừ mô phỏng, chưa xử lý hoàn tiền.'),
    h('section',{'aria-label':'Vé của booking',className:'tickets'},h('h3',null,'Vé của booking'),
      booking.status!=='CONFIRMED'?h('p',null,'Chưa có vé. Chỉ booking CONFIRMED được phát vé.'):h('div',null,
        h('button',{type:'button',className:'quiet',disabled:busy,onClick:()=>void refresh().catch(value=>setError(value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa tải được vé')))},'Tải lại vé'),
        tickets.length===0?h('p',{role:'status'},'Chưa tải được vé từ máy chủ.'):h('ul',{className:'ticket-list'},...tickets.map(ticket=>h('li',{key:ticket.id,'data-ticket-id':ticket.id},h('strong',null,ticket.seatLabel?'Ghế '+ticket.seatLabel:'Vé GA · số '+ticket.ticketOrdinal),h('code',null,ticket.code),h('small',null,'Mã tra cứu demo · '+ticket.id)))))));
}
