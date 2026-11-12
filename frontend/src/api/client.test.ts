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
