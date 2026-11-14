export function percentiles(values) {
  if (!values.length) return {count:0,p50Ms:null,p95Ms:null,p99Ms:null,maxMs:null};
  if (values.some(n=>!Number.isFinite(n)||n<0)) throw new Error('Invalid latency');
  const sorted=[...values].sort((a,b)=>a-b), rank=p=>sorted[Math.ceil(p*sorted.length)-1];
  return {count:sorted.length,p50Ms:rank(.5),p95Ms:rank(.95),p99Ms:rank(.99),maxMs:sorted.at(-1)};
}
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function classify(row) {
  if (row.transportError || row.status===0) return 'transportError';
  if (row.status===202 && uuid.test(row.response?.bookingId) && row.response.status==='PENDING_PAYMENT') return 'success';
  if (row.status===409 && row.response?.code==='INVENTORY_UNAVAILABLE') return 'expectedConflict';
  return 'unexpectedResponse';
}
export function summarize(samples,elapsedMs) {
  if (!(elapsedMs>0) || !Number.isFinite(elapsedMs)) throw new Error('Invalid measurement window');
  const groups=Object.groupBy(samples,classify), success=groups.success?.length??0;
  return {requests:samples.length,success,expectedConflict:groups.expectedConflict?.length??0,
    otherErrors:(groups.transportError?.length??0)+(groups.unexpectedResponse?.length??0),
    outcomes:Object.fromEntries(Object.entries(groups).map(([key,rows])=>[key,percentiles(rows.map(r=>r.latencyMs))])),
    latency:percentiles(samples.map(r=>r.latencyMs)),elapsedMs,
    completedRequestsPerSecond:samples.length*1000/elapsedMs,successfulBookingsPerSecond:success*1000/elapsedMs};
}
