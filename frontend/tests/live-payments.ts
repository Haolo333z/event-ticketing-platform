import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createElement,act} from 'react';
import {createApi} from '../src/api/client.ts';
import type {Ticket} from '../src/api/client.ts';
const base=process.env.API_BASE_URL;if(!base)throw new Error('Run infra/test-part6-ui.ps1 with isolated PostgreSQL, TTL4s and scheduler1s.');
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{App}=await import('../src/app/App.ts');
const title='Payment UI '+crypto.randomUUID();
const response=await fetch(base+'/api/admin/events',{method:'POST',headers:{Authorization:'Bearer demo-admin-token','Content-Type':'application/json'},body:JSON.stringify({title,description:'Real checkout integration',venue:'Isolated DB',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-06-01T00:00:00Z',startsAt:'2099-07-01T00:00:00Z',status:'PUBLISHED',ticketTypes:[{name:'GA UI',mode:'GA',priceVnd:100000,capacity:12},{name:'Seats UI',mode:'SEATED',priceVnd:200000,seats:[{label:'A1'},{label:'A2'}]}]})});assert.equal(response.status,201);const event=await response.json();
const writes:{path:string;body:string;status:number;response:Record<string,unknown>}[]=[];
const api=createApi(base,async(input,options)=>{const result=await fetch(input,options);if(options?.method==='POST')writes.push({path:String(input),body:String(options.body??''),status:result.status,response:await result.clone().json()});return result;});
const root=createRoot(document.getElementById('root')!);const gaType=event.ticketTypes.find((t:{mode:string})=>t.mode==='GA'),seatType=event.ticketTypes.find((t:{mode:string})=>t.mode==='SEATED');
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,30));});}
async function until(predicate:()=>boolean){for(let i=0;i<500;i++){await settle();if(predicate())return;}throw new Error('Checkout timed out: '+document.body.textContent);}
function button(text:string){const result=[...document.querySelectorAll('button')].find(b=>b.textContent===text);assert(result,`Missing ${text}`);return result;}
function status(state:string){return document.querySelector('.result dl')?.textContent?.includes(state)===true;}
async function selectType(id:string){await settle(()=>{const select=document.querySelector<HTMLSelectElement>('#ticket-type')!;select.value=id;select.dispatchEvent(new dom.window.Event('change',{bubbles:true}));});}
async function quantity(value:number){await settle(()=>{const input=document.querySelector<HTMLInputElement>('#quantity')!;Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value')!.set!.call(input,String(value));input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});}
function lastBooking(){return writes.filter(w=>w.path.endsWith('/api/bookings')).at(-1)!.response.bookingId as string;}
async function newGa(quantityValue:number){if(document.querySelector('.result'))await settle(()=>button('Đặt thêm vé').click());await selectType(gaType.id);await quantity(quantityValue);await settle(()=>button('Giữ vé GA').click());await until(()=>status('PENDING_PAYMENT'));return lastBooking();}
try{
  await settle(()=>root.render(createElement(App,{api,store:dom.window.sessionStorage})));
  await until(()=>[...document.querySelectorAll('article')].some(a=>a.textContent?.includes(title)));
  const card=[...document.querySelectorAll('article')].find(a=>a.textContent?.includes(title))!;await settle(()=>card.querySelector('button')!.click());await until(()=>!!document.querySelector<HTMLSelectElement>('#ticket-type')?.querySelector(`option[value="${gaType.id}"]`));
  const gaId=await newGa(3);await settle(()=>button('Mô phỏng thành công').click());await until(()=>status('CONFIRMED')&&document.querySelectorAll('.ticket-list li').length===3);
  let gaTickets:Ticket[]=[];
  await act(async()=>{
    gaTickets=await api.tickets(gaId);assert.equal(gaTickets.length,3);assert(gaTickets.every(t=>t.seatId===null&&t.seatLabel===null));
    const command=writes.find(w=>w.path.endsWith('/simulate-payment'))!;
    const replay=await fetch(command.path,{method:'POST',headers:{Authorization:'Bearer demo-customer-token','Content-Type':'application/json'},body:command.body});assert.equal(replay.status,202);assert.deepEqual(await replay.json(),command.response);assert.deepEqual(await api.tickets(gaId),gaTickets);
  });
  await settle(()=>button('Đặt thêm vé').click());await selectType(seatType.id);await until(()=>document.querySelectorAll('.seat').length===2);
  await settle(()=>{for(const seat of document.querySelectorAll<HTMLButtonElement>('.seat'))seat.click();});await settle(()=>button('Giữ ghế đã chọn').click());await until(()=>status('PENDING_PAYMENT'));
  const seatId=lastBooking();await settle(()=>button('Mô phỏng thành công').click());await until(()=>status('CONFIRMED')&&document.querySelectorAll('.ticket-list li').length===2);
  await act(async()=>{const issued=await api.tickets(seatId);assert.equal(issued.length,2);assert.deepEqual(new Set(issued.map(t=>t.seatLabel)),new Set(['A1','A2']));assert(issued.every(t=>t.ticketOrdinal===1));assert.equal(new Set([...gaTickets,...issued].map(t=>t.code)).size,5);});
  const failed=await newGa(1);await settle(()=>button('Mô phỏng thất bại').click());await until(()=>status('PAYMENT_FAILED'));assert.equal(document.querySelector('.ticket-list'),null);
  const delayed=await newGa(1);await settle(()=>button('Mô phỏng kết quả đến muộn').click());
  await until(()=>status('REFUND_REQUIRED'));assert.match(document.querySelector('.checkout')!.textContent!,/nghĩa vụ bù trừ/);assert.equal(document.querySelector('.ticket-list'),null);
  await act(async()=>{const current=await api.booking(delayed);assert.deepEqual(current.history?.map(h=>h.toStatus),['PENDING_PAYMENT','EXPIRED','REFUND_REQUIRED']);assert.equal((await api.tickets(delayed)).length,0);assert.equal((await api.tickets(failed)).length,0);});
  const cancelled=await newGa(1);await settle(()=>button('Hủy giữ chỗ').click());await until(()=>status('CANCELLED'));
  await act(async()=>{assert.equal((await api.cancel(cancelled)).status,'CANCELLED');const snapshot=await api.availability(event.id);const ga=snapshot.ticketTypes.find(t=>t.mode==='GA');assert(ga?.mode==='GA');assert.equal(ga.available,9);const seated=snapshot.ticketTypes.find(t=>t.mode==='SEATED');assert(seated?.mode==='SEATED');assert(seated.seats.every(s=>s.status==='SOLD'));});
  assert(writes.filter(w=>w.path.endsWith('/cancel')).every(w=>w.body===''));
  console.log('PASS checkout UI: real HTTP/PostgreSQL; GA3 and seats2 CONFIRMED with5unique tickets; duplicate request unchanged; FAILURE/CANCELLED release; delayed EXPIRED->REFUND_REQUIRED with0tickets; GAavailable9/seatsSOLD.');
}finally{await act(()=>root.unmount());dom.window.close();}
