import {createElement as h,useEffect,useState} from 'react';
import {ApiError} from '../api/client.ts';
import type {Api,Booking,Ticket,Status} from '../api/client.ts';

const vnd=(value:number)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
const date=(value:string)=>new Date(value).toLocaleString('vi-VN');

const statusLabels:Record<Status,string>={
  PENDING_PAYMENT:'Chờ thanh toán',
  CONFIRMED:'Đã xác nhận',
  PAYMENT_FAILED:'Thất bại',
  CANCELLED:'Đã hủy',
  EXPIRED:'Đã hết hạn',
  REFUND_REQUIRED:'Cần hoàn tiền'
};

const statusClasses:Record<Status,string>={
  PENDING_PAYMENT:'status-pending',
  CONFIRMED:'status-confirmed',
  PAYMENT_FAILED:'status-failed',
  CANCELLED:'status-cancelled',
  EXPIRED:'status-expired',
  REFUND_REQUIRED:'status-refund'
};

export function MyBookingsModal({
  isOpen,
  onClose,
  api,
  onSelectBooking
}:{
  isOpen:boolean;
  onClose:()=>void;
  api:Api;
  onSelectBooking?:(id:string)=>void;
}){
  const[bookings,setBookings]=useState<Booking[]>([]);
  const[loading,setLoading]=useState(false);
  const[error,setError]=useState<string|null>(null);
  const[ticketsMap,setTicketsMap]=useState<Record<string,Ticket[]>>({});
  const[loadingTickets,setLoadingTickets]=useState<Record<string,boolean>>({});

  useEffect(()=>{
    if(!isOpen)return;
    let active=true;
    setLoading(true);
    setError(null);
    api.mine()
      .then(res=>{
        if(!active)return;
        setBookings(res.items);
        // Automatically fetch tickets for confirmed bookings
        const confirmed=res.items.filter(b=>b.status==='CONFIRMED');
        for(const b of confirmed){
          setLoadingTickets(prev=>({...prev,[b.bookingId]:true}));
          api.tickets(b.bookingId)
            .then(t=>{
              if(active)setTicketsMap(prev=>({...prev,[b.bookingId]:t}));
            })
            .catch(()=>{/* ignore ticket fetch failure */})
            .finally(()=>{
              if(active)setLoadingTickets(prev=>({...prev,[b.bookingId]:false}));
            });
        }
      })
      .catch(err=>{
        if(!active)return;
        setError(err instanceof ApiError?err.message:'Không thể tải danh sách vé. Hãy thử lại.');
      })
      .finally(()=>{
        if(active)setLoading(false);
      });

    return()=>{active=false;};
  },[isOpen,api]);

  if(!isOpen)return null;

  return h('div',{className:'auth-modal-backdrop',onClick:(e)=>{if(e.target===e.currentTarget)onClose();}},
    h('div',{className:'auth-modal-card my-bookings-card',role:'dialog','aria-modal':true,'aria-labelledby':'my-bookings-title'},
      h('div',{className:'auth-modal-header'},
        h('div',{className:'auth-brand'},
          h('span',{id:'my-bookings-title',className:'auth-brand-logo'},'🎟️ Vé của tôi (My Bookings)'),
          h('p',{className:'auth-brand-subtitle'},'Quản lý lịch sử đặt vé và xem mã vé điện tử E-Ticket')
        ),
        h('button',{
          type:'button',
          className:'auth-close-btn',
          onClick:onClose,
          'aria-label':'Đóng'
        },'×')
      ),

      h('div',{className:'my-bookings-body'},
        loading&&h('div',{className:'bookings-loading-state'},
          h('div',{className:'spinner'}),
          h('p',null,'Đang đồng bộ vé từ PostgreSQL...')
        ),

        error&&h('div',{className:'alert error',role:'alert'},
          h('p',null,error)
        ),

        !loading&&!error&&bookings.length===0&&h('div',{className:'bookings-empty-state'},
          h('div',{className:'empty-icon'},'🎫'),
          h('h3',null,'Bạn chưa có vé nào'),
          h('p',null,'Khám phá ngay các sự kiện âm nhạc, hội nghị đỉnh cao và đặt vé liền tay.'),
          h('button',{
            type:'button',
            className:'primary',
            onClick:onClose
          },'Khám phá sự kiện ngay')
        ),

        !loading&&bookings.length>0&&h('div',{className:'bookings-list'},
          ...bookings.map(b=>{
            const bTickets=ticketsMap[b.bookingId]||[];
            const isFetchingTickets=loadingTickets[b.bookingId];
            return h('div',{key:b.bookingId,className:'my-booking-card'},
              h('div',{className:'booking-card-header'},
                h('div',{className:'booking-meta'},
                  h('span',{className:'booking-id-tag'},'Mã: '+b.bookingId.slice(0,8)+'...'),
                  h('span',{className:'booking-time'},'Hạn: '+date(b.expiresAt))
                ),
                h('div',{className:'booking-header-right'},
                  h('span',{className:'booking-amount'},vnd(b.totalAmountVnd)),
                  h('span',{className:'booking-status-badge '+statusClasses[b.status]},statusLabels[b.status]||b.status)
                )
              ),

              // If confirmed, render tickets
              b.status==='CONFIRMED'&&h('div',{className:'my-booking-tickets-section'},
                h('h4',{className:'tickets-section-title'},'Vé điện tử chính thức:'),
                isFetchingTickets&&h('p',{className:'ticket-loading-text'},'Đang tải mã vé...'),
                !isFetchingTickets&&bTickets.length===0&&h('p',{className:'ticket-loading-text'},'Chưa có dữ liệu vé.'),
                bTickets.length>0&&h('div',{className:'ticket-cards-grid'},
                  ...bTickets.map(ticket=>h('div',{key:ticket.id,className:'e-ticket-card mini'},
                    h('div',{className:'ticket-notch-top'}),
                    h('div',{className:'ticket-notch-bottom'}),
                    h('div',{className:'ticket-main'},
                      h('div',{className:'ticket-header'},
                        h('span',{className:'ticket-badge'},'🎟️ OFFICIAL TICKET'),
                        h('span',{className:'ticket-status-tag'},'XÁC NHẬN')
                      ),
                      h('div',{className:'ticket-body-content'},
                        h('div',{className:'ticket-details'},
                          h('strong',{className:'ticket-seat-label'},ticket.seatLabel?'Ghế '+ticket.seatLabel:'Vé GA · #'+ticket.ticketOrdinal),
                          h('div',{className:'ticket-code-wrap'},
                            h('span',{className:'code-caption'},'MÃ CHECK-IN:'),
                            h('code',{className:'ticket-code'},ticket.code)
                          )
                        ),
                        h('div',{className:'ticket-qr-zone'},
                          h('div',{className:'qr-box'},
                            h('svg',{viewBox:'0 0 24 24',width:'44',height:'44',fill:'none',stroke:'currentColor',strokeWidth:'1.5'},
                              h('rect',{x:'3',y:'3',width:'7',height:'7'}),
                              h('rect',{x:'14',y:'3',width:'7',height:'7'}),
                              h('rect',{x:'3',y:'14',width:'7',height:'7'}),
                              h('rect',{x:'14',y:'14',width:'3',height:'3'}),
                              h('rect',{x:'18',y:'18',width:'3',height:'3'}),
                              h('rect',{x:'18',y:'14',width:'3',height:'3'}),
                              h('rect',{x:'14',y:'18',width:'3',height:'3'})
                            )
                          ),
                          h('small',{className:'qr-hint'},'Quét tại cổng')
                        )
                      )
                    )
                  ))
                )
              ),

              // If pending, allow continuing payment
              b.status==='PENDING_PAYMENT'&&h('div',{className:'booking-pending-actions'},
                h('p',{className:'pending-hint'},'⚠️ Đơn hàng đang được giữ chỗ. Vui lòng hoàn tất thanh toán trước khi hết hạn.'),
                onSelectBooking&&h('button',{
                  type:'button',
                  className:'primary btn-continue-pay',
                  onClick:()=>{
                    onSelectBooking(b.bookingId);
                    onClose();
                  }
                },'Tiếp tục thanh toán ngay →')
              )
            );
          })
        )
      )
    )
  );
}
