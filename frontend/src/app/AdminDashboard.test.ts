import assert from 'node:assert/strict';
import {test,afterEach} from 'node:test';
import {createElement,act} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {JSDOM} from 'jsdom';
import {ApiError,readAttempt,saveAttempt} from '../api/client.ts';
import type {Api,Store,Attempt} from '../api/client.ts';
import type {AdminApi,AdminEvent,CreateEvent,PatchEvent} from '../api/admin.ts';

const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://127.0.0.1:5173'});
for(const[name,value]of Object.entries({window:dom.window,document:dom.window.document,HTMLElement:dom.window.HTMLElement,HTMLInputElement:dom.window.HTMLInputElement,IS_REACT_ACT_ENVIRONMENT:true}))Object.defineProperty(globalThis,name,{value,writable:true,configurable:true});
const{createRoot}=await import('react-dom/client');const{AdminDashboard}=await import('./AdminDashboard.ts');const{DemoShell}=await import('./DemoShell.ts');
const event:AdminEvent={id:'20000000-0000-4000-8000-000000000001',title:'Sự kiện quản trị',description:'',venue:'Local',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-01-01T00:00:00Z',startsAt:'2099-01-02T00:00:00Z',status:'DRAFT',ticketTypes:[{id:'30000000-0000-4000-8000-000000000001',name:'GA',mode:'GA',priceVnd:100000,active:true}]};
function fixture(overrides:Partial<AdminApi>={}):AdminApi{return{events:async(page,size)=>({items:[event],page,size,totalElements:1}),inventory:async()=>({eventId:event.id,asOf:'2026-11-13T00:00:00Z',ticketTypes:[{ticketTypeId:event.ticketTypes[0].id,mode:'GA',capacity:6,held:2,sold:1,available:3}]}),create:async()=>event,patch:async()=>event,bookings:async(page,size)=>({items:[],page,size,totalElements:0}),booking:async()=>({bookingId:'fixture',status:'EXPIRED',expiresAt:'2026-11-13T00:00:00Z',totalAmountVnd:100000,history:[]}),outbox:async(page,size)=>({items:[],page,size,totalElements:0}),...overrides};}
const roots:ReturnType<typeof createRoot>[]=[];
async function settle(action:()=>void=()=>{}){await act(async()=>{action();await new Promise(resolve=>setTimeout(resolve,0));});}
async function mount(api:AdminApi){const el=document.createElement('div');document.body.append(el);const root=createRoot(el);roots.push(root);await settle(()=>root.render(createElement(AdminDashboard,{api})));return root;}
function button(text:string){const b=[...document.querySelectorAll<HTMLButtonElement>('button')].find(b=>b.textContent===text);assert(b,'Missing button '+text);return b;}
function captureScroll(){
  const prototype=dom.window.HTMLElement.prototype,original=Object.getOwnPropertyDescriptor(prototype,'scrollIntoView');
  const calls:{element:HTMLElement;options:ScrollIntoViewOptions}[]=[];
  Object.defineProperty(prototype,'scrollIntoView',{configurable:true,value:function(this:HTMLElement,options:ScrollIntoViewOptions){calls.push({element:this,options});}});
  return{calls,restore:()=>{if(original)Object.defineProperty(prototype,'scrollIntoView',original);else Reflect.deleteProperty(prototype,'scrollIntoView');}};
}
async function input(id:string,value:string){const el=document.querySelector<HTMLInputElement|HTMLTextAreaElement>('#'+id)!;await settle(()=>{Object.getOwnPropertyDescriptor(el.tagName==='TEXTAREA'?dom.window.HTMLTextAreaElement.prototype:dom.window.HTMLInputElement.prototype,'value')!.set!.call(el,value);el.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});}
afterEach(async()=>{for(const root of roots.splice(0))await act(()=>root.unmount());document.body.innerHTML='';});

test('admin SSR loads from API without invented records; errors are not shown as empty data',async()=>{
  const html=renderToStaticMarkup(createElement(AdminDashboard,{api:fixture()}));assert.match(html,/Đang tải dữ liệu quản trị/);assert.doesNotMatch(html,/Sự kiện quản trị/);
  await mount(fixture({events:async()=>{throw new ApiError('FORBIDDEN','Không có quyền',403,'trace-demo');}}));assert.match(document.querySelector('[role=alert]')!.textContent!,/FORBIDDEN.*trace-demo/);assert.doesNotMatch(document.body.textContent!,/Không có dữ liệu cho trang/);
});
test('create opens an editable focused form beside its trigger before a long table, and both close actions remove it',async()=>{
  const scroll=captureScroll();let creates=0;
  try{
    const events=Array.from({length:20},(_,i)=>({...event,id:'20000000-0000-4000-8000-'+String(i+1).padStart(12,'0'),title:'Sự kiện '+(i+1)}));
    await mount(fixture({events:async(page,size)=>({items:events,page,size,totalElements:20}),create:async()=>{creates++;return event;}}));assert.equal(document.querySelectorAll('tbody tr').length,20);
    await settle(()=>button('Tạo sự kiện mới').click());
    const panel=document.querySelector<HTMLElement>('#event-editor-panel')!,form=panel.querySelector<HTMLFormElement>('form[aria-label="Tạo sự kiện"]')!,title=panel.querySelector<HTMLInputElement>('#admin-title')!;
    assert(panel&&form);assert.equal(button('Tạo sự kiện mới').nextElementSibling,panel);assert.equal(panel.closest('[hidden]'),null);assert.equal(form.hidden,false);assert.equal(title.disabled,false);assert.equal(document.activeElement,title);assert.equal(button('Tạo sự kiện mới').getAttribute('aria-expanded'),'true');
    assert(scroll.calls.some(c=>c.element===panel&&c.options.block==='start'));
    await input('admin-title','Nhập được ngay');assert.equal(title.value,'Nhập được ngay');
    await settle(()=>button('Đóng biểu mẫu').click());assert.equal(document.querySelector('#event-editor-panel'),null);assert.equal(document.querySelector('.event-editor'),null);assert.equal(button('Tạo sự kiện mới').getAttribute('aria-expanded'),'false');
    await settle(()=>button('Tạo sự kiện mới').click());assert.equal(document.activeElement,document.querySelector('#admin-title'));await settle(()=>button('Hủy').click());assert.equal(document.querySelector('.event-editor'),null);assert.equal(creates,0);
  }finally{scroll.restore();}
});
test('edit reveals its loading panel then focuses the chosen event data and can be closed without saving',async()=>{
  const scroll=captureScroll();let finish!:(stock:Awaited<ReturnType<AdminApi['inventory']>>)=>void;let patches=0;
  const stock=new Promise<Awaited<ReturnType<AdminApi['inventory']>>>(resolve=>{finish=resolve;});
  const second:AdminEvent={...event,id:'20000000-0000-4000-8000-000000000002',title:'Sự kiện thứ hai',venue:'Địa điểm thứ hai',status:'CLOSED',ticketTypes:[{...event.ticketTypes[0],id:'30000000-0000-4000-8000-000000000002',name:'GA thứ hai',priceVnd:250000}]};
  try{
    await mount(fixture({events:async(page,size)=>({items:[event,second],page,size,totalElements:2}),inventory:async()=>stock,patch:async()=>{patches++;return second;}}));
    await settle(()=>button('Sửa '+second.title).click());const panel=document.querySelector<HTMLElement>('#event-editor-panel')!;assert.equal(button('Tạo sự kiện mới').nextElementSibling,panel);assert.match(panel.textContent!,/Đang tải cấu hình tồn kho/);assert(scroll.calls.some(c=>c.element===panel));
    await settle(()=>finish({eventId:second.id,asOf:'2026-11-13T00:00:00Z',ticketTypes:[{ticketTypeId:second.ticketTypes[0].id,mode:'GA',capacity:12,held:0,sold:0,available:12}]}));
    const form=panel.querySelector<HTMLFormElement>('form[aria-label="Sửa sự kiện"]')!;assert(form);assert.equal(form.hidden,false);assert.equal(document.activeElement,document.querySelector('#admin-title'));
    for(const [id,value]of Object.entries({'admin-title':second.title,'admin-venue':second.venue,'admin-status':'CLOSED','type-name-0':'GA thứ hai','type-price-0':'250000','type-capacity-0':'12'}))assert.equal(document.querySelector<HTMLInputElement|HTMLSelectElement>('#'+id)!.value,value);
    await input('admin-title','Sửa nhập được');assert.equal(document.querySelector<HTMLInputElement>('#admin-title')!.value,'Sửa nhập được');await settle(()=>button('Đóng biểu mẫu').click());assert.equal(document.querySelector('.event-editor'),null);assert.equal(patches,0);
  }finally{scroll.restore();}
});
test('edit loading failure keeps an accessible close action above the list',async()=>{
  await mount(fixture({inventory:async()=>{throw new ApiError('NETWORK_ERROR','Chưa tải được tồn kho');}}));await settle(()=>button('Sửa '+event.title).click());
  const panel=document.querySelector('#event-editor-panel')!;assert.match(panel.textContent!,/Chưa tải được tồn kho/);assert.equal(document.querySelector('.event-editor'),null);assert.equal(button('Đóng biểu mẫu').disabled,false);
  await settle(()=>button('Đóng biểu mẫu').click());assert.equal(document.querySelector('#event-editor-panel'),null);
});
test('admin pagination and booking status filter go to the API; empty state and outbox are distinct',async()=>{
  const calls:(string|number)[][]=[];await mount(fixture({events:async(page,size)=>{calls.push(['events',page,size]);return{items:[event],page,size,totalElements:21};},bookings:async(page,size,status)=>{calls.push(['bookings',page,size,status??'']);return{items:[],page,size,totalElements:0};}}));
  await settle(()=>button('Trang sau').click());assert.deepEqual(calls.at(-1),['events',1,20]);await settle(()=>button('Booking').click());
  const filter=document.querySelector<HTMLSelectElement>('#booking-status-filter')!;await settle(()=>{filter.value='EXPIRED';filter.dispatchEvent(new dom.window.Event('change',{bubbles:true}));});assert.deepEqual(calls.at(-1),['bookings',0,20,'EXPIRED']);assert.match(document.body.textContent!,/Không có dữ liệu cho trang/);
  await settle(()=>button('Outbox').click());assert.match(document.querySelector('caption')!.textContent!,/Outbox sau commit/);assert.equal(document.querySelectorAll('tbody tr').length,0);
});
test('pagination tolerates a real loading interval and renders the next API page',async()=>{
  let finish!:(page:{items:AdminEvent[];page:number;size:number;totalElements:number})=>void;
  const next=new Promise<{items:AdminEvent[];page:number;size:number;totalElements:number}>(resolve=>{finish=resolve;});
  await mount(fixture({events:async(page,size)=>page===0?{items:[event],page,size,totalElements:21}:next}));
  await settle(()=>button('Trang sau').click());assert.match(document.body.textContent!,/Đang tải dữ liệu quản trị/);assert.equal(document.querySelector('.pagination'),null);
  await settle(()=>finish({items:[{...event,id:'20000000-0000-4000-8000-000000000002',title:'Trang hai từ API'}],page:1,size:20,totalElements:21}));
  assert.match(document.querySelector('tbody')!.textContent!,/Trang hai từ API/);assert.match(document.querySelector('.pagination')!.textContent!,/Trang 2/);assert.equal(button('Trang sau').disabled,true);assert.equal(button('Trang trước').disabled,false);
});
test('clicking the active tab preserves loaded data instead of getting stuck loading',async()=>{
  let calls=0;await mount(fixture({events:async(page,size)=>{calls++;return{items:[event],page,size,totalElements:1};}}));
  await settle(()=>button('Sự kiện').click());assert.match(document.querySelector('tbody')!.textContent!,/Sự kiện quản trị/);assert.doesNotMatch(document.body.textContent!,/Đang tải dữ liệu quản trị/);assert.equal(calls,1);
});
test('reopening inventory for the same event fetches current holds instead of reusing its old snapshot',async()=>{
  let reads=0;await mount(fixture({inventory:async()=>({eventId:event.id,asOf:'2026-11-13T00:00:00Z',ticketTypes:[{ticketTypeId:event.ticketTypes[0].id,mode:'GA',capacity:6,held:reads++===0?0:2,sold:0,available:reads===1?6:4}]})}));
  await settle(()=>button('Tồn kho '+event.title).click());assert.deepEqual([...document.querySelectorAll('tbody td')].map(c=>c.textContent),['6','0','0','6']);
  await settle(()=>button('Sự kiện').click());await settle(()=>button('Tồn kho '+event.title).click());assert.equal(reads,2);assert.deepEqual([...document.querySelectorAll('tbody td')].map(c=>c.textContent),['6','2','0','4']);
});
test('inventory shows server capacity/held/sold and editing preserves immutable type allocation',async()=>{
  const patches:PatchEvent[]=[];await mount(fixture({patch:async(_id,body)=>{patches.push(body);return event;}}));await settle(()=>button('Tồn kho '+event.title).click());assert.match(document.body.textContent!,/Sức chứa.*Đang giữ.*Đã bán/);assert.deepEqual([...document.querySelectorAll('tbody td')].map(c=>c.textContent),['6','2','1','3']);
  await settle(()=>button('Sự kiện').click());await settle(()=>button('Sửa '+event.title).click());await input('type-price-0','90000');await settle(()=>button('Lưu thay đổi').click());assert.equal(patches.length,1);assert.deepEqual(patches[0].ticketTypes,[{id:event.ticketTypes[0].id,name:'GA',priceVnd:90000,active:true,capacity:6}]);assert(!JSON.stringify(patches[0]).includes('mode'));
});
test('capacity conflict keeps the edit form and displays server error rather than a success',async()=>{
  await mount(fixture({patch:async()=>{throw new ApiError('CATALOG_CONFLICT','Sức chứa không đủ',409,'trace-conflict');}}));await settle(()=>button('Sửa '+event.title).click());await input('type-capacity-0','1');await settle(()=>button('Lưu thay đổi').click());
  assert.match(document.querySelector('.event-editor [role=alert]')!.textContent!,/CATALOG_CONFLICT.*trace-conflict/);assert.equal(document.querySelector<HTMLInputElement>('#type-capacity-0')!.value,'1');assert.equal(document.querySelector('.alert.success'),null);
});
test('create form supports GA and seats, rejects duplicate labels and sends no role or IDs',async()=>{
  const commands:CreateEvent[]=[];await mount(fixture({create:async body=>{commands.push(body);return event;}}));await settle(()=>button('Tạo sự kiện mới').click());await input('admin-title','Một sự kiện mới');await input('admin-venue','Local');await settle(()=>button('Thêm loại vé ghế').click());await input('type-seats-1','A1\nA1');await settle(()=>button('Tạo sự kiện').click());assert.equal(commands.length,0);assert.match(document.querySelector('.event-editor [role=alert]')!.textContent!,/nhãn khác nhau/);
  await input('type-seats-1','A1\nA2');await settle(()=>button('Tạo sự kiện').click());assert.equal(commands.length,1);assert.equal(commands[0].ticketTypes.length,2);assert.deepEqual(commands[0].ticketTypes[1],{name:'Ghế chỉ định',mode:'SEATED',priceVnd:100000,seats:[{label:'A1'},{label:'A2'}]});assert(!JSON.stringify(commands[0]).includes('userId'));assert(!JSON.stringify(commands[0]).includes('role'));
});
test('double click creates once and unknown create outcome requires reviewing the list',async()=>{
  let calls=0;let reject!:(value:unknown)=>void;const response=new Promise<AdminEvent>((_,r)=>{reject=r;});await mount(fixture({create:async()=>{calls++;return response;}}));await settle(()=>button('Tạo sự kiện mới').click());await input('admin-title','Một sự kiện mới');await input('admin-venue','Local');await settle(()=>{button('Tạo sự kiện').click();button('Tạo sự kiện').click();});assert.equal(calls,1);
  await settle(()=>reject(new ApiError('NETWORK_ERROR','Chưa rõ kết quả')));assert.equal(button('Tạo sự kiện').disabled,true);assert.match(document.body.textContent!,/Không gửi lại tự động/);assert.equal(calls,1);await settle(()=>button('Tải danh sách để kiểm tra').click());assert.equal(button('Tạo sự kiện').disabled,false);assert.equal(calls,1);
});
test('API text is escaped and never interpreted as markup',async()=>{
  await mount(fixture({events:async(page,size)=>({items:[{...event,title:'<img src=x onerror=alert(1)>'}],page,size,totalElements:1})}));assert.equal(document.querySelector('img'),null);assert.match(document.body.textContent!,/<img src=x/);
});
test('switching demo persona preserves pending customer key and never stores a token or auto-posts',async()=>{
  const entries=new Map<string,string>();const store:Store={getItem:k=>entries.get(k)??null,setItem:(k,v)=>{entries.set(k,v);},removeItem:k=>{entries.delete(k);}};
  const attempt:Attempt={key:'50000000-0000-4000-8000-000000000001',payload:{eventId:event.id,ticketTypeId:event.ticketTypes[0].id,quantity:2}};saveAttempt(store,attempt);let posts=0;
  const customer:Api={events:async()=>({items:[event]}),detail:async()=>event,availability:async()=>({eventId:event.id,asOf:'2026-11-13T00:00:00Z',ticketTypes:[{ticketTypeId:event.ticketTypes[0].id,mode:'GA',available:6}]}),mine:async()=>({items:[]}),booking:async()=>{throw Error('not used');},create:async()=>{posts++;throw new ApiError('BOOKING_BUSY','Bận',503);},simulate:async()=>{throw Error('not used');},cancel:async()=>{throw Error('not used');},tickets:async()=>[]};
  const el=document.createElement('div');document.body.append(el);const root=createRoot(el);roots.push(root);await settle(()=>root.render(createElement(DemoShell,{customer,admin:fixture(),store})));
  await settle(()=>button('Quản trị demo').click());await settle(()=>button('Khách hàng demo').click());assert.equal(posts,0);assert.deepEqual(readAttempt(store),attempt);assert(![...entries.values()].join('').includes('token'));await settle(()=>button('Thử lại cùng yêu cầu').click());assert.equal(posts,1);assert.deepEqual(readAttempt(store),attempt);
});
