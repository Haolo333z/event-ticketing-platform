import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {execFileSync,spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createInterface} from 'node:readline';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {classify,summarize} from './statistics.mjs';

const base=process.env.LOAD_BASE, project=process.env.COMPOSE_PROJECT_NAME;
const output=process.env.LOAD_OUTPUT, container=project+'-postgres-1', database=process.env.POSTGRES_DB;
const requests=Number(process.env.LOAD_REQUESTS??50), rounds=Number(process.env.LOAD_ROUNDS??3);
assert(/^ticketing-load-[a-f0-9]{12}$/.test(project??''),'Owned project required');
assert.equal(database,'ticketing_load');assert(output);
assert(Number.isInteger(requests)&&requests>=50&&requests<=200);assert(Number.isInteger(rounds)&&rounds>=1&&rounds<=10);
assert.equal(new URL(base).hostname,'127.0.0.1');assert.equal(new URL(base).protocol,'http:');
assert.equal(execFileSync('docker',['inspect','--format','{{ index .Config.Labels "com.docker.compose.project" }}',container],{encoding:'utf8'}).trim(),project);
await mkdir(output,{recursive:true});
const fixtureToken={CUSTOMER:'demo-customer-token',ADMIN:'demo-admin-token'};
async function api(route,role='CUSTOMER',body) {
  const response=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+fixtureToken[role],...(body===undefined?{}:{'Content-Type':'application/json'})},
    ...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(15000)});
  const data=await response.json();
  assert(response.ok,`Setup/settlement API failed: HTTP${response.status}, code=${data.code??'unknown'}`);
  return data;
}
const uuid=/^[0-9a-f-]{36}$/i;
function id(value){assert(uuid.test(value),'Fixture UUID required');return "'"+value+"'::uuid";}
function inIds(values){return values.length?values.map(id).join(','):'NULL::uuid';}
function auditSql(event, samples) {
  const eventId=id(event.id), keys=inIds(samples.map(s=>s.requestId));
  return `WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id=${eventId}),
    i AS (SELECT * FROM ticketing.booking_items WHERE event_id=${eventId}),
    s AS (SELECT * FROM ticketing.seats WHERE event_id=${eventId}),
    k AS (SELECT * FROM ticketing.idempotency_keys WHERE key IN (${samples.map(s=>"'"+s.requestId+"'").join(',')})),
    g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id=${eventId})
    SELECT jsonb_build_object(
    'bookingIds',coalesce((SELECT jsonb_agg(id ORDER BY id) FROM b),'[]'::jsonb),
    'bookings',(SELECT count(*) FROM b),'pending',(SELECT count(*) FROM b WHERE status='PENDING_PAYMENT'),
    'confirmed',(SELECT count(*) FROM b WHERE status='CONFIRMED'),
    'itemQuantity',coalesce((SELECT sum(quantity) FROM i),0),'items',(SELECT count(*) FROM i),
    'capacity',coalesce((SELECT capacity FROM g),(SELECT count(*) FROM s)),
    'held',coalesce((SELECT held_count FROM g),(SELECT count(*) FROM s WHERE status='HELD')),
    'sold',coalesce((SELECT sold_count FROM g),(SELECT count(*) FROM s WHERE status='SOLD')),
    'oversell',coalesce((SELECT greatest(held_count+sold_count-capacity,0) FROM g),greatest((SELECT count(*) FROM i JOIN b ON b.id=i.booking_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED'))-(SELECT count(*) FROM s),0)),
    'negativeInventory',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE b.status='CONFIRMED')),
    'duplicateActiveSeatAllocations',(SELECT count(*) FROM (SELECT i.seat_id FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY i.seat_id HAVING count(*)>1) d),
    'seatOwnershipMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanSeatOwners',(SELECT count(*) FROM s WHERE s.status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'partialBookings',(SELECT count(*) FROM b WHERE (SELECT coalesce(sum(quantity),0) FROM i WHERE i.booking_id=b.id)<>${event.mode==='GA'?1:2}),
    'keys',(SELECT count(*) FROM k),
    'incompleteKeys',(SELECT count(*) FROM k WHERE booking_id IS NULL OR response_status IS DISTINCT FROM 202 OR response_body->>'bookingId' IS DISTINCT FROM booking_id::text OR response_body->>'status' IS DISTINCT FROM 'PENDING_PAYMENT'),
    'unmappedBookings',(SELECT count(*) FROM b WHERE NOT EXISTS(SELECT 1 FROM k WHERE k.booking_id=b.id)),
    'unexpectedKeys',(SELECT count(*) FROM k WHERE booking_id NOT IN (${inIds(samples.filter(s=>s.outcome==='success').map(s=>s.bookingId))})),
    'heldEvents',(SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b) AND type='BookingHeld'),
    'confirmedEvents',(SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b) AND type='BookingConfirmed'),
    'pendingHistory',(SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) AND to_status='PENDING_PAYMENT'),
    'confirmedHistory',(SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) AND to_status='CONFIRMED'),
    'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
    'badTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE t.booking_id<>b.id OR b.status<>'CONFIRMED' OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT i.seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE i.seat_id IS NOT NULL GROUP BY i.seat_id HAVING count(*)>1) d),
    'duplicateTicketOrdinals',(SELECT count(*) FROM (SELECT t.booking_item_id,t.ticket_ordinal FROM ticketing.tickets t WHERE t.booking_id IN (SELECT id FROM b) GROUP BY t.booking_item_id,t.ticket_ordinal HAVING count(*)>1) d));`;
}
function query(sql) {
  // Single SQL snapshot, read-only connection and bounded execution; never receives workspace credentials.
  return JSON.parse(execFileSync('docker',['exec','-i','-e','PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=5000',container,
    'psql','-XqAt','-U',process.env.POSTGRES_USER,'-d',database,'-v','ON_ERROR_STOP=1'],{input:sql,encoding:'utf8',maxBuffer:1024*1024}).trim());
}
function violations(snapshot,samples,mode,settled) {
  const success=samples.filter(s=>s.outcome==='success'), count=success.length, qty=count*(mode==='GA'?1:2), bad=[];
  const expected={bookings:count,itemQuantity:qty,items:mode==='GA'?count:qty,capacity:mode==='GA'?10:2,
    pending:settled?0:count,confirmed:settled?count:0,held:settled?0:qty,sold:settled?qty:0,keys:count,
    heldEvents:count,pendingHistory:count,confirmedEvents:settled?count:0,confirmedHistory:settled?count:0,tickets:settled?qty:0};
  for(const [key,value]of Object.entries(expected))if(snapshot[key]!==value)bad.push(`${key}:expected${value}/actual${snapshot[key]}`);
  for(const key of ['oversell','negativeInventory','gaQuantityMismatch','duplicateActiveSeatAllocations','seatOwnershipMismatch','orphanSeatOwners','partialBookings','incompleteKeys','unmappedBookings','unexpectedKeys','badTickets','duplicateSeatTickets','duplicateTicketOrdinals'])if(snapshot[key]!==0)bad.push(`${key}:${snapshot[key]}`);
  if(JSON.stringify([...snapshot.bookingIds].sort())!==JSON.stringify(success.map(s=>s.bookingId).sort()))bad.push('HTTP success IDs differ from committed bookings');
  return bad;
}
const report={task:'T9.2',status:'RUNNING',project,requestsPerBurst:requests,roundsPerMode:rounds,
  protocol:'HTTP/1.1',workers:requests,clock:'System.nanoTime; full response body',percentileMethod:'nearest-rank',
  throughputDefinition:'completed requests / (gate release to last body read); burst rate, not sustained TPS',
  warmup:'10requests per mode, separate fixtures, excluded from measured aggregates; same backend/client reused',measurements:[],warmups:[]};
