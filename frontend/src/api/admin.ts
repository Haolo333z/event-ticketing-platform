import {demoTransport} from './client.ts';
import type {Event,Booking,Status,Seat} from './client.ts';

export type Page<T>={items:T[];page:number;size:number;totalElements:number};
export type AdminEvent=Event&{saleStartAt:string;saleEndAt:string;status:'DRAFT'|'PUBLISHED'|'CLOSED'};
export type AdminInventory={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';capacity:number;held:number;sold:number;available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};
export type Outbox={eventId:string;bookingId:string;eventType:string;aggregateVersion:number;status:'PENDING'|'PUBLISHED'|'FAILED';createdAt:string;attempts:number};
export type TypeInput={name:string;priceVnd:number}&({mode:'GA';capacity:number}|{mode:'SEATED';seats:{label:string}[]});
export type CreateEvent={title:string;description:string;venue:string;saleStartAt:string;saleEndAt:string;startsAt:string;status:AdminEvent['status'];ticketTypes:TypeInput[]};
export type PatchEvent=Partial<Omit<CreateEvent,'ticketTypes'>>&{ticketTypes?:{id:string;name?:string;priceVnd?:number;active?:boolean;capacity?:number}[]};
export interface AdminApi{
  events(page:number,size:number):Promise<Page<AdminEvent>>;inventory(id:string):Promise<AdminInventory>;
  create(body:CreateEvent):Promise<AdminEvent>;patch(id:string,body:PatchEvent):Promise<AdminEvent>;
  bookings(page:number,size:number,status?:Status):Promise<Page<Booking>>;booking(id:string):Promise<Booking>;
  outbox(page:number,size:number):Promise<Page<Outbox>>;
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
    outbox:(page,size)=>call('/api/admin/operations/outbox?'+new URLSearchParams({page:String(page),size:String(size)}))
  };
}
