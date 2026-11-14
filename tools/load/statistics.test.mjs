import {test} from 'node:test';
import assert from 'node:assert/strict';
import {percentiles,classify,summarize} from './statistics.mjs';
const id='00000000-0000-4000-8000-000000000001';
test('nearest rank for50samples makes p99 the maximum, without interpolation',()=>{
  const values=Array.from({length:50},(_,i)=>50-i),before=[...values];
  assert.deepEqual(percentiles(values),{count:50,p50Ms:25,p95Ms:48,p99Ms:50,maxMs:50});assert.deepEqual(values,before);
});
test('empty outcomes do not fabricate zero percentiles',()=>assert.equal(percentiles([]).p95Ms,null));
test('nonfinite and negative latencies are rejected',()=>{for(const n of [NaN,Infinity,-1])assert.throws(()=>percentiles([n]));});
test('only validated202 and known inventory409 are expected outcomes',()=>{
  assert.equal(classify({status:202,response:{bookingId:id,status:'PENDING_PAYMENT'}}),'success');
  assert.equal(classify({status:409,response:{code:'INVENTORY_UNAVAILABLE'}}),'expectedConflict');
  for(const [status,code]of [[503,'BOOKING_BUSY'],[409,'IDEMPOTENCY_KEY_REUSED'],[400,'VALIDATION_ERROR'],[401,'UNAUTHORIZED'],[500,'INTERNAL_ERROR']])
    assert.equal(classify({status,response:{code}}),'unexpectedResponse');
});
test('malformed success and transport timeouts cannot become success or conflict',()=>{
  assert.equal(classify({status:202,response:{bookingId:'invalid',status:'PENDING_PAYMENT'}}),'unexpectedResponse');
  assert.equal(classify({status:0,transportError:'HttpTimeoutException'}),'transportError');
});
test('throughput counts all completed requests but reports success rate separately',()=>{
  const s=summarize([{status:202,response:{bookingId:id,status:'PENDING_PAYMENT'},latencyMs:10},
    {status:409,response:{code:'INVENTORY_UNAVAILABLE'},latencyMs:20},{status:503,response:{code:'BOOKING_BUSY'},latencyMs:30}],100);
  assert.equal(s.completedRequestsPerSecond,30);assert.equal(s.successfulBookingsPerSecond,10);assert.equal(s.otherErrors,1);assert.equal(s.expectedConflict,1);
});
test('zero or invalid elapsed time cannot produce fictional throughput',()=>{for(const n of [0,-1,NaN,Infinity])assert.throws(()=>summarize([],n));});
