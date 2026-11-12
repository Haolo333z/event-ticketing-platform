import fs from 'node:fs';
import assert from 'node:assert/strict';
import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const root = new URL('../../docs/contracts/', import.meta.url);
const read = name => JSON.parse(fs.readFileSync(new URL(name, root), 'utf8'));
const api = await SwaggerParser.validate(read('openapi.json'));
const ajv = new Ajv({strict: false, allErrors: true}); addFormats(ajv);
const compile = schema => ajv.compile(schema);
const fixtures = read('examples.json');
let cases = 0;
function check(schema, value, expected) {
  const validate = compile(schema);
  assert.equal(validate(value), expected, JSON.stringify(validate.errors)); cases++;
}
const create = api.components.schemas.CreateBooking;
check(create, fixtures.bookingGa, true);
check(create, fixtures.bookingSeats, true);
for (const value of [
  {...fixtures.bookingGa,quantity:0}, {...fixtures.bookingGa,quantity:7},
  {...fixtures.bookingGa,quantity:1.5}, {...fixtures.bookingGa,userId:fixtures.bookingGa.eventId},
  {...fixtures.bookingGa,totalAmountVnd:1}, {...fixtures.bookingGa,seatIds:fixtures.bookingSeats.seatIds},
  {...fixtures.bookingSeats,seatIds:[]}, {...fixtures.bookingSeats,seatIds:[...fixtures.bookingSeats.seatIds,fixtures.bookingGa.eventId]},
  {...fixtures.bookingSeats,seatIds:[fixtures.bookingGa.eventId,fixtures.bookingGa.eventId]},
  {...fixtures.bookingGa,eventId:'invalid-uuid'},
]) check(create,value,false);
check(api.components.schemas.CreateEvent, fixtures.catalogMax, true);
check(api.components.schemas.PatchEvent, {}, false);
check(api.components.schemas.SimulatePayment,{scenario:'SUCCESS',requestId:fixtures.bookingGa.eventId},true);
check(api.components.schemas.SimulatePayment,{scenario:'SUCCESS',requestId:fixtures.bookingGa.eventId,recordedAt:'2026-11-12T00:00:00Z'},false);
const eventSchema = read('events.schema.json');
for (const value of fixtures.events) check(eventSchema,value,true);
check(eventSchema,{...fixtures.events[0],schemaVersion:2},false);
check(eventSchema,{...fixtures.events[0],aggregateVersion:0},false);
check(eventSchema,{...fixtures.events[0],eventType:'Unknown'},false);
check(eventSchema,{...fixtures.events[0],token:'forbidden'},false);
check(eventSchema,{...fixtures.events[0],payload:{bookingId:'bad'}},false);
const ids = new Set(); let operations=0;
for (const [path,item] of Object.entries(api.paths)) for (const [method,op] of Object.entries(item)) {
  assert(!ids.has(op.operationId)); ids.add(op.operationId); operations++;
  for (const match of path.matchAll(/\{(\w+)\}/g)) assert(op.parameters.some(p=>p.in==='path'&&p.name===match[1]&&p.required));
  assert(op['x-role']);
  if(op['x-role']!=='PUBLIC') {assert(op.security.length); assert(op.responses['401']); assert(op.responses['403']);}
  if(op.requestBody) {assert(op.responses['413']); assert(op.responses['415']); assert(op['x-max-body-bytes']);}
  if(op.responses['503']) assert(op.responses['503'].headers['Retry-After']);
  for(const res of Object.values(op.responses)) for(const code of res['x-error-codes']??[]) assert(api.components.schemas.Error.properties.code.enum.includes(code));
  if(op.operationId==='simulatePayment'||op.operationId==='replayEvent'||op.operationId==='listOutbox') assert.deepEqual(op['x-profiles'],['local','test']);
}
assert(api.paths['/api/bookings'].post.responses['202'].headers.Location);
assert.deepEqual(api.components.schemas.BookingStatus.enum,['PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED']);
const cors=api['x-local-cors']; assert(!cors.origins.includes('*')); assert.equal(cors.allowCredentials,false);
for (const key of ['Authorization','Idempotency-Key','Content-Type']) assert(cors.allowedHeaders.includes(key));
for (const [key,fixture] of Object.entries(fixtures).filter(([key])=>key!=='events')) {
  const bytes=Buffer.byteLength(JSON.stringify(fixture),'utf8');
  const limit=key==='catalogMax'?131072:8192; assert(bytes<limit);
  console.log(`${key}: ${bytes} UTF-8 bytes / ${limit} byte limit`);
}
// Every selected state appears in the source PRD; no accidental extra state.
const prd=fs.readFileSync(new URL('../../docs/PRD.md',import.meta.url),'utf8');
for(const state of api.components.schemas.BookingStatus.enum) assert(prd.includes(state));
const machine=read('booking-transitions.json');
assert.deepEqual(Object.keys(machine.transitions),api.components.schemas.BookingStatus.enum);
assert.deepEqual(machine.transitions.PENDING_PAYMENT,['CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED']);
assert.deepEqual(machine.transitions.EXPIRED,['REFUND_REQUIRED']);
for(const state of ['CONFIRMED','PAYMENT_FAILED','CANCELLED','REFUND_REQUIRED']) assert.equal(machine.transitions[state].length,0);
for(const [source,targets] of Object.entries(machine.transitions)) for(const target of targets) assert(prd.includes(`${source} --> ${target}`));
console.log(`PASS: OpenAPI 3.1 validated; ${operations} operations; ${cases} positive/negative payload cases; roles, headers, CORS and states checked.`);
