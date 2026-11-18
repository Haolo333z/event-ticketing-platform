import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createApi,ApiError,saveAttempt,readAttempt,unknownOutcome,readPaymentAttempt,savePaymentAttempt} from './client.ts';
import type {Attempt} from './client.ts';
const attempt:Attempt={key:'00000000-0000-4000-8000-000000000001',payload:{eventId:'20000000-0000-4000-8000-000000000002',ticketTypeId:'30000000-0000-4000-8000-000000000002',quantity:2}};
test('HTTP adapter sends the exact command and stable idempotency header',async()=>{
  const requests:RequestInit[]=[];
  const api=createApi('http://api.test',async(input,options)=>{assert.equal(input,'http://api.test/api/bookings');requests.push(options!);return new Response(JSON.stringify({bookingId:'server'}),{status:202,headers:{'Content-Type':'application/json'}});});
  await api.create(attempt);await api.create(attempt);assert.equal(requests.length,2);
  for(const request of requests){assert.equal(request.method,'POST');assert.equal(new Headers(request.headers).get('Idempotency-Key'),attempt.key);assert.deepEqual(JSON.parse(request.body as string),attempt.payload);}
});
test('HTTP error codes and correlation IDs are preserved',async()=>{
  const api=createApi('',async()=>new Response(JSON.stringify({code:'INVENTORY_UNAVAILABLE',message:'Hết vé',correlationId:'trace'}),{status:409}));
  await assert.rejects(api.create(attempt),e=>e instanceof ApiError&&e.status===409&&e.code==='INVENTORY_UNAVAILABLE'&&e.correlationId==='trace'&&!unknownOutcome(e));
});
test('transport or malformed responses have unknown outcomes, while 503 is retryable with the same key',async()=>{
  for(const fetcher of [async()=>{throw new Error('offline');},async()=>new Response('not json')])await assert.rejects(createApi('',fetcher).create(attempt),e=>e instanceof ApiError&&unknownOutcome(e));
  assert(unknownOutcome(new ApiError('BOOKING_BUSY','busy',503)));
});
test('only the pending command is stored; malformed session data is ignored',()=>{
  let value:string|null=null;const store={getItem:()=>value,setItem:(_key:string,v:string)=>{value=v;},removeItem:()=>{value=null;}};
  saveAttempt(store,attempt);assert.deepEqual(readAttempt(store),attempt);assert(!value!.includes('token'));saveAttempt(store,null);assert.equal(readAttempt(store),null);
  value='{';assert.equal(readAttempt(store),null);value=JSON.stringify({...attempt,payload:{...attempt.payload,quantity:9}});assert.equal(readAttempt(store),null);
});

test('seat commands survive reload with the exact key and reject duplicate or mixed selections',()=>{
  let value:string|null=null;const store={getItem:()=>value,setItem:(_key:string,v:string)=>{value=v;},removeItem:()=>{value=null;}};
  const seats:Attempt={key:attempt.key,payload:{eventId:attempt.payload.eventId,ticketTypeId:attempt.payload.ticketTypeId,seatIds:['40000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000002']}};
  saveAttempt(store,seats);assert.deepEqual(readAttempt(store),seats);
  value=JSON.stringify({...seats,payload:{...seats.payload,quantity:1}});assert.equal(readAttempt(store),null);
  value=JSON.stringify({...seats,payload:{...seats.payload,seatIds:[seats.payload.seatIds![0],seats.payload.seatIds![0]]}});assert.equal(readAttempt(store),null);
});
test('payment adapter sends only scenario/requestId and cancellation has no body',async()=>{
  const calls:{path:string;init:RequestInit}[]=[];const api=createApi('',async(path,init)=>{calls.push({path:String(path),init:init!});return new Response('{}',{status:200});});
  const payment={bookingId:attempt.payload.eventId,requestId:attempt.key,scenario:'SUCCESS' as const};await api.simulate(payment);await api.cancel(payment.bookingId);await api.tickets(payment.bookingId);
  assert.deepEqual(JSON.parse(String(calls[0].init.body)),{scenario:'SUCCESS',requestId:attempt.key});assert.equal(calls[1].init.method,'POST');assert.equal(calls[1].init.body,undefined);assert(calls[2].path.endsWith('/tickets'));
});
test('payment storage preserves only the command and ignores invalid sessions',()=>{
  let value:string|null=null;const storage={getItem:()=>value,setItem:(_key:string,v:string)=>{value=v;},removeItem:()=>{value=null;}};
  const payment={bookingId:attempt.payload.eventId,requestId:attempt.key,scenario:'DELAY_TIMEOUT' as const};savePaymentAttempt(storage,payment);assert.deepEqual(readPaymentAttempt(storage),payment);assert(!value!.includes('token'));value=JSON.stringify({...payment,scenario:'fake'});assert.equal(readPaymentAttempt(storage),null);
});

