import {demoTransport} from './client.ts';
import type {Event,Booking,Status,Seat} from './client.ts';

export type Page<T>={items:T[];page:number;size:number;totalElements:number};
export type AdminEvent=Event&{saleStartAt:string;saleEndAt:string;status:'DRAFT'|'PUBLISHED'|'CLOSED'};
export type AdminInventory={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';capacity:number;held:number;sold:number;available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};
export type Outbox={eventId:string;bookingId:string;eventType:string;aggregateVersion:number;status:'PENDING'|'PUBLISHED'|'FAILED';createdAt:string;attempts:number};
export type RecoveryFilter='OPEN'|'RESOLVED'|'ALL';
export type ReplayRequest={requestId:string;reason:string};
export type ReplayAccepted={eventId:string;requestId:string;status:'ACCEPTED'};
export type RecoveryItem={id:string;source:'OUTBOX'|'CONSUMER';eventId:string|null;bookingId:string|null;eventType:string|null;consumer:string|null;status:string;error:string;createdAt:string;dlqStatus:'PENDING'|'PUBLISHED'|'EXHAUSTED'|null;replayable:boolean};
export type RecoveryEvent=Outbox&{correlationId:string;publishedAt:string|null;publisherError:string;remainingReplays:number;
  audit:{requestId:string;adminAccountId:string;reason:string;requestedAt:string}[];
  consumers:{consumer:string;processedAt:string}[];
  failures:{id:string;consumer:string;status:'DEAD'|'RESOLVED';error:string;attempts:number;createdAt:string;resolvedAt:string|null;dlqStatus:string;dlqAttempts:number}[]};
export type TypeInput={name:string;priceVnd:number}&({mode:'GA';capacity:number}|{mode:'SEATED';seats:{label:string}[]});
export type CreateEvent={title:string;description:string;venue:string;saleStartAt:string;saleEndAt:string;startsAt:string;status:AdminEvent['status'];ticketTypes:TypeInput[]};
export type PatchEvent=Partial<Omit<CreateEvent,'ticketTypes'>>&{ticketTypes?:{id:string;name?:string;priceVnd?:number;active?:boolean;capacity?:number}[]};
export interface AdminApi{
  events(page:number,size:number):Promise<Page<AdminEvent>>;inventory(id:string):Promise<AdminInventory>;
  create(body:CreateEvent):Promise<AdminEvent>;patch(id:string,body:PatchEvent):Promise<AdminEvent>;
  bookings(page:number,size:number,status?:Status):Promise<Page<Booking>>;booking(id:string):Promise<Booking>;
  outbox(page:number,size:number):Promise<Page<Outbox>>;
  recovery(page:number,size:number,status:RecoveryFilter):Promise<Page<RecoveryItem>>;
  recoveryEvent(id:string):Promise<RecoveryEvent>;
  replayEvent(id:string,request:ReplayRequest):Promise<ReplayAccepted>;
}
export function createAdminApi(base='http://127.0.0.1:8080',fetcher:typeof fetch=globalThis.fetch):AdminApi{
  const call=demoTransport(base,fetcher,'demo-admin-token');
  return{
    events:(page,size)=>call('/api/admin/events?'+new URLSearchParams({page:String(page),size:String(size)})),
    inventory:id=>call('/api/admin/events/'+encodeURIComponent(id)+'/inventory'),
    create:body=>call('/api/admin/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}),
    patch:(id,body)=>call('/api/admin/events/'+encodeURIComponent(id),{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}),
    bookings:(page,size,status)=>call('/api/admin/bookings?'+new URLSearchParams({page:String(page),size:String(size),...(status?{status}:{})})),
    booking:id=>call('/api/bookings/'+encodeURIComponent(id)),
    outbox:(page,size)=>call('/api/admin/operations/outbox?'+new URLSearchParams({page:String(page),size:String(size)})),
    recovery:(page,size,status)=>call('/api/admin/operations/recovery?'+new URLSearchParams({page:String(page),size:String(size),status})),
    recoveryEvent:id=>call('/api/admin/operations/events/'+encodeURIComponent(id)),
    replayEvent:(id,request)=>call('/api/admin/operations/events/'+encodeURIComponent(id)+'/replay',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requestId:request.requestId,reason:request.reason})})
  };
}
