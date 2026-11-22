import {createElement as h,useEffect,useRef,useState} from 'react';
import {ApiError,readPaymentAttempt,savePaymentAttempt,unknownOutcome} from '../api/client.ts';
import type {Api,Booking,PaymentAttempt,Scenario,Store,Ticket} from '../api/client.ts';

export function Checkout({api,store,booking,onChange}:{api:Api;store:Store;booking:Booking;onChange:(value:Booking)=>void}){
  const[pending,setPending]=useState<PaymentAttempt|null>(()=>readPaymentAttempt(store));
  const[busy,setBusy]=useState(false),[error,setError]=useState<ApiError|null>(null),[notice,setNotice]=useState('');
  const[tickets,setTickets]=useState<Ticket[]>([]),[accepted,setAccepted]=useState(false);
  const[vnpayLoading,setVnpayLoading]=useState(false);
  const[voucherCode,setVoucherCode]=useState('');
  const[voucherBusy,setVoucherBusy]=useState(false);
  const[voucherSuccess,setVoucherSuccess]=useState<string|null>(null);
  const[voucherDiscount,setVoucherDiscount]=useState<number>(0);
  const[voucherError,setVoucherError]=useState<string|null>(null);
  const[requestVat,setRequestVat]=useState(false);
  const[vatTaxCode,setVatTaxCode]=useState('');
  const[vatCompanyName,setVatCompanyName]=useState('');
  const[vatCompanyAddress,setVatCompanyAddress]=useState('');
  const[vatRecipientEmail,setVatRecipientEmail]=useState('');
  const[vatIssued,setVatIssued]=useState(false);
  const[vatNotice,setVatNotice]=useState<string|null>(null);
  const sending=useRef(false);

  useEffect(()=>{
    if(requestVat&&!vatCompanyName&&api.profile?.vatDefaults){
      api.profile.vatDefaults().then(defaults=>{
        if(defaults){
          if(defaults.companyName)setVatCompanyName(defaults.companyName);
          if(defaults.taxCode)setVatTaxCode(defaults.taxCode);
          if(defaults.companyAddress)setVatCompanyAddress(defaults.companyAddress);
          if(defaults.recipientEmail)setVatRecipientEmail(defaults.recipientEmail);
        }
      }).catch(()=>{});
    }
  },[requestVat,api.profile]);

  async function applyVoucher(){
    if(!voucherCode.trim()||voucherBusy||booking.status!=='PENDING_PAYMENT')return;
    setVoucherBusy(true);setVoucherError(null);setVoucherSuccess(null);
    try{
      if(api.vouchers?.apply){
        const res=await api.vouchers.apply(booking.bookingId,voucherCode.trim());
        setVoucherSuccess(res.message||`Áp dụng mã ${res.code} thành công: giảm ${res.discountAmountVnd.toLocaleString('vi-VN')}đ`);
        setVoucherDiscount(res.discountAmountVnd);
        await refresh(booking.bookingId);
      }
    }catch(val){
      const msg=val instanceof ApiError?val.message:'Không áp dụng được mã giảm giá';
      setVoucherError(msg);
    }finally{
      setVoucherBusy(false);
    }
  }

  async function checkoutVnPay(){
    if(sending.current||busy||vnpayLoading)return;
    setVnpayLoading(true);setError(null);
    try{
      if(api.payments?.checkout){
        const returnUrl=typeof window!=='undefined'?window.location.origin+'/payment-return':'http://localhost:5173/payment-return';
        const res=await api.payments.checkout({bookingId:booking.bookingId,provider:'VNPAY',returnUrl});
        if(res&&res.gatewayUrl&&typeof window!=='undefined'){
          window.location.href=res.gatewayUrl;
        }
      }
    }catch(val){
      setError(val instanceof ApiError?val:new ApiError('NETWORK_ERROR','Không thể kết nối cổng thanh toán VNPAY. Thử lại hoặc dùng mô phỏng.'));
    }finally{
      setVnpayLoading(false);
    }
  }
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
    h('h3',null,'Thanh toán mô phỏng'),
    h('p',{className:'demo-warning'},'Demo — không thu tiền thật. Không nhập thông tin thẻ.'),
    error&&h('div',{
      role:'alert',
      className:error.code==='PAYMENT_GATEWAY_CIRCUIT_OPEN'?'alert warning circuit-breaker-alert':'alert error',
      style:error.code==='PAYMENT_GATEWAY_CIRCUIT_OPEN'?{background:'#fff7ed',borderColor:'#fdba74',color:'#9a3412',padding:'1rem',borderRadius:'12px',marginBottom:'1rem'}:undefined
    },
      error.code==='PAYMENT_GATEWAY_CIRCUIT_OPEN'&&h('div',{style:{fontWeight:700,fontSize:'0.95rem',marginBottom:'0.35rem',display:'flex',alignItems:'center',gap:'0.5rem'}},
        h('span',null,'⚠️'),
        h('span',null,'CỔNG THANH TOÁN TẠM THỜI GIÁN ĐOẠN / BẢO TRÌ (CIRCUIT OPEN)')
      ),
      h('div',null,error.message),
      h('small',{style:{display:'block',marginTop:'0.35rem',opacity:0.85}},'Mã lỗi: '+error.code,error.correlationId&&' · Tra cứu: '+error.correlationId)
    ),
    notice&&h('p',{role:'status'},notice),
    pending&&h('div',{className:'alert pending'},'Yêu cầu mô phỏng chưa rõ kết quả cho booking '+pending.bookingId+'. Giữ nguyên requestId và kịch bản khi thử lại.',h('button',{type:'button',disabled:busy,onClick:()=>void pay(pending.scenario)},'Thử lại thanh toán cùng yêu cầu')),
    booking.status==='PENDING_PAYMENT'&&h('div',{className:'voucher-box',style:{margin:'1rem 0',padding:'1rem',background:'#f8fafc',border:'1px dashed #cbd5e1',borderRadius:'12px'}},
      h('div',{style:{fontWeight:600,marginBottom:'0.5rem',display:'flex',alignItems:'center',gap:'0.5rem'}},
        h('span',null,'🎟️'),
        h('span',null,'Mã ưu đãi / Voucher giảm giá')
      ),
      h('div',{style:{display:'flex',gap:'0.5rem'}},
        h('input',{
          type:'text',
          placeholder:'Nhập SUMMER2026 hoặc WELCOME50K...',
          value:voucherCode,
          disabled:voucherBusy||voucherDiscount>0,
          onChange:(e:any)=>setVoucherCode(e.target.value.toUpperCase()),
          style:{flex:1,padding:'0.5rem 0.75rem',border:'1px solid #cbd5e1',borderRadius:'8px',textTransform:'uppercase',fontWeight:600}
        }),
        h('button',{
          type:'button',
          className:'btn-primary',
          disabled:voucherBusy||!voucherCode.trim()||voucherDiscount>0,
          onClick:()=>void applyVoucher(),
          style:{padding:'0.5rem 1rem',borderRadius:'8px'}
        },voucherBusy?'Đang áp dụng…':voucherDiscount>0?'✓ Đã áp dụng':'Áp dụng')
      ),
      voucherSuccess&&h('p',{style:{color:'#059669',fontSize:'0.875rem',margin:'0.5rem 0 0',fontWeight:500}},'✓ '+voucherSuccess),
      voucherError&&h('p',{style:{color:'#e11d48',fontSize:'0.875rem',margin:'0.5rem 0 0'}},'✕ '+voucherError),
      voucherDiscount>0&&h('div',{style:{display:'flex',justifyContent:'space-between',marginTop:'0.75rem',paddingTop:'0.5rem',borderTop:'1px solid #e2e8f0',fontWeight:600}},
        h('span',null,'Giảm giá voucher:'),
        h('span',{style:{color:'#059669'}},`-${voucherDiscount.toLocaleString('vi-VN')}đ`)
      )
    ),
    booking.status==='PENDING_PAYMENT'&&h('div',{className:'vat-invoice-box',style:{margin:'1rem 0',padding:'1rem',background:'#f0fdf4',border:'1px solid #bbf7d0',borderRadius:'12px'}},
      h('label',{style:{display:'flex',alignItems:'center',gap:'0.5rem',fontWeight:600,cursor:'pointer',userSelect:'none'}},
        h('input',{
          type:'checkbox',
          checked:requestVat,
          onChange:(e:any)=>setRequestVat(e.target.checked),
          style:{width:'18px',height:'18px',cursor:'pointer'}
        }),
        h('span',null,'🧾 Yêu cầu xuất hóa đơn điện tử VAT (10%)')
      ),
      requestVat&&h('div',{style:{marginTop:'0.75rem',display:'flex',flexDirection:'column',gap:'0.5rem'}},
        h('p',{style:{fontSize:'0.825rem',color:'#166534',margin:'0 0 0.25rem'}},
          '✓ Thông tin đã được tự động điền từ Hồ sơ cá nhân (nếu đã lưu sẵn)'
        ),
        h('div',{style:{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'0.5rem'}},
          h('input',{
            type:'text',
            placeholder:'Mã số thuế (MST)',
            value:vatTaxCode,
            onChange:(e:any)=>setVatTaxCode(e.target.value),
            style:{padding:'0.45rem 0.65rem',border:'1px solid #cbd5e1',borderRadius:'6px',fontSize:'0.875rem'}
          }),
          h('input',{
            type:'text',
            placeholder:'Tên công ty / Đơn vị',
            value:vatCompanyName,
            onChange:(e:any)=>setVatCompanyName(e.target.value),
            style:{padding:'0.45rem 0.65rem',border:'1px solid #cbd5e1',borderRadius:'6px',fontSize:'0.875rem'}
          })
        ),
        h('input',{
          type:'text',
          placeholder:'Địa chỉ công ty theo ĐKKD',
          value:vatCompanyAddress,
          onChange:(e:any)=>setVatCompanyAddress(e.target.value),
          style:{padding:'0.45rem 0.65rem',border:'1px solid #cbd5e1',borderRadius:'6px',fontSize:'0.875rem'}
        }),
        h('input',{
          type:'email',
          placeholder:'Email nhận hóa đơn điện tử (.pdf & .xml)',
          value:vatRecipientEmail,
          onChange:(e:any)=>setVatRecipientEmail(e.target.value),
          style:{padding:'0.45rem 0.65rem',border:'1px solid #cbd5e1',borderRadius:'6px',fontSize:'0.875rem'}
        })
      )
    ),
    booking.status==='PENDING_PAYMENT'&&h('div',{className:'payment-gateway-box'},
      h('button',{
        type:'button',
        className:'btn-vnpay-pay',
        disabled:busy||vnpayLoading||!!pending||!canSimulate,
        onClick:()=>void checkoutVnPay()
      },vnpayLoading?'Đang kết nối cổng VNPAY…':'💳 Thanh Toán VNPAY Sandbox (ATM / QR Code)'),
      h('div',{className:'divider-or'},h('span',null,'hoặc chọn kịch bản mô phỏng kiểm thử:'))
    ),
    h('div',{className:'payment-actions'},...([
      ['SUCCESS','Mô phỏng thành công'],['FAILURE','Mô phỏng thất bại'],['DELAY_TIMEOUT','Mô phỏng kết quả đến muộn']
    ] as const).map(([scenario,label])=>h('button',{key:scenario,type:'button',disabled:busy||!!pending||!canSimulate,onClick:()=>void pay(scenario)},label)),
      booking.status==='PENDING_PAYMENT'&&h('button',{type:'button',className:'quiet',disabled:busy||!!pending,onClick:()=>void cancel()},'Hủy giữ chỗ')),
    booking.status==='REFUND_REQUIRED'&&h('p',{className:'alert pending'},'Kết quả thành công đến sau khi mất quyền giữ chỗ. Không phát vé; nghĩa vụ bù trừ được worker hoàn tiền mô phỏng xử lý. Trạng thái này được giữ để theo dõi; không có giao dịch tiền thật.'),
    h('section',{'aria-label':'Vé của booking',className:'tickets'},
      h('h3',null,'Vé điện tử của bạn (E-Tickets)'),
      booking.status!=='CONFIRMED'?h('p',null,'Chưa có vé. Chỉ booking CONFIRMED được phát vé.'):h('div',null,
        h('button',{type:'button',className:'quiet',disabled:busy,onClick:()=>void refresh().catch(value=>setError(value instanceof ApiError?value:new ApiError('NETWORK_ERROR','Chưa tải được vé')))},'Tải lại vé'),
        tickets.length===0?h('p',{role:'status'},'Chưa tải được vé từ máy chủ.'):h('ul',{className:'ticket-list'},...tickets.map(ticket=>h('li',{key:ticket.id,'data-ticket-id':ticket.id,className:'e-ticket-card'},
          h('div',{className:'ticket-notch-top'}),
          h('div',{className:'ticket-notch-bottom'}),
          h('div',{className:'ticket-main'},
            h('div',{className:'ticket-header'},
              h('span',{className:'ticket-badge'},'🎟️ EVENTFLOW OFFICIAL TICKET'),
              h('span',{className:'ticket-status-tag'},'ĐÃ XÁC NHẬN')
            ),
            h('div',{className:'ticket-body-content'},
              h('div',{className:'ticket-details'},
                h('strong',{className:'ticket-seat-label'},ticket.seatLabel?'Ghế '+ticket.seatLabel:'Vé GA · số '+ticket.ticketOrdinal),
                h('div',{className:'ticket-code-wrap'},
                  h('span',{className:'code-caption'},'MÃ CHECK-IN:'),
                  h('code',{className:'ticket-code'},ticket.code)
                ),
                h('small',{className:'ticket-id-sub'},'Mã tra cứu demo · '+ticket.id)
              ),
              h('div',{className:'ticket-qr-zone'},
                h('div',{className:'qr-box'},
                  h('svg',{viewBox:'0 0 24 24',width:'48',height:'48',fill:'none',stroke:'currentColor',strokeWidth:'1.5'},
                    h('rect',{x:'3',y:'3',width:'7',height:'7'}),
                    h('rect',{x:'14',y:'3',width:'7',height:'7'}),
                    h('rect',{x:'3',y:'14',width:'7',height:'7'}),
                    h('rect',{x:'14',y:'14',width:'3',height:'3'}),
                    h('rect',{x:'18',y:'18',width:'3',height:'3'}),
                    h('rect',{x:'18',y:'14',width:'3',height:'3'}),
                    h('rect',{x:'14',y:'18',width:'3',height:'3'})
                  )
                ),
                h('small',{className:'qr-hint'},'Quét mã tại cổng')
              )
            )
          )
        )))
      )
    )
  );
}
