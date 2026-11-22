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

test('oauth api adapter retrieves google config and calls callback correctly',async()=>{
  const calls:{url:string;method?:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method,body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).endsWith('/oauth/google/config')){
      return new Response(JSON.stringify({
        provider:'GOOGLE',
        mode:'DEMO',
        clientId:'',
        demoAccounts:[{id:'google-demo-customer',email:'customer.google@eventflow.vn',name:'Nguyen Van Google',avatarUrl:''}]
      }),{status:200});
    }
    if(String(url).endsWith('/oauth/google/callback')){
      return new Response(JSON.stringify({
        token:'oauth-access-token',
        user:{id:'user-oauth',displayName:'Nguyen Van Google',email:'customer.google@eventflow.vn',role:'CUSTOMER',roles:['CUSTOMER']}
      }),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const config=await api.oauth!.getGoogleConfig();
  assert.equal(config.provider,'GOOGLE');
  assert.equal(config.mode,'DEMO');
  assert.equal(config.demoAccounts.length,1);
  assert.equal(calls[0].url,'http://api.test/api/auth/oauth/google/config');

  const auth=await api.oauth!.googleCallback({demoAccountId:'google-demo-customer'});
  assert.equal(auth.token,'oauth-access-token');
  assert.equal(auth.user.email,'customer.google@eventflow.vn');
  assert.equal(calls[1].method,'POST');
  assert.equal(calls[1].body.demoAccountId,'google-demo-customer');
});

test('seatview, reminders and refundProgress api adapters dispatch correct endpoints',async()=>{
  const calls:{url:string;method?:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method,body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).includes('/seats/s1/view')){
      return new Response(JSON.stringify({seatId:'s1',seatLabel:'A5',distanceMeters:10.5,viewQualityScore:96}),{status:200});
    }
    if(String(url).endsWith('/reminders')&&init?.method==='POST'){
      return new Response(JSON.stringify({id:'r1',message:'OK'}),{status:200});
    }
    if(String(url).includes('/refund-progress')){
      return new Response(JSON.stringify({bookingId:'b1',bookingStatus:'REFUND_REQUIRED',steps:[{stepNumber:1,title:'Tiếp nhận',status:'COMPLETED'}]}),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const seatView=await api.seats!.getView('e1','s1');
  assert.equal(seatView.seatLabel,'A5');
  assert.equal(seatView.viewQualityScore,96);
  assert.equal(calls[0].url,'http://api.test/api/events/e1/seats/s1/view');

  const reminder=await api.reminders!.register('e1',15);
  assert.equal(reminder.id,'r1');
  assert.equal(calls[1].method,'POST');
  assert.equal(calls[1].body.remindBeforeMinutes,15);

  const refund=await api.refunds!.getProgress('b1');
  assert.equal(refund.bookingStatus,'REFUND_REQUIRED');
  assert.equal(refund.steps.length,1);
  assert.equal(calls[2].url,'http://api.test/api/me/bookings/b1/refund-progress');
});

test('ticket transfer api adapter handles initiate, lookup and accept correctly',async()=>{
  const calls:{url:string;method?:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method,body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).endsWith('/transfers/initiate')){
      return new Response(JSON.stringify({id:'trf-1',transferToken:'TRF-TESTTOKEN',status:'PENDING',toEmail:'friend@test.com'}),{status:200});
    }
    if(String(url).includes('/transfers/lookup/TRF-TESTTOKEN')){
      return new Response(JSON.stringify({id:'trf-1',transferToken:'TRF-TESTTOKEN',ticketCode:'EF-OLD',eventTitle:'Rock Concert'}),{status:200});
    }
    if(String(url).includes('/transfers/accept/TRF-TESTTOKEN')){
      return new Response(JSON.stringify({transferId:'trf-1',status:'ACCEPTED',newTicketCode:'EF-TRF-NEW'}),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const initiated=await api.transfers!.initiate({ticketId:'t1',toEmail:'friend@test.com'});
  assert.equal(initiated.id,'trf-1');
  assert.equal(initiated.transferToken,'TRF-TESTTOKEN');
  assert.equal(calls[0].url,'http://api.test/api/transfers/initiate');

  const detail=await api.transfers!.lookup('TRF-TESTTOKEN');
  assert.equal(detail.ticketCode,'EF-OLD');

  const accepted=await api.transfers!.accept('TRF-TESTTOKEN');
  assert.equal(accepted.status,'ACCEPTED');
  assert.equal(accepted.newTicketCode,'EF-TRF-NEW');
});

test('resale api adapter handles create, list, reserve and settle correctly',async()=>{
  const calls:{url:string;method?:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method,body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).endsWith('/resale/listings')&&init?.method==='POST'){
      return new Response(JSON.stringify({id:'rsl-1',ticketId:'t1',askingPriceVnd:450000,status:'LISTED'}),{status:200});
    }
    if(String(url).includes('/resale-listings')){
      return new Response(JSON.stringify({items:[{id:'rsl-1',askingPriceVnd:450000}],total:1}),{status:200});
    }
    if(String(url).includes('/reserve')){
      return new Response(JSON.stringify({listingId:'rsl-1',status:'RESERVED'}),{status:200});
    }
    if(String(url).includes('/settle')){
      return new Response(JSON.stringify({listingId:'rsl-1',status:'SOLD',newTicketCode:'EF-RSL-NEW'}),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const created=await api.resale!.create({ticketId:'t1',askingPriceVnd:450000});
  assert.equal(created.id,'rsl-1');
  assert.equal(created.status,'LISTED');

  const list=await api.resale!.listForEvent('ev-1');
  assert.equal(list.total,1);

  const reserved=await api.resale!.reserve('rsl-1');
  assert.equal(reserved.status,'RESERVED');

  const settled=await api.resale!.settle('rsl-1');
  assert.equal(settled.status,'SOLD');
  assert.equal(settled.newTicketCode,'EF-RSL-NEW');
});

test('profile and received transfers api adapters dispatch correct endpoints and payloads',async()=>{
  const calls:{url:string;method?:string;body?:any}[]=[];
  const api=createApi('http://api.test',async(url,init)=>{
    calls.push({url:String(url),method:init?.method,body:init?.body?JSON.parse(String(init.body)):undefined});
    if(String(url).endsWith('/api/me/profile')&&!init?.method){
      return new Response(JSON.stringify({id:'u-1',email:'user@test.com',displayName:'Test User',roles:['CUSTOMER']}),{status:200});
    }
    if(String(url).endsWith('/api/me/profile')&&init?.method==='PUT'){
      return new Response(JSON.stringify({id:'u-1',displayName:'Updated User'}),{status:200});
    }
    if(String(url).endsWith('/api/me/change-password')){
      return new Response(JSON.stringify({success:true,message:'Đổi mật khẩu thành công'}),{status:200});
    }
    if(String(url).endsWith('/api/me/vat-defaults')&&!init?.method){
      return new Response(JSON.stringify({companyName:'Công ty ABC',taxCode:'0101234567'}),{status:200});
    }
    if(String(url).endsWith('/api/me/sessions')&&!init?.method){
      return new Response(JSON.stringify({sessions:[{familyId:'f-1',userAgent:'Chrome',current:true}],total:1}),{status:200});
    }
    if(String(url).includes('/api/me/sessions/f-1')&&init?.method==='DELETE'){
      return new Response(JSON.stringify({success:true}),{status:200});
    }
    if(String(url).endsWith('/api/me/sessions/revoke-others')&&init?.method==='DELETE'){
      return new Response(JSON.stringify({success:true}),{status:200});
    }
    if(String(url).endsWith('/api/me/transfers/received')){
      return new Response(JSON.stringify({items:[{id:'trf-2',ticketCode:'EF-REC'}],total:1}),{status:200});
    }
    return new Response('{}',{status:200});
  });

  const prof=await api.profile!.get();
  assert.equal(prof.email,'user@test.com');
  assert.equal(calls[0].url,'http://api.test/api/me/profile');

  const updated=await api.profile!.update({displayName:'Updated User'});
  assert.equal(updated.displayName,'Updated User');
  assert.equal(calls[1].method,'PUT');

  const cp=await api.profile!.changePassword({newPassword:'newPassword123'});
  assert.equal(cp.success,true);
  assert.equal(calls[2].method,'POST');

  const vat=await api.profile!.vatDefaults();
  assert.equal(vat.taxCode,'0101234567');

  const sess=await api.profile!.sessions();
  assert.equal(sess.total,1);
  assert.equal(sess.sessions[0].current,true);

  await api.profile!.revokeSession('f-1');
  assert.equal(calls[5].method,'DELETE');

  await api.profile!.revokeOtherSessions();
  assert.equal(calls[6].method,'DELETE');

  const received=await api.transfers!.received();
  assert.equal(received.total,1);
  assert.equal(received.items[0].ticketCode,'EF-REC');
});
