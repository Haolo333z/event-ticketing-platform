export type TicketType={id:string;name:string;mode:'GA'|'SEATED';priceVnd:number;active:boolean};
export type Event={id:string;title:string;description:string;venue:string;startsAt:string;ticketTypes:TicketType[]};
export type Status='PENDING_PAYMENT'|'CONFIRMED'|'PAYMENT_FAILED'|'CANCELLED'|'EXPIRED'|'REFUND_REQUIRED';
export type Booking={bookingId:string;status:Status;expiresAt:string;totalAmountVnd:number;history?:{fromStatus:Status|null;toStatus:Status;occurredAt:string}[]};
export type Scenario='SUCCESS'|'FAILURE'|'DELAY_TIMEOUT';
export type PaymentAttempt={bookingId:string;scenario:Scenario;requestId:string};
export type Ticket={id:string;bookingId:string;bookingItemId:string;ticketOrdinal:number;code:string;seatId:string|null;seatLabel:string|null};
export type TicketQrResponse={ticketId:string;ticketCode:string;eventId:string;qrToken:string;issuedAt:string};
export type Command={eventId:string;ticketTypeId:string}&({quantity:number;seatIds?:never}|{seatIds:string[];quantity?:never});
export type Attempt={key:string;payload:Command};
export type Seat={id:string;label:string;status:'AVAILABLE'|'HELD'|'SOLD'};
export type Availability={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};

export type Role='CUSTOMER'|'STAFF'|'ORGANIZER'|'ADMIN';

export type User={
  id:string;
  displayName:string;
  email:string;
  phone?:string;
  role:Role|string;
  roles?:Role[];
};

export type OrganizerProfile={
  id:string;
  userId:string;
  organizationName:string;
  contactEmail:string;
  phone:string;
  taxCode?:string;
  status:string;
  createdAt:string;
  updatedAt:string;
};

export type Venue={
  id:string;
  name:string;
  address:string;
  city:string;
  country:string;
  capacity:number;
  seatingLayoutJson?:string;
};

export type EventCategory={
  id:string;
  name:string;
  description?:string;
  iconUrl?:string;
};

export type OrganizerEvent={
  id:string;
  title:string;
  description:string;
  venue:string;
  venueId?:string;
  categoryId?:string;
  organizerId?:string;
  saleStartAt:string;
  saleEndAt:string;
  startsAt:string;
  status:'DRAFT'|'PUBLISHED'|'CLOSED';
  ticketTypes:{
    id:string;
    name:string;
    mode:'GA'|'SEATED';
    priceVnd:number;
    active:boolean;
    capacity?:number;
    available?:number;
    seats?:{id:string;label:string;status:string}[];
  }[];
  totalCapacity:number;
  soldTickets:number;
  createdAt:string;
};

export type StaffAssignment={
  id:string;
  eventId:string;
  staffUserId:string;
  staffName:string;
  staffEmail:string;
  assignedBy:string;
  assignedAt:string;
  revokedAt?:string;
};

export type StaffCandidate={
  id:string;
  displayName:string;
  email:string;
  phone?:string;
};

export type AuthResponse={
  token:string;
  user:User;
};

export const AUTH_TOKEN_KEY='eventflow_token';
export const AUTH_USER_KEY='eventflow_user';

export function getStoredToken():string|null{
  try{
    if(typeof localStorage!=='undefined')return localStorage.getItem(AUTH_TOKEN_KEY);
  }catch{/* ignore */}
  return null;
}

export function getStoredUser():User|null{
  try{
    if(typeof localStorage!=='undefined'){
      const raw=localStorage.getItem(AUTH_USER_KEY);
      return raw?JSON.parse(raw):null;
    }
  }catch{/* ignore */}
  return null;
}

export function setStoredAuth(token:string,user:User):void{
  try{
    if(typeof localStorage!=='undefined'){
      localStorage.setItem(AUTH_TOKEN_KEY,token);
      localStorage.setItem(AUTH_USER_KEY,JSON.stringify(user));
    }
  }catch{/* ignore */}
}

export function clearStoredAuth():void{
  try{
    if(typeof localStorage!=='undefined'){
      localStorage.removeItem(AUTH_TOKEN_KEY);
      localStorage.removeItem(AUTH_USER_KEY);
    }
  }catch{/* ignore */}
}