test('auth methods refresh and logout send correct endpoint and credentials: include',async()=>{
  const calls:{url:string;init:RequestInit}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),init:init!});
    if(String(url).endsWith('/refresh')){
      return new Response(JSON.stringify({token:'new-access-jwt',user:{id:'u1',displayName:'Tester',email:'t@test.com',role:'CUSTOMER',roles:['CUSTOMER']}}),{status:200});
    }
    if(String(url).endsWith('/logout')){
      return new Response(JSON.stringify({success:true,message:'Đã đăng xuất'}),{status:200});
    }
    return new Response('{}',{status:200});
  });
  const refreshRes=await api.auth!.refresh();
  assert.equal(refreshRes.token,'new-access-jwt');
  assert.equal(calls[0].url,'http://api.test/api/auth/refresh');
  assert.equal(calls[0].init.credentials,'include');

  const logoutRes=await api.auth!.logout();
  assert.equal(logoutRes.success,true);
  assert.equal(calls[1].url,'http://api.test/api/auth/logout');
  assert.equal(calls[1].init.credentials,'include');
});

test('401 interceptor automatically refreshes token and retries original request',async()=>{
  let callCount=0;
  const calls:{url:string;auth?:string}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    callCount++;
    const headers=new Headers(init?.headers);
    calls.push({url:String(url),auth:headers.get('Authorization')??undefined});

    if(String(url).endsWith('/api/me/bookings?size=100')){
      if(callCount===1){
        return new Response(JSON.stringify({code:'EXPIRED_TOKEN',message:'Token expired'}),{status:401});
      }
      return new Response(JSON.stringify({items:[]}),{status:200});
    }
    if(String(url).endsWith('/api/auth/refresh')){
      return new Response(JSON.stringify({
        token:'refreshed-token-xyz',
        user:{id:'user-1',displayName:'User One',email:'u1@test.com',role:'CUSTOMER',roles:['CUSTOMER']}
      }),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const res=await api.mine();
  assert.deepEqual(res,{items:[]});
  assert.equal(calls.length,3); // 1: original GET /mine (401), 2: POST /refresh, 3: retried GET /mine with new token
  assert.equal(calls[0].url,'http://api.test/api/me/bookings?size=100');
  assert.equal(calls[1].url,'http://api.test/api/auth/refresh');
  assert.equal(calls[2].url,'http://api.test/api/me/bookings?size=100');
  assert.equal(calls[2].auth,'Bearer refreshed-token-xyz');
});

test('organizer api adapter dispatches correct paths and payloads',async()=>{
  const calls:{url:string;method:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method||'GET',body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).endsWith('/api/organizer/profile')){
      return new Response(JSON.stringify({id:'p1',organizationName:'Org Test',contactEmail:'org@test.com',phone:'0901',status:'ACTIVE'}),{status:200});
    }
    if(String(url).endsWith('/api/organizer/venues')){
      return new Response(JSON.stringify([{id:'v1',name:'Nhà hát Lớn',capacity:600}]),{status:200});
    }
    if(String(url).endsWith('/api/organizer/categories')){
      return new Response(JSON.stringify([{id:'CONCERT',name:'Concert'}]),{status:200});
    }
    if(String(url).endsWith('/api/organizer/events')){
      return new Response(JSON.stringify([{id:'e1',title:'Event 1',status:'DRAFT'}]),{status:200});
    }
    if(String(url).endsWith('/publish')){
      return new Response(JSON.stringify({id:'e1',title:'Event 1',status:'PUBLISHED'}),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const p=await api.organizer!.getProfile();
  assert.equal(p?.organizationName,'Org Test');
  assert.equal(calls[0].url,'http://api.test/api/organizer/profile');

  const venues=await api.organizer!.venues();
  assert.equal(venues.length,1);
  assert.equal(venues[0].name,'Nhà hát Lớn');

  const categories=await api.organizer!.categories();
  assert.equal(categories.length,1);
  assert.equal(categories[0].id,'CONCERT');

  const evts=await api.organizer!.events();
  assert.equal(evts.length,1);
  assert.equal(evts[0].status,'DRAFT');

  const pub=await api.organizer!.publishEvent('e1');
  assert.equal(pub.status,'PUBLISHED');
  assert.equal(calls[4].method,'POST');
  assert.equal(calls[4].url,'http://api.test/api/organizer/events/e1/publish');
});


test('v3 api adapter builds best-seats, stream, calendar and discovery URLs accurately',async()=>{
  const calls:string[]=[];
  const api=createApi('http://api.test',async(url)=>{
    calls.push(String(url));
    if(String(url).includes('/best-available')){
      return new Response(JSON.stringify({eventId:'e1',ticketTypeId:'t1',quantity:2,contiguous:true,rowLabel:'A',centerDistance:0,seats:[{id:'s1',label:'A5',rowLabel:'A',seatNumber:5},{id:'s2',label:'A6',rowLabel:'A',seatNumber:6}],explanation:'OK'}),{status:200});
    }
    return new Response(JSON.stringify({items:[],totalElements:0}),{status:200});
  });
  const best=await api.v3!.bestSeats('e1','t1',2);
  assert.equal(best.contiguous,true);
  assert.equal(best.seats.length,2);
  assert.equal(calls[0],'http://api.test/api/events/e1/seats/best-available?ticketTypeId=t1&quantity=2');
  assert.equal(api.v3!.streamUrl('e1'),'http://api.test/api/events/e1/stream');
  assert.equal(api.v3!.calendarUrl('e1'),'http://api.test/api/events/e1/calendar.ics');
  await api.v3!.discoverEvents({format:'ONLINE',minPrice:100000,maxPrice:500000,q:'AI'});
  assert.equal(calls[1],'http://api.test/api/v3/events?q=AI&format=ONLINE&minPrice=100000&maxPrice=500000');
});
