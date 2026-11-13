import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';
import {createElement,act} from 'react';
import {createApi,ApiError} from '../src/api/client.ts';
import {createAdminApi} from '../src/api/admin.ts';
import type {AdminEvent} from '../src/api/admin.ts';

const base=process.env.API_BASE_URL;if(!base)throw new Error('Run infra/test-part8-ui.ps1 with its isolated database and Kafka topic.');
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{DemoShell}=await import('../src/app/DemoShell.ts');
const writes:{path:string;method:string;body:Record<string,unknown>;status:number;response:Record<string,unknown>}[]=[];
const eventPages:{page:number;status:number;items:AdminEvent[];totalElements:number}[]=[];
const fetcher:typeof fetch=async(input,init)=>{
  const response=await fetch(input,init);const url=new URL(String(input));
  if((!init?.method||init.method==='GET')&&url.pathname==='/api/admin/events'&&response.ok){const body=await response.clone().json();eventPages.push({page:Number(url.searchParams.get('page')??0),status:response.status,...body});}
  if(init?.method&&init.method!=='GET')writes.push({path:url.pathname,method:init.method,body:init.body?JSON.parse(String(init.body)):{},status:response.status,response:await response.clone().json()});return response;
};
const api=createApi(base,fetcher),admin=createAdminApi(base,fetcher);
// Catalog fixtures make pagination observable; they do not fabricate UI responses.
for(let i=0;i<20;i++)await admin.create({title:'Pagination fixture '+i,description:'Isolated T7 test',venue:'Local',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-01-01T00:00:00Z',startsAt:'2099-01-02T00:00:00Z',status:'DRAFT',ticketTypes:[{name:'GA',mode:'GA',priceVnd:100000,capacity:6}]});
const root=createRoot(document.getElementById('root')!);
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,30));});}
async function until(predicate:()=>boolean){for(let i=0;i<700;i++){await settle();if(predicate())return;}throw new Error('Admin UI timed out: '+document.body.textContent);}
function button(text:string){const b=[...document.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent===text);assert(b,'Missing button '+text);return b;}
async function input(id:string,value:string){const el=document.querySelector<HTMLInputElement|HTMLTextAreaElement>('#'+id)!;assert(el,'Missing field '+id);await settle(()=>{Object.getOwnPropertyDescriptor(el.tagName==='TEXTAREA'?dom.window.HTMLTextAreaElement.prototype:dom.window.HTMLInputElement.prototype,'value')!.set!.call(el,value);el.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});}
async function select(id:string,value:string){const el=document.querySelector<HTMLSelectElement>('#'+id)!;await settle(()=>{el.value=value;el.dispatchEvent(new dom.window.Event('change',{bubbles:true}));});}
const title='T7 live '+crypto.randomUUID();let created:AdminEvent;let bookingId='';
try{
  await settle(()=>root.render(createElement(DemoShell,{customer:api,admin,store:dom.window.sessionStorage})));
  await settle(()=>button('Quản trị demo').click());await until(()=>!!document.querySelector('.admin-dashboard tbody tr'));
  const firstPage=eventPages.at(-1)!;assert.equal(firstPage.page,0);assert(firstPage.totalElements>20);
  await settle(()=>button('Trang sau').click());await until(()=>document.querySelector('.pagination')?.textContent?.includes('Trang 2')===true);
  const secondPage=eventPages.at(-1)!;assert.equal(secondPage.page,1);assert.equal(secondPage.status,200);assert(secondPage.items.length>0);assert(secondPage.items.every(e=>!firstPage.items.some(first=>first.id===e.id)));
  assert.deepEqual([...document.querySelectorAll('.admin-dashboard tbody tr td:first-child strong')].map(c=>c.textContent),secondPage.items.map(e=>e.title));
  await settle(()=>button('Trang trước').click());await until(()=>document.querySelector('.pagination')?.textContent?.includes('Trang 1')===true);
  await settle(()=>button('Tạo sự kiện mới').click());await input('admin-title',title);await input('admin-venue','Local T7');await input('type-capacity-0','6');await settle(()=>button('Thêm loại vé ghế').click());await input('type-price-1','200000');await input('type-seats-1','A1\nA2');
  await settle(()=>button('Tạo sự kiện').click());await until(()=>document.querySelector('.admin-dashboard tbody')?.textContent?.includes(title)===true&&document.querySelector('.event-editor')===null);
  const command=writes.filter(w=>w.path==='/api/admin/events'&&w.body.title===title).at(-1)!;assert.equal(command.status,201);created=command.response as unknown as AdminEvent;
  await assert.rejects(api.detail(created.id),(e:unknown)=>e instanceof ApiError&&e.status===404);assert.equal(created.status,'DRAFT');
  await settle(()=>button('Sửa '+title).click());await until(()=>!!document.querySelector('#admin-status'));await select('admin-status','PUBLISHED');await settle(()=>button('Lưu thay đổi').click());await until(()=>document.querySelector('.event-editor')===null);
  await settle(()=>button('Khách hàng demo').click());await settle(()=>button('Tải lại sự kiện').click());await until(()=>[...document.querySelectorAll('article')].some(a=>a.textContent?.includes(title)));
  const ga=created.ticketTypes.find(t=>t.mode==='GA')!;
  const gaIndex=created.ticketTypes.findIndex(t=>t.id===ga.id);
  const card=[...document.querySelectorAll('article')].find(a=>a.textContent?.includes(title))!;await settle(()=>card.querySelector('button')!.click());await until(()=>!!document.querySelector('#ticket-type option[value="'+ga.id+'"]'));
  await select('ticket-type',ga.id);await until(()=>!!document.querySelector('#quantity')&&!button('Giữ vé GA').disabled);await input('quantity','2');await settle(()=>button('Giữ vé GA').click());await until(()=>!!document.querySelector('.result .booking-id'));
  bookingId=String(writes.filter(w=>w.path==='/api/bookings'&&w.status===202).at(-1)!.response.bookingId);assert.match(document.querySelector('.result')!.textContent!,/PENDING_PAYMENT/);
  await settle(()=>button('Quản trị demo').click());await settle(()=>button('Tồn kho '+title).click());await until(()=>!!document.querySelector('.inventory-type table'));
  assert.deepEqual([...document.querySelectorAll('.inventory-type table tbody td')].map(t=>t.textContent),['6','2','0','4']);assert.match(document.querySelector('.admin-seat-list')!.textContent!,/A1.*AVAILABLE.*A2.*AVAILABLE/);
  await settle(()=>button('Sự kiện').click());await until(()=>!![...document.querySelectorAll('button')].find(b=>b.textContent==='Sửa '+title));await settle(()=>button('Sửa '+title).click());await until(()=>!!document.querySelector('#type-capacity-'+gaIndex));
  await input('type-capacity-'+gaIndex,'1');await settle(()=>button('Lưu thay đổi').click());await until(()=>document.querySelector('.event-editor [role=alert]')?.textContent?.includes('CATALOG_CONFLICT')===true);assert.equal((await admin.inventory(created.id)).ticketTypes.find(t=>t.mode==='GA')!.capacity,6);
  await input('type-capacity-'+gaIndex,'6');await input('type-price-'+gaIndex,'90000');await settle(()=>button('Lưu thay đổi').click());await until(()=>document.querySelector('.event-editor')===null);assert.equal((await api.booking(bookingId)).totalAmountVnd,200000);
  await settle(()=>button('Booking').click());await select('booking-status-filter','PENDING_PAYMENT');await until(()=>document.querySelector('.admin-dashboard tbody')?.textContent?.includes(bookingId)===true);await settle(()=>button('Xem '+bookingId).click());await until(()=>!!document.querySelector('[aria-label="Chi tiết booking quản trị"]'));assert.match(document.querySelector('[aria-label="Chi tiết booking quản trị"]')!.textContent!,/PENDING_PAYMENT/);
  await select('booking-status-filter','REFUND_REQUIRED');await until(()=>!!document.querySelector('.admin-dashboard .empty-state'));
  await settle(()=>button('Outbox').click());await until(()=>document.querySelector('.admin-dashboard tbody')?.textContent?.includes('BookingHeld')===true);
  await settle(()=>button('Khách hàng demo').click());await settle(()=>button('Mô phỏng thành công').click());await until(()=>document.querySelector('.result')?.textContent?.includes('CONFIRMED')===true&&document.querySelectorAll('.ticket-list li').length===2);
  await settle(()=>button('Quản trị demo').click());await settle(()=>button('Làm mới dữ liệu').click());await until(()=>document.querySelector('.admin-dashboard tbody')?.textContent?.includes('BookingConfirmed')===true);
  assert.equal((await api.tickets(bookingId)).length,2);assert(!JSON.stringify(Object.values(dom.window.sessionStorage)).includes('token'));
  const proof={result:'PASS',eventId:created.id,bookingId,gaTypeId:ga.id,tests:['persona switch','pagination','create draft GA+seats','private catalog','publish','GA2 via customer UI','inventory held2/available4','capacity conflict','price snapshot200000','booking filter/history','empty state','outbox held/confirmed','Kafka confirm2tickets'],writeCount:writes.length,uiCreateCalls:writes.filter(w=>w.path==='/api/admin/events'&&w.body.title===title).length};
  const pagination={total:firstPage.totalElements,firstPageCount:firstPage.items.length,secondPageCount:secondPage.items.length,overlap:0,domMatchesApi:true};
  fs.mkdirSync(new URL('../reports/',import.meta.url),{recursive:true});fs.writeFileSync(new URL('../reports/part8-live.json',import.meta.url),JSON.stringify({...proof,pagination},null,2));
  console.log('PASS pagination: '+JSON.stringify(pagination));
  console.log('PASS admin/customer UI with real HTTP/PostgreSQL/Kafka: '+JSON.stringify(proof));
}finally{await act(()=>root.unmount());dom.window.close();}