export class ApiError extends Error{
  code:string;status:number;correlationId?:string;
  constructor(code:string,message:string,status=0,correlationId?:string){super(message);this.name='ApiError';this.code=code;this.status=status;this.correlationId=correlationId;}
}

export interface Api{
  events():Promise<{items:Event[]}>;detail(id:string):Promise<Event>;availability(id:string):Promise<Availability>;
  create(attempt:Attempt):Promise<Booking>;booking(id:string):Promise<Booking>;mine():Promise<{items:Booking[]}>;
  simulate(attempt:PaymentAttempt):Promise<{paymentIntentId:string;status:'ACCEPTED'}>;cancel(id:string):Promise<Booking>;tickets(id:string):Promise<Ticket[]>;
  ticketQr?(id:string):Promise<TicketQrResponse>;
  auth?:{
    register(data:{displayName:string;email:string;password:string;phone?:string}):Promise<AuthResponse>;
    login(data:{email:string;password:string}):Promise<AuthResponse>;
    refresh():Promise<AuthResponse>;
    logout():Promise<{success:boolean;message?:string}>;
    me():Promise<User>;
  };
  contact?:{
    submitLead(data:{fullName:string;email:string;phone:string;companyName?:string;eventScale?:string;message?:string}):Promise<{id:string;message:string}>;
  };
  organizer?:{
    getProfile():Promise<OrganizerProfile|null>;
    saveProfile(data:{organizationName:string;contactEmail:string;phone:string;taxCode?:string}):Promise<OrganizerProfile>;
    venues():Promise<Venue[]>;
    categories():Promise<EventCategory[]>;
    events():Promise<OrganizerEvent[]>;
    event(id:string):Promise<OrganizerEvent>;
    createEvent(data:{title:string;description?:string;venue?:string;venueId?:string;categoryId?:string;saleStartAt:string;saleEndAt:string;startsAt:string}):Promise<OrganizerEvent>;
    updateEvent(id:string,data:{title:string;description?:string;venue?:string;venueId?:string;categoryId?:string;saleStartAt:string;saleEndAt:string;startsAt:string}):Promise<OrganizerEvent>;
    publishEvent(id:string):Promise<OrganizerEvent>;
    addTicketType(id:string,data:{name:string;mode:'GA'|'SEATED';priceVnd:number;capacity?:number;seatLabels?:string[]}):Promise<OrganizerEvent>;
    staff(id:string):Promise<StaffAssignment[]>;
    assignStaff(id:string,data:{staffUserId?:string;staffEmail?:string}):Promise<StaffAssignment>;
    revokeStaff(id:string,staffUserId:string):Promise<void>;
    staffCandidates(q?:string):Promise<StaffCandidate[]>;
  };
}

let activeRefreshPromise:Promise<string|null>|null=null;

export function demoTransport(base:string,fetcher:typeof fetch,token:string|(()=>string)){
  async function doRefresh():Promise<string|null>{
    if(activeRefreshPromise){
      return activeRefreshPromise;
    }
    activeRefreshPromise=(async()=>{
      try{
        const res=await fetcher(base+'/api/auth/refresh',{
          method:'POST',
          credentials:'include',
          headers:{'Content-Type':'application/json'}
        });
        if(!res.ok){
          clearStoredAuth();
          return null;
        }
        const data=await res.json() as AuthResponse;
        if(data&&data.token&&data.user){
          setStoredAuth(data.token,data.user);
          return data.token;
        }
        clearStoredAuth();
        return null;
      }catch{
        return null;
      }finally{
        activeRefreshPromise=null;
      }
    })();
    return activeRefreshPromise;
  }

  async function call<T>(path:string,init:RequestInit={}):Promise<T>{
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);
    const resolvedToken=typeof token==='function'?token():token;
    try{
      const headers:Record<string,string>={
        ...(init.headers as Record<string,string>|undefined)
      };
      if(resolvedToken){
        headers['Authorization']='Bearer '+resolvedToken;
      }
      let response=await fetcher(base+path,{...init,signal:controller.signal,headers,credentials:'include'});

      // 401 interceptor with token family refresh
      if(response.status===401&&!path.startsWith('/api/auth/login')&&!path.startsWith('/api/auth/register')&&!path.startsWith('/api/auth/refresh')){
        const refreshedToken=await doRefresh();
        if(refreshedToken){
          const retryHeaders:Record<string,string>={
            ...(init.headers as Record<string,string>|undefined),
            'Authorization':'Bearer '+refreshedToken
          };
          response=await fetcher(base+path,{...init,signal:controller.signal,headers:retryHeaders,credentials:'include'});
        }
      }

      let body:Record<string,unknown>;
      try{body=await response.json();}catch{throw new ApiError('NETWORK_ERROR','Chưa xác định được kết quả. Hãy thử lại cùng yêu cầu.');}
      if(!response.ok)throw new ApiError(String(body.code??'INTERNAL_ERROR'),String(body.message??'Không thể hoàn tất yêu cầu'),response.status,body.correlationId as string|undefined);
      return body as T;
    }catch(error){if(error instanceof ApiError)throw error;throw new ApiError('NETWORK_ERROR','Không nhận được phản hồi. Hãy thử lại cùng yêu cầu.');}
    finally{clearTimeout(timer);}
  }
  return call;
}

