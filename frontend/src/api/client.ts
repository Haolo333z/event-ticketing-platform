export type TicketType={id:string;name:string;mode:'GA'|'SEATED';priceVnd:number;active:boolean};
export type Event={id:string;title:string;description:string;venue:string;startsAt:string;ticketTypes:TicketType[]};
export type Status='PENDING_PAYMENT'|'CONFIRMED'|'PAYMENT_FAILED'|'CANCELLED'|'EXPIRED'|'REFUND_REQUIRED';
export type Booking={bookingId:string;status:Status;expiresAt:string;totalAmountVnd:number;history?:{fromStatus:Status|null;toStatus:Status;occurredAt:string}[]};
export type Command={eventId:string;ticketTypeId:string}&({quantity:number;seatIds?:never}|{seatIds:string[];quantity?:never});
export type Attempt={key:string;payload:Command};
export type Seat={id:string;label:string;status:'AVAILABLE'|'HELD'|'SOLD'};
export type Availability={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};
export class ApiError extends Error{
  code:string;status:number;correlationId?:string;
  constructor(code:string,message:string,status=0,correlationId?:string){super(message);this.name='ApiError';this.code=code;this.status=status;this.correlationId=correlationId;}
}
export interface Api{
  events():Promise<{items:Event[]}>;detail(id:string):Promise<Event>;availability(id:string):Promise<Availability>;
  create(attempt:Attempt):Promise<Booking>;booking(id:string):Promise<Booking>;mine():Promise<{items:Booking[]}>;
}
export function createApi(base='http://127.0.0.1:8080',fetcher:typeof fetch=globalThis.fetch):Api{
  async function call<T>(path:string,init:RequestInit={}):Promise<T>{
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);
    try{
      const response=await fetcher(base+path,{...init,signal:controller.signal,headers:{Authorization:'Bearer demo-customer-token',...init.headers}});
      let body:Record<string,unknown>;
      try{body=await response.json();}catch{throw new ApiError('NETWORK_ERROR','Chưa xác định được kết quả. Hãy thử lại cùng yêu cầu.');}
      if(!response.ok)throw new ApiError(String(body.code??'INTERNAL_ERROR'),String(body.message??'Không thể hoàn tất yêu cầu'),response.status,body.correlationId as string|undefined);
      return body as T;
    }catch(error){if(error instanceof ApiError)throw error;throw new ApiError('NETWORK_ERROR','Không nhận được phản hồi. Hãy thử lại cùng yêu cầu.');}
    finally{clearTimeout(timer);}
  }
  return{events:()=>call('/api/events?size=100'),detail:id=>call('/api/events/'+encodeURIComponent(id)),availability:id=>call('/api/events/'+encodeURIComponent(id)+'/availability'),
    create:attempt=>call('/api/bookings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':attempt.key},body:JSON.stringify(attempt.payload)}),
    booking:id=>call('/api/bookings/'+encodeURIComponent(id)),mine:()=>call('/api/me/bookings?size=100')};
}
const STORAGE_KEY='eventflow.pending-ga.v1';
export type Store=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
export function readAttempt(store:Store):Attempt|null{
  try{
    const value=JSON.parse(store.getItem(STORAGE_KEY)??'null');const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if(value&&uuid.test(value.key)&&uuid.test(value.payload?.eventId)&&uuid.test(value.payload?.ticketTypeId)){
      const p=value.payload;
      if(!('seatIds' in p)&&Number.isInteger(p.quantity)&&p.quantity>=1&&p.quantity<=6)return{key:value.key,payload:{eventId:p.eventId,ticketTypeId:p.ticketTypeId,quantity:p.quantity}};
      if(!('quantity' in p)&&Array.isArray(p.seatIds)&&p.seatIds.length>=1&&p.seatIds.length<=6&&p.seatIds.every((id:unknown)=>typeof id==='string'&&uuid.test(id))&&new Set(p.seatIds.map((id:string)=>id.toLowerCase())).size===p.seatIds.length)
        return{key:value.key,payload:{eventId:p.eventId,ticketTypeId:p.ticketTypeId,seatIds:[...p.seatIds]}};
    }
  }catch{/* Never automatically POST invalid or unavailable session data. */}
  return null;
}
export function saveAttempt(store:Store,value:Attempt|null){if(value)store.setItem(STORAGE_KEY,JSON.stringify(value));else store.removeItem(STORAGE_KEY);}
export function unknownOutcome(error:ApiError){return error.status===0||error.status>=500||error.code==='IDEMPOTENCY_KEY_REUSED';}
