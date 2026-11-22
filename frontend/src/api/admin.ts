import {demoTransport,getStoredToken,getStoredUser} from './client.ts';
import type {Event,Booking,Status,Seat} from './client.ts';

export type Page<T>={items:T[];page:number;size:number;totalElements:number};
export type AdminEvent=Event&{saleStartAt:string;saleEndAt:string;status:'DRAFT'|'PUBLISHED'|'CLOSED'};
export type AdminInventory={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';capacity:number;held:number;sold:number;available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};
export type Outbox={eventId:string;bookingId:string;eventType:string;aggregateVersion:number;status:'PENDING'|'PUBLISHED'|'FAILED';createdAt:string;attempts:number};
export type RecoveryFilter='OPEN'|'RESOLVED'|'ALL';
export type ReplayRequest={requestId:string;reason:string};
export type ReplayAccepted={eventId:string;requestId:string;status:'ACCEPTED'};
export type RecoveryItem={id:string;source:'OUTBOX'|'CONSUMER';eventId:string|null;bookingId:string|null;eventType:string|null;consumer:string|null;status:string;error:string;createdAt:string;dlqStatus:'PENDING'|'PUBLISHED'|'EXHAUSTED'|null;replayable:boolean};
export type RecoveryEvent=Outbox&{correlationId:string;publishedAt:string|null;publisherError:string;remainingReplays:number;
  audit:{requestId:string;adminAccountId:string;reason:string;requestedAt:string}[];
  consumers:{consumer:string;processedAt:string}[];
  failures:{id:string;consumer:string;status:'DEAD'|'RESOLVED';error:string;attempts:number;createdAt:string;resolvedAt:string|null;dlqStatus:string;dlqAttempts:number}[]};
export type TypeInput={name:string;priceVnd:number}&({mode:'GA';capacity:number}|{mode:'SEATED';seats:{label:string}[]});
export type CreateEvent={title:string;description:string;venue:string;saleStartAt:string;saleEndAt:string;startsAt:string;status:AdminEvent['status'];ticketTypes:TypeInput[]};
export type PatchEvent=Partial<Omit<CreateEvent,'ticketTypes'>>&{ticketTypes?:{id:string;name?:string;priceVnd?:number;active?:boolean;capacity?:number}[]};

export type AdminUser={
  id:string;
  displayName:string;
  email:string;
  phone?:string;
  role:string;
  roles?:string[];
  status?:'ACTIVE'|'LOCKED';
  createdAt:string;
};

export type ContactLeadItem={
  id:string;
  fullName:string;
  email:string;
  phone:string;
  companyName:string;
  eventScale?:string;
  saasPlan?:string;
  message:string;
  status?:'NEW'|'CONTACTED'|'CONVERTED'|'CLOSED';
  notes?:string;
  createdAt:string;
  updatedAt?:string;
};

export type AdminRefundItem={
  id:string;
  bookingId:string;
  eventTitle:string;
  customerName:string;
  customerEmail:string;
  amountVnd:number;
  reason?:string;
  status:'REQUESTED'|'PROCESSING'|'COMPLETED'|'FAILED';
  createdAt:string;
  settledAt?:string;
};

export type PlatformSummary={
  totalRevenueVnd:number;
  totalTicketsSold:number;
  totalConfirmedBookings:number;
  totalCheckIns:number;
  averageOccupancyPercent:number;
};

export type SellOutForecast = {
  eventId: string;
  eventTitle: string;
  status: string;
  totalCapacity: number;
  soldTickets: number;
  heldTickets: number;
  availableTickets: number;
  soldLast24Hours: number;
  salesVelocityPerHour: number;
  heldPercentage: number;
  occupancyPercentage: number;
  waitlistCount: number;
  estimatedHoursToSellOut: number | null;
  sellOutStatus: 'SOLD_OUT' | 'SELL_OUT_IMMINENT' | 'HIGH_DEMAND' | 'NORMAL' | 'STEADY';
  recommendVirtualWaitingRoom: boolean;
  recommendationVi: string;
  adapterUsed: string;
};

export type ScalpingAlert = {
  id: string;
  alertType: 'MASS_HOLD_SAME_IP' | 'HOARDING_ABANDON_BURST' | 'MULTI_ACCOUNT_SAME_CARD' | string;
  eventId: string;
  eventTitle: string;
  targetAccountId: string | null;
  targetAccountEmail: string;
  targetAccountName: string;
  clientIp: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  explanationVi: string;
  status: 'NEW' | 'INVESTIGATING' | 'DISMISSED' | 'ACTION_TAKEN';
  evidence: Record<string, any>;
  createdAt: string;
  resolvedAt: string | null;
};

export type ScalpingActionResponse = {
  alertId: string;
  actionTaken: string;
  status: string;
  messageVi: string;
  releasedBookingsCount: number;
};

export type EventReport={
  eventId:string;
  title:string;
  venue:string;
  startsAt:string|null;
  status:string;
  totalCapacity:number;
  soldGa:number;
  soldSeated:number;
  totalSold:number;
  revenueVnd:number;
  occupancyPercent:number;
};

export type CircuitBreakerStatus={
  provider:string;
  state:'CLOSED'|'OPEN'|'HALF_OPEN';
  failureCount:number;
  failureThreshold:number;
  resetTimeoutSeconds:number;
  remainingCooldownSeconds:number;
  lastFailureReason:string|null;
  lastChangedAt:string;
};

export type ReconciliationRunSummary={
  id:string;
  reconciliationDate:string;
  provider:string;
  totalTransactions:number;
  matchedCount:number;
  discrepancyCount:number;
  totalGatewayAmountVnd:number;
  totalSystemAmountVnd:number;
  differenceAmountVnd:number;
  status:'RUNNING'|'COMPLETED'|'DISCREPANCY_FOUND'|'FAILED';
  triggeredBy:string;
  startedAt:string;
  completedAt:string|null;
  notes:string|null;
};

export type ReconciliationDiscrepancy={
  id:string;
  runId:string;
  merchantRef:string|null;
  providerTxRef:string|null;
  bookingId:string|null;
  discrepancyType:'MATCHED'|'AMOUNT_MISMATCH'|'MISSING_IN_GATEWAY'|'MISSING_IN_DB';
  systemAmountVnd:number;
  gatewayAmountVnd:number;
  differenceVnd:number;
  status:'OPEN'|'INVESTIGATING'|'RESOLVED'|'IGNORED';
  createdAt:string;
  resolvedAt:string|null;
  resolutionNotes:string|null;
};

export type ReconciliationRunDetail={
  summary:ReconciliationRunSummary;
  discrepancies:ReconciliationDiscrepancy[];
};

export interface AdminApi{
  events(page:number,size:number):Promise<Page<AdminEvent>>;inventory(id:string):Promise<AdminInventory>;
  create(body:CreateEvent):Promise<AdminEvent>;patch(id:string,body:PatchEvent):Promise<AdminEvent>;
  bookings(page:number,size:number,status?:Status):Promise<Page<Booking>>;booking(id:string):Promise<Booking>;
  users?(page:number,size:number,search?:string,role?:string):Promise<Page<AdminUser>>;
  updateUserRole?(userId:string,role:string,action:'GRANT'|'REVOKE'):Promise<{id:string;roles:string[]}>;
  updateUserStatus?(userId:string,status:'ACTIVE'|'LOCKED'):Promise<{id:string;status:string}>;
  refunds?(status?:string):Promise<AdminRefundItem[]>;
  processRefund?(refundId:string,decision:'APPROVE'|'REJECT',notes?:string):Promise<{refundId:string;status:string;message:string}>;
  reports?:{
    summary():Promise<PlatformSummary>;
    events():Promise<EventReport[]>;
    exportCsvUrl():string;
  };
  circuitBreaker?:{
    getStatus():Promise<CircuitBreakerStatus>;
    reset():Promise<CircuitBreakerStatus>;
    trip(reason?:string):Promise<CircuitBreakerStatus>;
  };
  reconciliation?:{
    run(date?:string,simulationMode?:string):Promise<ReconciliationRunDetail>;
    getRuns(page?:number,size?:number):Promise<ReconciliationRunSummary[]>;
    getRunDetail(id:string):Promise<ReconciliationRunDetail>;
    updateStatus(id:string,status:string,notes?:string):Promise<{success:boolean}>;
    exportCsvUrl(id:string):string;
  };
  leads?(page:number,size:number,status?:string):Promise<Page<ContactLeadItem>>;
  updateLeadStatus?(leadId:string,status:'NEW'|'CONTACTED'|'CONVERTED'|'CLOSED',notes?:string):Promise<ContactLeadItem>;
  convertToOrganizer?(leadId:string):Promise<{leadId:string;userId:string;organizerProfileId:string;organizationName:string;email:string;saasPlan:string;status:string;message:string}>;
  payouts?(status?:string):Promise<import('./client.ts').OrganizerPayoutItem[]>;
  processPayout?(payoutId:string,decision:'APPROVE'|'PAID'|'REJECT',adminNotes?:string):Promise<{payoutId:string;status:string;processedAt:string;message:string}>;
  outbox(page:number,size:number):Promise<Page<Outbox>>;
  recovery(page:number,size:number,status:RecoveryFilter):Promise<Page<RecoveryItem>>;
  recoveryEvent(id:string):Promise<RecoveryEvent>;
  replayEvent(id:string,request:ReplayRequest):Promise<ReplayAccepted>;
  aiDiagnose?(data?:{bookingId?:string;failureId?:string}):Promise<{adapterUsed:string;severity:'HIGH'|'MEDIUM'|'LOW';summaryVi:string;rootCause:string;recommendedAction:string;safeToReplay:boolean;evidence:Record<string,unknown>}>;
  scalpingAlerts?(status?:string,eventId?:string):Promise<ScalpingAlert[]>;
  actionScalpingAlert?(id:string,action:'LOCK_ACCOUNT'|'DISMISS'|'ACTION_TAKEN',notes?:string):Promise<ScalpingActionResponse>;
  sellOutForecast?(eventId:string):Promise<SellOutForecast>;
}
export function createAdminApi(base='http://127.0.0.1:8080',fetcher:typeof fetch=globalThis.fetch):AdminApi{
  const tokenProvider=()=>{
    const user=getStoredUser();
    const token=getStoredToken();
    if(user&&token&&(user.roles?.includes('ADMIN')||user.role==='ADMIN')){
      return token;
    }
    return 'demo-admin-token';
  };
  const call=demoTransport(base,fetcher,tokenProvider);
  return{
    events:(page,size)=>call('/api/admin/events?'+new URLSearchParams({page:String(page),size:String(size)})),
    inventory:id=>call('/api/admin/events/'+encodeURIComponent(id)+'/inventory'),
    create:body=>call('/api/admin/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}),
    patch:(id,body)=>call('/api/admin/events/'+encodeURIComponent(id),{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}),
    bookings:(page,size,status)=>call('/api/admin/bookings?'+new URLSearchParams({page:String(page),size:String(size),...(status?{status}:{})})),
    booking:id=>call('/api/bookings/'+encodeURIComponent(id)),
    users:(page,size,search,role)=>call('/api/admin/users?'+new URLSearchParams({page:String(page),size:String(size),...(search?{search}:{}),...(role?{role}:{})})),
    updateUserRole:(userId,role,action)=>call('/api/admin/users/'+encodeURIComponent(userId)+'/roles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({role,action})}),
    updateUserStatus:(userId,status)=>call('/api/admin/users/'+encodeURIComponent(userId)+'/status',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status})}),
    refunds:(status)=>call('/api/admin/refunds'+(status?'?status='+encodeURIComponent(status):'')),
    processRefund:(refundId,decision,notes)=>call('/api/admin/refunds/'+encodeURIComponent(refundId)+'/process',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({decision,notes})}),
    reports:{
      summary:()=>call('/api/admin/reports/summary'),
      events:()=>call('/api/admin/reports/events'),
      exportCsvUrl:()=>base+'/api/admin/reports/export-csv'
    },
    circuitBreaker:{
      getStatus:()=>call('/api/admin/payments/circuit-breaker'),
      reset:()=>call('/api/admin/payments/circuit-breaker/reset',{method:'POST'}),
      trip:(reason)=>call('/api/admin/payments/circuit-breaker/trip',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason})})
    },
    reconciliation:{
      run:(date,simulationMode)=>call('/api/admin/reconciliation/run',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({date,simulationMode})}),
      getRuns:(page=0,size=20)=>call('/api/admin/reconciliation/runs?'+new URLSearchParams({page:String(page),size:String(size)})),
      getRunDetail:(id)=>call('/api/admin/reconciliation/runs/'+encodeURIComponent(id)),
      updateStatus:(id,status,notes)=>call('/api/admin/reconciliation/discrepancies/'+encodeURIComponent(id)+'/status',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,resolutionNotes:notes})}),
      exportCsvUrl:(id)=>base+'/api/admin/reconciliation/runs/'+encodeURIComponent(id)+'/export-csv'
    },
    leads:(page,size,status)=>call('/api/admin/leads?'+new URLSearchParams({page:String(page),size:String(size),...(status&&status!=='ALL'?{status}:{})})),
    updateLeadStatus:(leadId,status,notes)=>call('/api/admin/leads/'+encodeURIComponent(leadId)+'/status',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,notes})}),
    convertToOrganizer:(leadId)=>call('/api/admin/leads/'+encodeURIComponent(leadId)+'/convert-to-organizer',{method:'POST'}),
    payouts:(status)=>call('/api/admin/payouts'+(status&&status!=='ALL'?'?status='+encodeURIComponent(status):'')),
    processPayout:(payoutId,decision,adminNotes)=>call('/api/admin/payouts/'+encodeURIComponent(payoutId)+'/process',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({decision,adminNotes})
    }),
    outbox:(page,size)=>call('/api/admin/operations/outbox?'+new URLSearchParams({page:String(page),size:String(size)})),
    recovery:(page,size,status)=>call('/api/admin/operations/recovery?'+new URLSearchParams({page:String(page),size:String(size),status})),
    recoveryEvent:id=>call('/api/admin/operations/events/'+encodeURIComponent(id)),
    replayEvent:(id,request)=>call('/api/admin/operations/events/'+encodeURIComponent(id)+'/replay',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requestId:request.requestId,reason:request.reason})}),
    aiDiagnose:(data={})=>call('/api/ai/admin/diagnose',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
    scalpingAlerts:(status?:string,eventId?:string)=>call('/api/ai/admin/scalping-alerts?'+new URLSearchParams({
      ...(status&&status!=='ALL'?{status}:{}),
      ...(eventId?{eventId}:{})
    })),
    actionScalpingAlert:(id:string,action:'LOCK_ACCOUNT'|'DISMISS'|'ACTION_TAKEN',notes?:string)=>call('/api/ai/admin/scalping-alerts/'+encodeURIComponent(id)+'/action',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({action,notes})
    }),
    sellOutForecast:(eventId:string)=>call('/api/ai/admin/forecast/'+encodeURIComponent(eventId))
  };
}