async function save(){await writeFile(path.join(output,'load-results.json'),JSON.stringify(report,null,2));}
const client=spawn('java',['-Xms128m','-Xmx256m',path.join(path.dirname(fileURLToPath(import.meta.url)),'HttpBurst.java'),base,String(requests)],
  {env:{...process.env,LOAD_CUSTOMER_TOKEN:fixtureToken.CUSTOMER},stdio:['pipe','pipe','pipe']});
client.stderr.pipe(createWriteStream(path.join(output,'client.stderr.log')));
let pending, resolveReady,rejectReady;
const ready=new Promise((resolve,reject)=>{resolveReady=resolve;rejectReady=reject;});
const exited=new Promise(resolve=>client.once('exit',resolve));
createInterface({input:client.stdout}).on('line',line=>{if(line==='READY')resolveReady();if(line==='RESULT'&&pending){pending.resolve();pending=null;}});
client.on('error',error=>{rejectReady(error);pending?.reject(error);});
client.on('exit',code=>{if(code!==0){const e=new Error('Java transport failed: '+code);rejectReady(e);pending?.reject(e);}});
async function burst(name,payload,n) {
  const rawPath=path.join(output,name+'.transport.json'), properties=path.join(output,name+'.properties');
  await writeFile(properties,`requests=${n}\nbodyEven=${JSON.stringify(payload)}\nbodyOdd=${JSON.stringify(payload.seatIds?{...payload,seatIds:[...payload.seatIds].reverse()}:payload)}\noutput=${rawPath.replaceAll('\\','/')}\n`);
  await new Promise((resolve,reject)=>{pending={resolve,reject};client.stdin.write(properties+'\n');});
  const raw=JSON.parse(await readFile(rawPath,'utf8'));
  const samples=raw.samples.map(r=>{
    let response;try{response=JSON.parse(r.responseBody);}catch{}
    const outcome=classify({...r,response});
    return {requestId:r.requestId,worker:r.worker,startOffsetMs:r.startOffsetMs,latencyMs:r.latencyMs,status:r.status,
      outcome,errorCode:response?.code??r.transportError??(outcome==='unexpectedResponse'?'INVALID_RESPONSE':null),bookingId:outcome==='success'?response.bookingId:null};
  });
  // Raw booking responses contain only owned test fixtures; published samples omit bodies/headers/tokens.
  return {samples,summary:summarize(samples.map(s=>({...s,response:s.outcome==='success'?{bookingId:s.bookingId,status:'PENDING_PAYMENT'}:{code:s.errorCode}})),raw.elapsedMs),
    readyWorkers:raw.readyWorkers,peakInFlight:raw.peakInFlight,distinctWorkers:new Set(samples.map(s=>s.worker)).size,
    startSpreadMs:Math.max(...samples.map(s=>s.startOffsetMs))-Math.min(...samples.map(s=>s.startOffsetMs))};
}
async function run(mode,round,warmup=false) {
  const name=(warmup?'warmup':'round'+round)+'-'+mode.toLowerCase(), n=warmup?10:requests;
  const event=await api('/api/admin/events','ADMIN',{title:`LOAD-01 ${project} ${name}`,description:'Owned isolated load fixture',venue:'Loopback test',
    saleStartAt:'2020-01-01T00:00:00Z',saleEndAt:'2099-01-01T00:00:00Z',startsAt:'2099-02-01T00:00:00Z',status:'PUBLISHED',
    ticketTypes:[{name:'Load '+mode,mode,priceVnd:100000,...(mode==='GA'?{capacity:10}:{seats:[{label:'A1'},{label:'A2'}]})}]});
  event.mode=mode;
  const type=event.ticketTypes[0];
  const availability=await api(`/api/events/${event.id}/availability`);
  const payload={eventId:event.id,ticketTypeId:type.id,...(mode==='GA'?{quantity:1}:{seatIds:availability.ticketTypes[0].seats.map(s=>s.id)})};
  const result={name,mode,round,warmup,eventId:event.id,ticketTypeId:type.id,...await burst(name,payload,n),violations:[]};
  const expectedSuccess=mode==='GA'?Math.min(n,10):1;
  if(result.summary.otherErrors)result.violations.push('Unexpected HTTP/transport errors:'+result.summary.otherErrors);
  if(result.summary.success!==expectedSuccess||result.summary.expectedConflict!==n-expectedSuccess)result.violations.push('Unexpected success/conflict totals');
  if(!warmup&&(result.readyWorkers!==n||result.distinctWorkers!==n||result.peakInFlight<n))result.violations.push('Required simultaneous50+workers/in-flight not observed');
  result.sql=auditSql(event,result.samples);result.afterHold=query(result.sql);
  result.violations.push(...violations(result.afterHold,result.samples,mode,false));
  if(!result.violations.length) {
    const ids=result.samples.filter(s=>s.outcome==='success').map(s=>s.bookingId);
    await Promise.all(ids.map(booking=>api(`/api/bookings/${booking}/simulate-payment`,'CUSTOMER',{scenario:'SUCCESS',requestId:randomUUID()})));
    const deadline=Date.now()+45000;
    while(true){const states=await Promise.all(ids.map(booking=>api(`/api/bookings/${booking}`)));if(states.every(b=>b.status==='CONFIRMED'))break;if(Date.now()>deadline)throw new Error('Settlement deadline');await new Promise(r=>setTimeout(r,100));}
    result.afterSale=query(result.sql);result.violations.push(...violations(result.afterSale,result.samples,mode,true));
  }
  result.status=result.violations.length?'FAIL':'PASS';
  report[warmup?'warmups':'measurements'].push(result);await save();
  console.log(JSON.stringify({name,status:result.status,...result.summary,peakInFlight:result.peakInFlight,violations:result.violations}));
}
try {
  await ready;
  const max=await api('/actuator/metrics/hikaricp.connections.max','ADMIN'), min=await api('/actuator/metrics/hikaricp.connections.min','ADMIN');
  report.observedPool={max:max.measurements[0].value,min:min.measurements[0].value,source:'Actuator Hikari gauges'};
  assert.equal(report.observedPool.max,10);assert.equal(report.observedPool.min,2);
  await run('GA',0,true);await run('SEATED',0,true);
  for(let round=1;round<=rounds;round++){await run('GA',round);await run('SEATED',round);}
  report.status=[...report.warmups,...report.measurements].every(r=>r.status==='PASS')?'PASS':'FAIL';
  report.aggregates=Object.fromEntries(['GA','SEATED'].map(mode=>{const rows=report.measurements.filter(r=>r.mode===mode);return [mode,summarize(rows.flatMap(r=>r.samples.map(s=>({...s,response:s.outcome==='success'?{bookingId:s.bookingId,status:'PENDING_PAYMENT'}:{code:s.errorCode}}))),rows.reduce((n,r)=>n+r.summary.elapsedMs,0))];}));
}catch(error){report.status='FAIL';report.error=error.message;throw error;}
finally{client.stdin.end('QUIT\n');await exited;await save();}
if(report.status!=='PASS')throw new Error('Load acceptance failed; inspect load-results.json including all errors');
