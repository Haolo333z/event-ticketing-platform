import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createAdminApi} from './admin.ts';
import {createApi,ApiError} from './client.ts';

test('admin adapter uses bearer fixture and exact API paths without client role or identity',async()=>{
  const calls:{path:string;init:RequestInit}[]=[];
  const fetcher:typeof fetch=async(input,init)=>{calls.push({path:String(input),init:init!});return new Response(JSON.stringify({items:[],page:0,size:20,totalElements:0}),{status:200});};
  const admin=createAdminApi('http://local',fetcher);await admin.events(2,20);await admin.inventory('event/id');await admin.bookings(1,20,'EXPIRED');await admin.booking('booking/id');await admin.outbox(3,20);
  assert.deepEqual(calls.map(c=>c.path),['http://local/api/admin/events?page=2&size=20','http://local/api/admin/events/event%2Fid/inventory','http://local/api/admin/bookings?page=1&size=20&status=EXPIRED','http://local/api/bookings/booking%2Fid','http://local/api/admin/operations/outbox?page=3&size=20']);
  for(const {init} of calls){assert.equal((init.headers as Record<string,string>).Authorization,'Bearer demo-admin-token');assert.equal(init.body,undefined);assert(!JSON.stringify(init.headers).includes('role'));}
  await createApi('http://local',fetcher).events();assert.equal((calls.at(-1)!.init.headers as Record<string,string>).Authorization,'Bearer demo-customer-token');
});
test('admin adapter preserves command payload, errors and correlation, with no automatic create retry',async()=>{
  let attempts=0;const body={title:'Draft',status:'DRAFT' as const,description:'',venue:'Local',saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-01-01T00:00:00Z',startsAt:'2099-01-02T00:00:00Z',ticketTypes:[{name:'GA',mode:'GA' as const,capacity:6,priceVnd:100000}]};
  const api=createAdminApi('http://local',async(input,init)=>{attempts++;assert.equal(String(input),'http://local/api/admin/events');assert.deepEqual(JSON.parse(init!.body as string),body);return new Response(JSON.stringify({code:'FORBIDDEN',message:'Không có quyền',correlationId:'trace-fixture'}),{status:403});});
  await assert.rejects(api.create(body),(e:unknown)=>e instanceof ApiError&&e.status===403&&e.code==='FORBIDDEN'&&e.correlationId==='trace-fixture');assert.equal(attempts,1);
});

test('recovery adapter reuses bounded replay endpoint with exact request and no force/user fields or automatic retry',async()=>{
  const calls:{path:string;init:RequestInit}[]=[];
  const admin=createAdminApi('http://local',async(input,init)=>{calls.push({path:String(input),init:init!});return new Response(JSON.stringify({code:'REPLAY_LIMIT_REACHED',message:'Hết ngân sách',correlationId:'safe-trace'}),{status:409});});
  await assert.rejects(admin.recovery(1,20,'OPEN'));await assert.rejects(admin.recoveryEvent('event/id'));
  await assert.rejects(admin.replayEvent('event/id',{requestId:'same-request',reason:'Same reason'}),(e:unknown)=>e instanceof ApiError&&e.code==='REPLAY_LIMIT_REACHED'&&e.correlationId==='safe-trace');
  assert.equal(calls.length,3);assert.equal(calls[0].path,'http://local/api/admin/operations/recovery?page=1&size=20&status=OPEN');assert.equal(calls[1].path,'http://local/api/admin/operations/events/event%2Fid');
  assert.equal(calls[2].path,'http://local/api/admin/operations/events/event%2Fid/replay');assert.deepEqual(JSON.parse(calls[2].init.body as string),{requestId:'same-request',reason:'Same reason'});
});
