import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createElement,act} from 'react';
import {createApi} from '../src/api/client.ts';
const base=process.env.API_BASE_URL;if(!base)throw new Error('Run infra/test-part4-ui.ps1 for an isolated real backend.');
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{App}=await import('../src/app/App.ts');
const headers={'Content-Type':'application/json',Authorization:'Bearer demo-admin-token'};
const title='UI integration '+crypto.randomUUID();
const created=await fetch(base+'/api/admin/events',{method:'POST',headers,body:JSON.stringify({title,description:'Real API integration fixture',venue:'Test DB',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-06-01T00:00:00Z',startsAt:'2099-07-01T00:00:00Z',status:'PUBLISHED',ticketTypes:[{name:'GA integration',mode:'GA',priceVnd:70000,capacity:3}]})});
assert.equal(created.status,201);const event=await created.json();
const preflight=await fetch(base+'/api/bookings',{method:'OPTIONS',headers:{Origin:'http://127.0.0.1:5173','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type,idempotency-key'}});
assert.equal(preflight.status,204);assert.equal(preflight.headers.get('Access-Control-Allow-Origin'),'http://127.0.0.1:5173');
const sent:{key:string;body:string;status:number;response:Record<string,unknown>}[]=[];
const api=createApi(base,async(input,options)=>{const response=await fetch(input,options);if(options?.method==='POST')sent.push({key:new Headers(options.headers).get('Idempotency-Key')!,body:String(options.body),status:response.status,response:await response.clone().json()});return response;});
const root=createRoot(document.getElementById('root')!);
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,30));});}
async function until(predicate:()=>boolean){for(let i=0;i<200;i++){await settle();if(predicate())return;}throw new Error('UI timed out: '+document.body.textContent);}
function button(label:string){const result=[...document.querySelectorAll('button')].find(b=>b.textContent===label);assert(result,`Missing ${label}`);return result;}
try{
  await settle(()=>root.render(createElement(App,{api,store:dom.window.sessionStorage})));
  await until(()=>[...document.querySelectorAll('article')].some(a=>a.textContent?.includes(title)));
  const card=[...document.querySelectorAll('article')].find(a=>a.textContent?.includes(title))!;
  await settle(()=>card.querySelector('button')!.click());await until(()=>document.querySelector<HTMLSelectElement>('#ticket-type')?.value===event.ticketTypes[0].id);
  await settle(()=>{const input=document.querySelector<HTMLInputElement>('#quantity')!;Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value')!.set!.call(input,'2');input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});
  await settle(()=>button('Giữ vé GA').click());await until(()=>!!document.querySelector('.result')?.textContent?.includes('PENDING_PAYMENT'));
  assert.equal(sent.length,1);assert.equal(sent[0].status,202);assert.equal(sent[0].response.totalAmountVnd,140000);assert.match(document.querySelector('.result')!.textContent!,/140[.\s]000/);
  await act(async()=>{
    const replay=await fetch(base+'/api/bookings',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer demo-customer-token','Idempotency-Key':sent[0].key},body:sent[0].body});
    assert.equal(replay.status,202);const replayBody=await replay.json();assert.deepEqual(replayBody,sent[0].response);
    await createApi(base).create({key:crypto.randomUUID(),payload:{eventId:event.id,ticketTypeId:event.ticketTypes[0].id,quantity:1}});
  });
  // UI still has availability=1; a new quantity=2 must show the real 409, not invent a result.
  await settle(()=>button('Đặt thêm vé').click());await settle(()=>button('Giữ vé GA').click());
  await until(()=>!!document.querySelector('[role=alert]')?.textContent?.includes('INVENTORY_UNAVAILABLE'));
  await until(()=>!!document.querySelector('.sold-out')?.textContent?.includes('Hết vé'));
  assert.equal(sent.length,2);assert.equal(sent[1].status,409);
  await settle(()=>button('Tải lại lịch sử').click());await until(()=>document.querySelectorAll('.history-list li').length===2);
  await act(async()=>{assert.equal((await api.availability(event.id)).ticketTypes.find(t=>t.mode==='GA'&&t.ticketTypeId===event.ticketTypes[0].id)?.mode,'GA');});
  console.log('PASS live UI: real PostgreSQL/HTTP; quantity2; server total140000; replay same booking; stale inventory409; refreshed sold-out; history2; CORS204.');
}finally{await act(()=>root.unmount());dom.window.close();}
