import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createElement,act} from 'react';
import {JSDOM} from 'jsdom';
import {createApi} from '../src/api/client.ts';
import {createAdminApi} from '../src/api/admin.ts';
const base=process.env.API_BASE_URL??'http://127.0.0.1:8080';
const customer=createApi(base),admin=createAdminApi(base);
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{RecoveryView}=await import('../src/app/RecoveryView.ts');
const root=createRoot(document.querySelector('#root')!);
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,30));});}
async function until(check:()=>boolean|Promise<boolean>){const end=Date.now()+25000;while(Date.now()<end){if(await check())return;await settle();}assert(await check(),'Live recovery condition timed out');}
function button(text:string){const b=[...document.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent===text);assert(b,'Missing '+text);return b;}
try{
  const event=await admin.create({title:'Recovery live '+crypto.randomUUID(),description:'Owned integration fixture',venue:'Local',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-01-01T00:00:00Z',startsAt:'2099-01-02T00:00:00Z',status:'PUBLISHED',ticketTypes:[{name:'GA',mode:'GA',priceVnd:100000,capacity:6}]});
  const type=event.ticketTypes[0].id;
  const booking=await customer.create({key:crypto.randomUUID(),payload:{eventId:event.id,ticketTypeId:type,quantity:2}});
  await customer.simulate({bookingId:booking.bookingId,scenario:'SUCCESS',requestId:crypto.randomUUID()});
  await until(async()=>(await customer.booking(booking.bookingId)).status==='CONFIRMED'&&(await customer.tickets(booking.bookingId)).length===2);
  const events=[];for(let page=0;;page++){const result=await admin.outbox(page,100);events.push(...result.items.filter(e=>e.bookingId===booking.bookingId));if((page+1)*result.size>=result.totalElements)break;}
  const payment=events.find(e=>e.eventType==='PaymentResultRecorded')!;assert(payment);
  await until(async()=>{const detail=await admin.recoveryEvent(payment.eventId);return detail.status==='PUBLISHED'&&detail.consumers.length===2;});
  const before={booking:await customer.booking(booking.bookingId),tickets:await customer.tickets(booking.bookingId),detail:await admin.recoveryEvent(payment.eventId)};
  const sent:{requestId:string;reason:string}[]=[];let dropReply=true;
  const faultFetch:typeof fetch=async(input,init)=>{
    const response=await fetch(input,init);
    if(String(input).endsWith('/replay')){
      sent.push(JSON.parse(init!.body as string));
      if(dropReply&&response.status===202){dropReply=false;await response.text();throw new TypeError('Injected lost response after server accepted replay');}
    }
    return response;
  };
  await settle(()=>root.render(createElement(RecoveryView,{api:createAdminApi(base,faultFetch),initialEventId:payment.eventId,store:dom.window.sessionStorage})));
  await until(()=>!!document.querySelector('#recovery-reason'));
  assert.equal(document.activeElement?.id,'recovery-reason');
  await settle(()=>{const input=document.querySelector<HTMLTextAreaElement>('#recovery-reason')!;Object.getOwnPropertyDescriptor(dom.window.HTMLTextAreaElement.prototype,'value')!.set!.call(input,'Đối soát replay an toàn T8.3');input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});
  await settle(()=>button('Gửi yêu cầu replay').click());await until(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Gửi lại cùng yêu cầu'));
  assert.equal(sent.length,1);await until(async()=>(await admin.recoveryEvent(payment.eventId)).audit.length===1&&(await admin.recoveryEvent(payment.eventId)).status==='PUBLISHED');
  await settle(()=>button('Gửi lại cùng yêu cầu').click());
  await until(()=>document.querySelector('[role=status].success')!==null&&!!document.querySelector('.replay-audit'));
  assert.equal(sent.length,2);assert.deepEqual(sent[0],sent[1]);
  const after={booking:await customer.booking(booking.bookingId),tickets:await customer.tickets(booking.bookingId),detail:await admin.recoveryEvent(payment.eventId)};
  assert.deepEqual(after.booking,before.booking);assert.deepEqual(after.tickets,before.tickets);assert.deepEqual(after.detail.consumers,before.detail.consumers);
  assert.equal(after.detail.audit.length,1);assert.equal(after.detail.audit[0].requestId,sent[0].requestId);assert.equal(after.detail.remainingReplays,2);assert.equal(after.detail.status,'PUBLISHED');
  assert(document.querySelector('.replay-audit')!.textContent!.includes(sent[0].reason));assert(!document.body.textContent!.includes('demo-admin-token'));
  const proof={result:'PASS',eventId:event.id,bookingId:booking.bookingId,ticketTypeId:type,paymentEventId:payment.eventId,requestId:sent[0].requestId,httpReplayCalls:sent.length,distinctRequestIds:new Set(sent.map(s=>s.requestId)).size,auditRows:after.detail.audit.length,ticketsBefore:before.tickets.length,ticketsAfter:after.tickets.length,historyBefore:before.booking.history?.length,historyAfter:after.booking.history?.length,remainingReplays:after.detail.remainingReplays,consumerReceiptsUnchanged:true};
  fs.mkdirSync(new URL('../reports/',import.meta.url),{recursive:true});fs.writeFileSync(new URL('../reports/recovery-live.json',import.meta.url),JSON.stringify(proof,null,2));
  console.log('PASS live recovery UI / lost response / same-request replay / no extra tickets: '+JSON.stringify(proof));
}finally{await act(()=>root.unmount());dom.window.close();}
