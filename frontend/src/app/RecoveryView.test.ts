import assert from 'node:assert/strict';
import {test,afterEach} from 'node:test';
import {createElement,act} from 'react';
import {JSDOM} from 'jsdom';
import {createAdminApi} from '../api/admin.ts';
import type {AdminApi,RecoveryEvent,RecoveryItem,ReplayRequest} from '../api/admin.ts';
import {ApiError} from '../api/client.ts';
import {readReplayAttempt,saveReplayAttempt} from './replayAttempt.ts';

const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{RecoveryView}=await import('./RecoveryView.ts');
const id='70000000-0000-4000-8000-000000000001',other='70000000-0000-4000-8000-000000000002',adminId='10000000-0000-4000-8000-000000000002';
const event:RecoveryEvent={eventId:id,bookingId:'60000000-0000-4000-8000-000000000001',eventType:'PaymentResultRecorded',aggregateVersion:2,status:'PUBLISHED',createdAt:'2026-11-13T00:00:00Z',attempts:1,correlationId:other,publishedAt:'2026-11-13T00:00:01Z',publisherError:'Không có lỗi publisher hiện tại.',remainingReplays:3,audit:[],consumers:[{consumer:'payment-v1',processedAt:'2026-11-13T00:00:02Z'}],failures:[]};
const item:RecoveryItem={id,source:'CONSUMER',eventId:id,bookingId:event.bookingId,eventType:event.eventType,consumer:'payment-v1',status:'DEAD',error:'Lỗi consumer an toàn',createdAt:event.createdAt,dlqStatus:'PUBLISHED',replayable:true};
function api(overrides:Partial<AdminApi>={}):AdminApi{return{...createAdminApi('http://never-used.invalid'),recovery:async(page,size)=>({items:[item,{...item,id:other,eventId:null,bookingId:null,replayable:false}],page,size,totalElements:2}),recoveryEvent:async()=>event,replayEvent:async(eventId,request)=>({eventId,requestId:request.requestId,status:'ACCEPTED'}),...overrides};}
const roots:ReturnType<typeof createRoot>[]=[];
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,0));});}
async function mount(value:AdminApi,initialEventId?:string){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);roots.push(root);await settle(()=>root.render(createElement(RecoveryView,{api:value,initialEventId,store:dom.window.sessionStorage})));return root;}
function button(text:string){const b=[...document.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent===text);assert(b,'Missing '+text);return b;}
async function reason(value:string){const el=document.querySelector<HTMLTextAreaElement>('#recovery-reason')!;await settle(()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLTextAreaElement.prototype,'value')!.set!.call(el,value);el.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});}
afterEach(async()=>{for(const root of roots.splice(0))await act(()=>root.unmount());document.body.innerHTML='';dom.window.sessionStorage.clear();});

test('recovery selection reveals focused form, safe audit and closes; navigation/refresh never posts',async()=>{
  const writes:ReplayRequest[]=[];let current=structuredClone(event);
  await mount(api({recoveryEvent:async()=>current,replayEvent:async(eventId,body)=>{writes.push(body);current={...current,remainingReplays:2,audit:[{...body,adminAccountId:adminId,requestedAt:'2026-11-13T00:01:00Z'}]};return{eventId,requestId:body.requestId,status:'ACCEPTED'};}}));
  assert.match(document.body.textContent!,/Chỉ điều tra/);assert.equal(writes.length,0);
  await settle(()=>button('Xem event '+id).click());assert.equal(document.activeElement?.id,'recovery-reason');assert.match(document.querySelector('.recovery-detail')!.textContent!,/Consumer chưa ghi nhận có thể xử lý lần đầu/);assert.doesNotMatch(document.querySelector('.recovery-detail')!.textContent!,/Event đã được xử lý/);
  await reason('<img src=x onerror=alert(1)> Đối soát');await settle(()=>button('Gửi yêu cầu replay').click());
  assert.equal(writes.length,1);assert.match(document.body.textContent!,/Đây là xác nhận ghi audit\/xếp hàng/);assert.match(document.querySelector('.replay-audit')!.textContent!,/ADMIN/);assert.equal(document.querySelector('.replay-audit img'),null);
  assert(document.querySelector('.replay-audit')!.textContent!.includes(writes[0].reason));
  await settle(()=>button('Làm mới recovery').click());assert.equal(writes.length,1);
  await settle(()=>button('Đóng chi tiết recovery').click());assert.equal(document.querySelector('#recovery-reason'),null);
});

