import type {Store} from '../api/client.ts';
import type {ReplayRequest} from '../api/admin.ts';
export type ReplayAttempt=ReplayRequest&{eventId:string};
const key='eventflow.pending-replay.v1';
export function replayStore():Store|null{try{return window.sessionStorage;}catch{return null;}}
export function readReplayAttempt(store:Store|null):ReplayAttempt|null{
  try{
    const value=JSON.parse(store?.getItem(key)??'null');const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if(value&&uuid.test(value.eventId)&&uuid.test(value.requestId)&&typeof value.reason==='string'&&value.reason.trim()&&Array.from(value.reason).length<=500)return{eventId:value.eventId,requestId:value.requestId,reason:value.reason};
  }catch{/* No automatic POST from corrupt or inaccessible browser state. */}return null;
}
export function saveReplayAttempt(store:Store|null,attempt:ReplayAttempt|null){try{if(attempt)store?.setItem(key,JSON.stringify(attempt));else store?.removeItem(key);}catch{/* Keep the same in-memory request; never silently create a new one. */}}
