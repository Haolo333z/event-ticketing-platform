import assert from 'node:assert/strict';
import {test,afterEach} from 'node:test';
import {createElement,act} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {JSDOM} from 'jsdom';
import {ApiError,readAttempt} from '../api/client.ts';
import type {Api,Attempt,Booking,Event,Store} from '../api/client.ts';

const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://127.0.0.1:5173'});
for(const [name,value] of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const {createRoot}=await import('react-dom/client');const {App}=await import('./App.ts');
const eventId='20000000-0000-4000-8000-000000000002',typeId='30000000-0000-4000-8000-000000000002';
const event:Event={id:eventId,title:'Ngày hội GA',description:'Dữ liệu test riêng',venue:'Demo',startsAt:'2099-01-02T00:00:00Z',ticketTypes:[{id:typeId,name:'Vào cửa',mode:'GA',priceVnd:100000,active:true}]};
const accepted:Booking={bookingId:'50000000-0000-4000-8000-000000000001',status:'PENDING_PAYMENT',expiresAt:'2020-01-01T00:00:00Z',totalAmountVnd:499999};
const roots:ReturnType<typeof createRoot>[]=[];
function storage():Store{const values=new Map<string,string>();return{getItem:key=>values.get(key)??null,setItem:(key,value)=>{values.set(key,value);},removeItem:key=>{values.delete(key);}};}
function fixture(overrides:Partial<Api>={}):Api{return{events:async()=>({items:[event]}),detail:async()=>event,availability:async()=>({eventId,asOf:'2026-11-12T00:00:00Z',ticketTypes:[{ticketTypeId:typeId,mode:'GA',available:10}]}),create:async()=>accepted,booking:async()=>accepted,mine:async()=>({items:[]}),...overrides};}
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,0));});}
async function mount(api:Api,store=storage()){const container=document.createElement('div');document.body.append(container);const root=createRoot(container);roots.push(root);await settle(()=>root.render(createElement(App,{api,store})));return{container,root,store};}
function button(text:string){const found=[...document.querySelectorAll('button')].find(b=>b.textContent===text);assert(found,`Missing button ${text}`);return found;}
async function selectGa(){await settle(()=>button('Xem sự kiện').click());}
async function quantity(value:number){const input=document.querySelector<HTMLInputElement>('#quantity')!;await settle(()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value')!.set!.call(input,String(value));input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});}
afterEach(async()=>{for(const root of roots.splice(0))await act(()=>root.unmount());document.body.innerHTML='';});

test('SSR shows a loading state, not fabricated catalog or bookings',()=>{
  const html=renderToStaticMarkup(createElement(App,{api:fixture(),store:storage()}));assert.match(html,/Đang tải sự kiện/);assert.doesNotMatch(html,/Ngày hội GA|50000000/);
});
test('select quantity and show the exact server total and status, then fetch history',async()=>{
  const attempts:Attempt[]=[];let created=false;
  await mount(fixture({create:async attempt=>{attempts.push(attempt);created=true;return accepted;},mine:async()=>({items:created?[accepted]:[]}),booking:async()=>({...accepted,status:'EXPIRED'})}));
  await selectGa();await quantity(2);await settle(()=>button('Giữ vé GA').click());
  assert.equal(attempts.length,1);assert.equal(attempts[0].payload.quantity,2);assert.deepEqual(Object.keys(attempts[0].payload).sort(),['eventId','quantity','ticketTypeId']);
  assert.match(document.body.textContent??'',/499[.\s]999/);assert.match(document.body.textContent??'',/PENDING_PAYMENT/);
  assert.equal(document.querySelectorAll('.history-list li').length,1);
  await settle(()=>button('Cập nhật booking').click());assert.match(document.body.textContent??'',/EXPIRED/);
});
test('inventory conflict displays the error code and releases the pending form',async()=>{
  const {store}=await mount(fixture({create:async()=>{throw new ApiError('INVENTORY_UNAVAILABLE','Không còn đủ vé',409);}}));await selectGa();await settle(()=>button('Giữ vé GA').click());
  assert.match(document.querySelector('[role=alert]')?.textContent??'',/INVENTORY_UNAVAILABLE/);assert.equal(readAttempt(store),null);assert.equal(document.querySelector<HTMLInputElement>('#quantity')!.disabled,false);
});
test('503 preserves the same key and payload until a successful retry',async()=>{
  const attempts:Attempt[]=[];const store=storage();await mount(fixture({create:async attempt=>{assert.deepEqual(readAttempt(store),attempt);attempts.push(structuredClone(attempt));if(attempts.length===1)throw new ApiError('BOOKING_BUSY','Bận',503);return accepted;}}),store);
  await selectGa();await settle(()=>button('Giữ vé GA').click());assert.equal(document.querySelector<HTMLInputElement>('#quantity')!.disabled,true);
  await settle(()=>button('Thử lại cùng yêu cầu').click());assert.equal(attempts.length,2);assert.deepEqual(attempts[0],attempts[1]);assert.equal(readAttempt(store),null);
});
test('a network error survives remount and never triggers an automatic new POST',async()=>{
  const attempts:Attempt[]=[];const store=storage();const first=await mount(fixture({create:async a=>{attempts.push(a);throw new ApiError('NETWORK_ERROR','Mất mạng');}}),store);
  await selectGa();await settle(()=>button('Giữ vé GA').click());await act(()=>first.root.unmount());roots.splice(roots.indexOf(first.root),1);first.container.remove();
  await mount(fixture({create:async a=>{attempts.push(a);return accepted;}}),store);assert.equal(attempts.length,1);
  await settle(()=>button('Thử lại cùng yêu cầu').click());assert.equal(attempts.length,2);assert.deepEqual(attempts[0],attempts[1]);
});
test('double click sends one request and disables controls until it settles',async()=>{
  let calls=0;let finish!:(value:Booking)=>void;const result=new Promise<Booking>(resolve=>{finish=resolve;});
  await mount(fixture({create:async()=>{calls++;return result;}}));await selectGa();const submit=button('Giữ vé GA');
  await settle(()=>{submit.click();submit.click();});assert.equal(calls,1);assert.equal(document.querySelector<HTMLInputElement>('#quantity')!.disabled,true);
  await settle(()=>finish(accepted));assert.match(document.body.textContent??'',/PENDING_PAYMENT/);assert.equal(button('Giữ vé GA').disabled,true);
  await settle(()=>button('Đặt thêm vé').click());assert.equal(button('Giữ vé GA').disabled,false);
});
test('sold-out availability disables booking and invalid quantities cannot be sent',async()=>{
  await mount(fixture({availability:async()=>({eventId,asOf:'2026-11-12T00:00:00Z',ticketTypes:[{ticketTypeId:typeId,mode:'GA',available:0}]})}));
  await selectGa();assert.match(document.body.textContent??'',/Hết vé/);assert.equal(button('Giữ vé GA').disabled,true);await quantity(7);assert.equal(button('Giữ vé GA').disabled,true);
});

const seatList=[
  {id:'40000000-0000-4000-8000-000000000001',label:'A1',status:'AVAILABLE' as const},
  {id:'40000000-0000-4000-8000-000000000002',label:'A2',status:'AVAILABLE' as const},
  {id:'40000000-0000-4000-8000-000000000003',label:'A3',status:'HELD' as const},
  {id:'40000000-0000-4000-8000-000000000004',label:'A4',status:'SOLD' as const},
];
function seated(overrides:Partial<Api>={}):Api{
  const detail:Event={...event,title:'Sự kiện ghế',ticketTypes:[{...event.ticketTypes[0],mode:'SEATED'}]};
  return fixture({events:async()=>({items:[detail]}),detail:async()=>detail,availability:async()=>({eventId,asOf:'2026-11-12T00:00:00Z',ticketTypes:[{ticketTypeId:typeId,mode:'SEATED',seats:seatList}]}),...overrides});
}
function seat(label:string){return document.querySelector<HTMLButtonElement>(`button[aria-label^="Ghế ${label}:"]`)!;}
test('seat map distinguishes available, held and sold and submits only selected IDs',async()=>{
  const calls:Attempt[]=[];await mount(seated({create:async a=>{calls.push(a);return accepted;}}));await selectGa();
  assert.equal(seat('A3').disabled,true);assert.equal(seat('A4').disabled,true);assert.equal(button('Giữ ghế đã chọn').disabled,true);
  await settle(()=>seat('A2').click());await settle(()=>seat('A1').click());assert.equal(seat('A1').getAttribute('aria-pressed'),'true');
  await settle(()=>button('Giữ ghế đã chọn').click());assert.equal(calls.length,1);assert.deepEqual(calls[0].payload,{eventId,ticketTypeId:typeId,seatIds:[seatList[0].id,seatList[1].id]});
  assert.match(document.querySelector('.result')?.textContent??'',/499[.\s]999/);
});
test('seat 503 retry after reload preserves the original selection and key',async()=>{
  const store=storage(),calls:Attempt[]=[];const first=await mount(seated({create:async a=>{calls.push(a);throw new ApiError('BOOKING_BUSY','Bận',503);}}),store);
  await selectGa();await settle(()=>seat('A1').click());await settle(()=>button('Giữ ghế đã chọn').click());assert.equal(seat('A2').disabled,true);
  await act(()=>first.root.unmount());roots.splice(roots.indexOf(first.root),1);first.container.remove();
  await mount(seated({create:async a=>{calls.push(a);return accepted;}}),store);await settle(()=>button('Thử lại cùng yêu cầu').click());assert.deepEqual(calls[0],calls[1]);assert.equal(readAttempt(store),null);
});
test('overdue conflict refreshes seat map and clears the rejected selection',async()=>{
  let rejected=false;await mount(seated({create:async()=>{rejected=true;throw new ApiError('HOLD_EXPIRY_PROCESSING','Đang nhả ghế',409);},availability:async()=>({eventId,asOf:'2026-11-12T00:00:00Z',ticketTypes:[{ticketTypeId:typeId,mode:'SEATED',seats:seatList.map((s,i)=>({...s,status:rejected&&i===0?'HELD':s.status}))}]})}));
  await selectGa();await settle(()=>seat('A1').click());await settle(()=>button('Giữ ghế đã chọn').click());assert.match(document.querySelector('[role=alert]')?.textContent??'',/HOLD_EXPIRY_PROCESSING/);assert.equal(seat('A1').disabled,true);assert.equal(button('Giữ ghế đã chọn').disabled,true);
});
test('countdown at zero never invents EXPIRED before a server response',async()=>{
  await mount(seated({booking:async()=>({...accepted,status:'EXPIRED'})}));await selectGa();await settle(()=>seat('A1').click());await settle(()=>button('Giữ ghế đã chọn').click());
  assert.match(document.querySelector('.hold-countdown')?.textContent??'',/Đang chờ máy chủ/);assert.match(document.querySelector('.result')?.textContent??'',/PENDING_PAYMENT/);
  await settle(()=>button('Cập nhật booking').click());assert.match(document.querySelector('.result')?.textContent??'',/EXPIRED/);assert.equal(document.querySelector('.hold-countdown'),null);
});
