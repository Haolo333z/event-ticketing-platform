import assert from 'node:assert/strict';
import fs from 'node:fs';
import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const api=await SwaggerParser.validate(JSON.parse(fs.readFileSync(new URL('../../docs/contracts/openapi.json',import.meta.url),'utf8')));
const ajv=new Ajv({strict:false,allErrors:true});addFormats(ajv);
const base=process.env.API_BASE_URL??'http://127.0.0.1:8080';let checks=0;
async function check(path,schema,status=200,options={}){
    const response=await fetch(base+path,options);const body=await response.json();assert.equal(response.status,status,JSON.stringify(body));
    const validate=ajv.compile(api.components.schemas[schema]);assert(validate(body),JSON.stringify(validate.errors));
    if(schema==='BookingAccepted')assert.equal(response.headers.get('Location'),'/api/bookings/'+body.bookingId);
    checks++;return body;
}
await check('/api/events','EventPage');
for(const id of ['20000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002']){
    await check('/api/events/'+id,'Event');await check('/api/events/'+id+'/availability','Availability');
}
await check('/api/events?size=101','Error',400);
await check('/api/events/99999999-0000-4000-8000-000000000001','Error',404);
await check('/api/admin/events','Error',401,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});
await check('/api/admin/events','Error',403,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer demo-customer-token'},body:'{}'});
const command={eventId:'20000000-0000-4000-8000-000000000002',ticketTypeId:'30000000-0000-4000-8000-000000000002',quantity:1};
const auth={Authorization:'Bearer demo-customer-token','Content-Type':'application/json','Idempotency-Key':'live-contract-part4-v1'};
const accepted=await check('/api/bookings','BookingAccepted',202,{method:'POST',headers:auth,body:JSON.stringify(command)});
const replay=await check('/api/bookings','BookingAccepted',202,{method:'POST',headers:auth,body:JSON.stringify(command)});
assert.deepEqual(replay,accepted);
await check('/api/bookings/'+accepted.bookingId,'Booking',200,{headers:auth});
await check('/api/me/bookings','BookingPage',200,{headers:auth});
await check('/api/bookings','Error',409,{method:'POST',headers:auth,body:JSON.stringify({...command,quantity:2})});
await check('/api/bookings','Error',400,{method:'POST',headers:auth,body:JSON.stringify({...command,userId:'untrusted'})});
console.log(`PASS: ${checks} live HTTP responses match OpenAPI schemas.`);