test('uncertain replay survives remount and retries exact request even with exhausted budget; double click is blocked',async()=>{
  const writes:ReplayRequest[]=[];let rejectFirst!:(e:Error)=>void;
  let current=structuredClone(event);
  const backend=api({recoveryEvent:async()=>current,replayEvent:async(eventId,request)=>{
    writes.push({...request});if(writes.length===1){current={...current,remainingReplays:0,audit:[{...request,adminAccountId:adminId,requestedAt:event.createdAt}]};return new Promise((_,reject)=>{rejectFirst=reject;});}
    return{eventId,requestId:request.requestId,status:'ACCEPTED'};
  }});
  const first=await mount(backend,id);await reason('Kiểm tra cùng request');
  await settle(()=>{const form=document.querySelector('form')!;form.dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));form.dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));});assert.equal(writes.length,1);
  await settle(()=>rejectFirst(new ApiError('NETWORK_ERROR','Mất phản hồi',0)));assert(readReplayAttempt(dom.window.sessionStorage));
  await act(()=>first.unmount());roots.splice(roots.indexOf(first),1);document.body.innerHTML='';
  await mount(backend);assert.equal(writes.length,1);assert.equal(button('Gửi yêu cầu replay').disabled,true);assert.equal(button('Gửi lại cùng yêu cầu').disabled,false);
  await settle(()=>button('Gửi lại cùng yêu cầu').click());assert.equal(writes.length,2);assert.deepEqual(writes[0],writes[1]);assert.equal(readReplayAttempt(dom.window.sessionStorage),null);assert.equal(current.audit.length,1);assert.equal(button('Gửi yêu cầu replay').disabled,true);
});

test('validation counts Unicode points and retains server limits/authorization/not-found errors',async()=>{
  let writes=0;await mount(api({replayEvent:async()=>{writes++;throw new ApiError('REPLAY_LIMIT_REACHED','Đã hết ngân sách',409,'trace-safe');}}),id);
  await reason('   ');await settle(()=>button('Gửi yêu cầu replay').click());assert.equal(writes,0);
  await reason('🎟'.repeat(501));await settle(()=>button('Gửi yêu cầu replay').click());assert.equal(writes,0);
  await reason('🎟'.repeat(500));await settle(()=>button('Gửi yêu cầu replay').click());assert.equal(writes,1);assert.match(document.body.textContent!,/REPLAY_LIMIT_REACHED.*trace-safe/);
  const root=roots.pop()!;await act(()=>root.unmount());document.body.innerHTML='';
  await mount(api({recovery:async()=>{throw new ApiError('FORBIDDEN','Không có quyền',403);},recoveryEvent:async()=>{throw new ApiError('NOT_FOUND','Không có event',404);}}),other);
  assert.match(document.body.textContent!,/FORBIDDEN/);assert.match(document.body.textContent!,/NOT_FOUND/);assert.equal(document.querySelector('#recovery-reason'),null);assert.equal(writes,1);
});

test('list filter/pagination and slow detail cannot replace the selected event',async()=>{
  const calls:{page:number;status:string}[]=[];let resolveOld!:(value:RecoveryEvent)=>void;
  await mount(api({recovery:async(page,size,status)=>{calls.push({page,status});return{items:[item,{...item,id:other,eventId:other}],page,size,totalElements:21};},recoveryEvent:async(key)=>key===id?new Promise(resolve=>{resolveOld=resolve;}):{...event,eventId:other}}));
  await settle(()=>button('Recovery sau').click());assert.equal(calls.at(-1)!.page,1);
  await settle(()=>{const select=document.querySelector<HTMLSelectElement>('#recovery-filter')!;select.value='ALL';select.dispatchEvent(new dom.window.Event('change',{bubbles:true}));});assert.deepEqual(calls.at(-1),{page:0,status:'ALL'});
  await settle(()=>button('Xem event '+id).click());await settle(()=>button('Xem event '+other).click());
  await settle(()=>resolveOld(event));assert(document.querySelector('.recovery-detail')!.textContent!.includes('Sự kiện '+other));assert(!document.querySelector('.recovery-detail')!.textContent!.includes('Sự kiện '+id));
});

test('stored replay state is bounded and never trusts malformed state or force fields',()=>{
  const store=dom.window.sessionStorage;saveReplayAttempt(store,{eventId:id,requestId:other,reason:'Check'});assert.equal(readReplayAttempt(store)?.requestId,other);
  store.setItem('eventflow.pending-replay.v1',JSON.stringify({eventId:'bad',requestId:other,reason:'Check',force:true}));assert.equal(readReplayAttempt(store),null);
  store.setItem('eventflow.pending-replay.v1',JSON.stringify({eventId:id,requestId:other,reason:'x'.repeat(501)}));assert.equal(readReplayAttempt(store),null);
});