export function createApi(base='http://127.0.0.1:8080',fetcher:typeof fetch=globalThis.fetch):Api{
  const tokenProvider=()=>getStoredToken()||'demo-customer-token';
  const call=demoTransport(base,fetcher,tokenProvider);
  return{
    events:()=>call('/api/events?size=100'),
    detail:id=>call('/api/events/'+encodeURIComponent(id)),
    availability:id=>call('/api/events/'+encodeURIComponent(id)+'/availability'),
    create:attempt=>call('/api/bookings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':attempt.key},body:JSON.stringify(attempt.payload)}),
    booking:id=>call('/api/bookings/'+encodeURIComponent(id)),
    mine:()=>call('/api/me/bookings?size=100'),
    simulate:attempt=>call('/api/bookings/'+encodeURIComponent(attempt.bookingId)+'/simulate-payment',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({scenario:attempt.scenario,requestId:attempt.requestId})}),
    cancel:id=>call('/api/bookings/'+encodeURIComponent(id)+'/cancel',{method:'POST'}),
    tickets:id=>call('/api/bookings/'+encodeURIComponent(id)+'/tickets'),
    ticketQr:id=>call('/api/tickets/'+encodeURIComponent(id)+'/qr'),
    auth:{
      register:(data)=>call('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      login:(data)=>call('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      refresh:()=>call('/api/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'}}),
      logout:()=>call('/api/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'}}),
      me:()=>call('/api/auth/me')
    },
    contact:{
      submitLead:(data)=>call('/api/contact/leads',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})
    },
    organizer:{
      getProfile:()=>call('/api/organizer/profile'),
      saveProfile:(data)=>call('/api/organizer/profile',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      venues:()=>call('/api/organizer/venues'),
      categories:()=>call('/api/organizer/categories'),
      events:()=>call('/api/organizer/events'),
      event:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)),
      createEvent:(data)=>call('/api/organizer/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      updateEvent:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id),{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      publishEvent:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/publish',{method:'POST'}),
      addTicketType:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/ticket-types',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      staff:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff'),
      assignStaff:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      revokeStaff:(id,staffUserId)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff/'+encodeURIComponent(staffUserId),{method:'DELETE'}),
      staffCandidates:(q)=>call('/api/organizer/staff-candidates'+(q?'?q='+encodeURIComponent(q):''))
    }
  };
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

const PAYMENT_KEY='eventflow.pending-payment.v1';
export function readPaymentAttempt(storage:Store):PaymentAttempt|null{
  try{
    const value=JSON.parse(storage.getItem(PAYMENT_KEY)??'null');const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if(value&&uuid.test(value.bookingId)&&uuid.test(value.requestId)&&['SUCCESS','FAILURE','DELAY_TIMEOUT'].includes(value.scenario))
      return{bookingId:value.bookingId,requestId:value.requestId,scenario:value.scenario};
  }catch{/* Ignore malformed session state. */}
  return null;
}
export function savePaymentAttempt(storage:Store,value:PaymentAttempt|null){if(value)storage.setItem(PAYMENT_KEY,JSON.stringify(value));else storage.removeItem(PAYMENT_KEY);}
