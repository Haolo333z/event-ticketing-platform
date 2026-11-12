import assert from 'node:assert/strict';
import {test,afterEach} from 'node:test';
import {createElement as h,act,useState} from 'react';
import {JSDOM} from 'jsdom';
import {ApiError,createApi,readPaymentAttempt} from '../api/client.ts';
import type {Api,Booking,PaymentAttempt,Store,Ticket} from '../api/client.ts';

const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{Checkout}=await import('./Checkout.ts');
const booking:Booking={bookingId:'50000000-0000-4000-8000-000000000001',status:'PENDING_PAYMENT',expiresAt:'2099-01-01T00:00:00Z',totalAmountVnd:200000};
const issued:Ticket[]=[{id:'60000000-0000-4000-8000-000000000001',bookingId:booking.bookingId,bookingItemId:'70000000-0000-4000-8000-000000000001',ticketOrdinal:1,code:'EF-server-only-ticket',seatId:'40000000-0000-4000-8000-000000000001',seatLabel:'A1'}];
const roots:ReturnType<typeof createRoot>[]=[];
function store():Store{const values=new Map<string,string>();return{getItem:key=>values.get(key)??null,setItem:(key,value)=>{values.set(key,value);},removeItem:key=>{values.delete(key);}};}
function fixture(overrides:Partial<Api>={}):Api{return{...createApi('http://unused'),simulate:async()=>({paymentIntentId:booking.bookingId,status:'ACCEPTED'}),booking:async()=>({...booking,status:'CONFIRMED'}),tickets:async()=>issued,cancel:async()=>({...booking,status:'CANCELLED'}),...overrides};}
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,0));});}
function button(text:string){const result=[...document.querySelectorAll('button')].find(b=>b.textContent===text);assert(result,`Missing ${text}`);return result;}
async function mount(api:Api,storage=store()){
  let update!:(value:Booking)=>void;const container=document.createElement('div');document.body.append(container);const root=createRoot(container);roots.push(root);
  function Harness(){const[current,setCurrent]=useState(booking);update=setCurrent;return h('div',null,h('p',{'data-state':true},current.status),h(Checkout,{api,store:storage,booking:current,onChange:setCurrent}));}
  await settle(()=>root.render(h(Harness)));return{root,container,store:storage,update:(value:Booking)=>update(value)};
}
afterEach(async()=>{for(const root of roots.splice(0))await act(()=>root.unmount());document.body.innerHTML='';});
test('success uses the real API result and displays server ticket code and seat label',async()=>{
  const calls:PaymentAttempt[]=[];await mount(fixture({simulate:async a=>{calls.push(a);return{paymentIntentId:booking.bookingId,status:'ACCEPTED'};}}));
  await settle(()=>button('Mô phỏng thành công').click());assert.equal(calls.length,1);assert.equal(calls[0].scenario,'SUCCESS');assert.equal(calls[0].bookingId,booking.bookingId);
  assert.match(document.querySelector('[data-state]')!.textContent!,/CONFIRMED/);assert.match(document.querySelector('.ticket-list')!.textContent!,/EF-server-only-ticket/);assert.match(document.querySelector('.ticket-list')!.textContent!,/A1/);
});
test('failure shows the server terminal state without inventing tickets',async()=>{
  await mount(fixture({booking:async()=>({...booking,status:'PAYMENT_FAILED'})}));await settle(()=>button('Mô phỏng thất bại').click());
  assert.match(document.querySelector('[data-state]')!.textContent!,/PAYMENT_FAILED/);assert.equal(document.querySelector('.ticket-list'),null);
});
test('accepted delayed payment never becomes confirmed until server state changes',async()=>{
  const result=await mount(fixture({booking:async()=>booking}));await settle(()=>button('Mô phỏng kết quả đến muộn').click());assert.match(document.querySelector('[data-state]')!.textContent!,/PENDING_PAYMENT/);assert.equal(document.querySelector('.ticket-list'),null);
  await settle(()=>result.update({...booking,status:'EXPIRED'}));assert.match(document.querySelector('[data-state]')!.textContent!,/EXPIRED/);
  await settle(()=>result.update({...booking,status:'REFUND_REQUIRED'}));assert.match(document.body.textContent!,/nghĩa vụ bù trừ/);assert.equal(document.querySelector('.ticket-list'),null);
});
test('double click records a single payment request and locks other actions',async()=>{
  let count=0,finish!:()=>void;const wait=new Promise<void>(resolve=>{finish=resolve;});await mount(fixture({simulate:async()=>{count++;await wait;return{paymentIntentId:booking.bookingId,status:'ACCEPTED'};}}));
  const submit=button('Mô phỏng thành công');await settle(()=>{submit.click();submit.click();});assert.equal(count,1);assert.equal(button('Mô phỏng thất bại').disabled,true);assert.equal(button('Hủy giữ chỗ').disabled,true);await settle(()=>finish());
});
test('503 and reload preserve requestId and scenario until retry succeeds',async()=>{
  const storage=store(),calls:PaymentAttempt[]=[];const first=await mount(fixture({simulate:async a=>{calls.push(structuredClone(a));throw new ApiError('BOOKING_BUSY','Bận',503);}}),storage);
  await settle(()=>button('Mô phỏng thành công').click());assert.deepEqual(readPaymentAttempt(storage),calls[0]);assert.equal(button('Mô phỏng thất bại').disabled,true);
  await act(()=>first.root.unmount());roots.splice(roots.indexOf(first.root),1);first.container.remove();
  await mount(fixture({simulate:async a=>{calls.push(structuredClone(a));return{paymentIntentId:booking.bookingId,status:'ACCEPTED'};}}),storage);await settle(()=>button('Thử lại thanh toán cùng yêu cầu').click());
  assert.deepEqual(calls[0],calls[1]);assert.equal(readPaymentAttempt(storage),null);assert.match(document.querySelector('[data-state]')!.textContent!,/CONFIRMED/);
});
test('cancel uses the API and only displays cancelled after its response',async()=>{
  let called='';await mount(fixture({cancel:async id=>{called=id;return{...booking,status:'CANCELLED'};}}));await settle(()=>button('Hủy giữ chỗ').click());assert.equal(called,booking.bookingId);assert.match(document.querySelector('[data-state]')!.textContent!,/CANCELLED/);assert.equal(document.querySelector('.ticket-list'),null);
});
test('cancel conflict refreshes a concurrently confirmed booking and preserves tickets',async()=>{
  await mount(fixture({cancel:async()=>{throw new ApiError('INVALID_BOOKING_STATE','Đã xử lý',409);}}));await settle(()=>button('Hủy giữ chỗ').click());
  assert.match(document.querySelector('[role=alert]')!.textContent!,/INVALID_BOOKING_STATE/);assert.match(document.querySelector('[data-state]')!.textContent!,/CONFIRMED/);assert.match(document.querySelector('.ticket-list')!.textContent!,/EF-server-only-ticket/);
});
