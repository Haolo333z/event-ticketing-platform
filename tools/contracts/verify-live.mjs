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
console.log(`PASS: ${checks} live HTTP responses match OpenAPI schemas.`);
