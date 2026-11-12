import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createElement,act} from 'react';
import {createApi} from '../src/api/client.ts';
import type {Seat} from '../src/api/client.ts';

const base=process.env.API_BASE_URL;
if(!base)throw new Error('Run infra/test-part5-ui.ps1: requires an isolated backend with TTL4s and scheduler1s.');
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{App}=await import('../src/app/App.ts');
const title='Seat UI integration '+crypto.randomUUID();
const created=await fetch(base+'/api/admin/events',{method:'POST',headers:{Authorization:'Bearer demo-admin-token','Content-Type':'application/json'},body:JSON.stringify({title,description:'Real seated UI and expiry scheduler',venue:'Test DB',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-06-01T00:00:00Z',startsAt:'2099-07-01T00:00:00Z',status:'PUBLISHED',ticketTypes:[{name:'Seats UI',mode:'SEATED',priceVnd:200000,seats:[{label:'A1'},{label:'A2'},{label:'A3'}]},{name:'GA expiry',mode:'GA',priceVnd:100000,capacity:2}]})});
assert.equal(created.status,201);const event=await created.json();
const sent:{key:string;body:string;status:number;response:Record<string,unknown>}[]=[];
const api=createApi(base,async(input,options)=>{const response=await fetch(input,options);if(options?.method==='POST')sent.push({key:new Headers(options.headers).get('Idempotency-Key')!,body:String(options.body),status:response.status,response:await response.clone().json()});return response;});
const root=createRoot(document.getElementById('root')!);
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,35));});}
async function until(predicate:()=>boolean){for(let i=0;i<400;i++){await settle();if(predicate())return;}throw new Error('UI timed out: '+document.body.textContent);}
function button(text:string){const result=[...document.querySelectorAll('button')].find(b=>b.textContent===text);assert(result,`Missing ${text}`);return result;}
function seat(label:string){return document.querySelector<HTMLButtonElement>(`button[aria-label^="Ghế ${label}:"]`);}
async function seatSnapshot(){const snapshot=await api.availability(event.id);return snapshot.ticketTypes.find(t=>t.mode==='SEATED') as {mode:'SEATED';seats:Seat[]};}
try{
  await settle(()=>root.render(createElement(App,{api,store:dom.window.sessionStorage})));
  await until(()=>[...document.querySelectorAll('article')].some(a=>a.textContent?.includes(title)));
  const card=[...document.querySelectorAll('article')].find(a=>a.textContent?.includes(title))!;
  await settle(()=>card.querySelector('button')!.click());
  const seatType=event.ticketTypes.find((t:{mode:string})=>t.mode==='SEATED');
  await until(()=>!!document.querySelector<HTMLSelectElement>('#ticket-type')?.querySelector(`option[value="${seatType.id}"]`));
  await settle(()=>{const select=document.querySelector<HTMLSelectElement>('#ticket-type')!;select.value=seatType.id;select.dispatchEvent(new dom.window.Event('change',{bubbles:true}));});
  await until(()=>!!seat('A1'));
  await settle(()=>seat('A2')!.click());await settle(()=>seat('A1')!.click());
  await settle(()=>button('Giữ ghế đã chọn').click());await until(()=>!!document.querySelector('.result')?.textContent?.includes('PENDING_PAYMENT'));
  assert.equal(sent[0].status,202);assert.equal(sent[0].response.totalAmountVnd,400000);assert(!('quantity' in JSON.parse(sent[0].body)));
  const first=sent[0];
  await act(async()=>{
    const replay=await fetch(base+'/api/bookings',{method:'POST',headers:{Authorization:'Bearer demo-customer-token','Content-Type':'application/json','Idempotency-Key':first.key},body:first.body});
    assert.equal(replay.status,202);assert.deepEqual(await replay.json(),first.response);
    const snapshot=await seatSnapshot();assert.equal(snapshot.seats.filter(s=>s.status==='HELD').length,2);
    const conflict=await fetch(base+'/api/bookings',{method:'POST',headers:{Authorization:'Bearer demo-customer-token','Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:first.body});
    assert.equal(conflict.status,409);assert.equal((await conflict.json()).code,'INVENTORY_UNAVAILABLE');
  });
  await until(()=>seat('A1')?.disabled===true&&seat('A1')?.textContent?.includes('Đang giữ')===true);
  assert(document.querySelector('.hold-countdown'));
  // Do not call an expiry endpoint or modify the DB: the scheduled worker must do this.
  await until(()=>!!document.querySelector('.result')?.textContent?.includes('EXPIRED'));
  await until(()=>seat('A1')?.disabled===false&&seat('A1')?.textContent?.includes('Trống')===true);
  await act(async()=>{
    const state=await api.booking(String(first.response.bookingId));assert.equal(state.status,'EXPIRED');assert.equal(state.history?.filter(h=>h.toStatus==='EXPIRED').length,1);
    const replay=await fetch(base+'/api/bookings',{method:'POST',headers:{Authorization:'Bearer demo-customer-token','Content-Type':'application/json','Idempotency-Key':first.key},body:first.body});assert.deepEqual(await replay.json(),first.response);
    assert((await seatSnapshot()).seats.every(s=>s.status==='AVAILABLE'));
  });
  await settle(()=>button('Đặt thêm vé').click());
  // Selection remains advisory; explicitly select the now free seats again.
  if(seat('A1')!.getAttribute('aria-pressed')!=='true')await settle(()=>seat('A1')!.click());
  await settle(()=>button('Giữ ghế đã chọn').click());
  await until(()=>sent.length===2&&!!document.querySelector('.result')?.textContent?.includes('PENDING_PAYMENT'));
  assert.equal(sent[1].status,202);assert.notEqual(sent[1].response.bookingId,first.response.bookingId);assert.notEqual(sent[1].key,first.key);
  await until(()=>!!document.querySelector('.result')?.textContent?.includes('EXPIRED'));
  const gaType=event.ticketTypes.find((t:{mode:string})=>t.mode==='GA');
  let gaId='';
  await act(async()=>{gaId=(await createApi(base).create({key:crypto.randomUUID(),payload:{eventId:event.id,ticketTypeId:gaType.id,quantity:2}})).bookingId;});
  let gaExpired=false;
  for(let i=0;i<200&&!gaExpired;i++)await act(async()=>{gaExpired=(await api.booking(gaId)).status==='EXPIRED';if(!gaExpired)await new Promise(resolve=>setTimeout(resolve,50));});
  assert(gaExpired,'GA must also expire through the real scheduler');
  await act(async()=>{const available=(await api.availability(event.id)).ticketTypes.find(t=>t.mode==='GA');assert(available?.mode==='GA');assert.equal(available.available,2);});
  console.log('PASS seats UI: real PostgreSQL/HTTP; select A1+A2;202/400000;replay same booking;clash409;scheduled EXPIRED;seat AVAILABLE;rebook new ID;second EXPIRED;GA expiry returns capacity2.');
}finally{await act(()=>root.unmount());dom.window.close();}
