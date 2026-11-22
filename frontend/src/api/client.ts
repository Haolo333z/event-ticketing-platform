export type TicketType={id:string;name:string;mode:'GA'|'SEATED';priceVnd:number;active:boolean};
export type Event={id:string;title:string;description:string;venue:string;saleStartAt?:string;saleEndAt?:string;startsAt:string;status?:string;ticketTypes:TicketType[]};
export type Status='PENDING_PAYMENT'|'CONFIRMED'|'PAYMENT_FAILED'|'CANCELLED'|'EXPIRED'|'REFUND_REQUIRED';
export type Booking={bookingId:string;eventId?:string;status:Status;expiresAt:string;totalAmountVnd:number;history?:{fromStatus:Status|null;toStatus:Status;occurredAt:string}[]};
export type Scenario='SUCCESS'|'FAILURE'|'DELAY_TIMEOUT';
export type PaymentAttempt={bookingId:string;scenario:Scenario;requestId:string};
export type Ticket={id:string;bookingId:string;bookingItemId:string;ticketOrdinal:number;code:string;seatId:string|null;seatLabel:string|null};
export type TicketQrResponse={ticketId:string;ticketCode:string;eventId:string;qrToken:string;issuedAt:string};

export type CheckoutResult={
  attemptId:string;
  merchantRef:string;
  amountVnd:number;
  currency:string;
  gatewayUrl:string;
  expiresAt:string;
};

export type PaymentStatus={
  bookingId:string;
  status:Status;
  totalAmountVnd:number;
  provider?:string;
  merchantRef?:string;
  gatewayUrl?:string;
  expiresAt:string;
};

export type VnPayReturnResult={
  isValidSignature:boolean;
  isSuccess:boolean;
  providerTxRef:string;
  merchantRef:string;
  amountVnd:number;
  responseCode:string;
  message:string;
};
export type Command={eventId:string;ticketTypeId:string}&({quantity:number;seatIds?:never}|{seatIds:string[];quantity?:never});
export type Attempt={key:string;payload:Command};
export type Seat={id:string;label:string;status:'AVAILABLE'|'HELD'|'SOLD'};
export type Availability={eventId:string;asOf:string;ticketTypes:({ticketTypeId:string;mode:'GA';available:number}|{ticketTypeId:string;mode:'SEATED';seats:Seat[]})[]};

export type Role='CUSTOMER'|'STAFF'|'ORGANIZER'|'ADMIN';

export type User={
  id:string;
  displayName:string;
  email:string;
  phone?:string;
  role:Role|string;
  roles?:Role[];
};

export type OAuthDemoAccount={
  id:string;
  email:string;
  name:string;
  avatarUrl:string;
};

export type OAuthProviderConfig={
  provider:string;
  mode:'LIVE'|'DEMO';
  clientId:string;
  demoAccounts:OAuthDemoAccount[];
};

export type OAuthCallbackRequest={
  code?:string;
  idToken?:string;
  demoAccountId?:string;
};

export type SeatView={
  seatId:string;
  eventId:string;
  seatLabel:string;
  rowLabel:string;
  seatNumber:number;
  distanceMeters:number;
  elevationAngle:number;
  viewQualityScore:number;
  viewNotes:string;
  perspectiveSvg:string;
  status:'AVAILABLE'|'HELD'|'SOLD';
  priceVnd:number;
  ticketTypeName:string;
};

export type EventReminder={
  id:string;
  eventId:string;
  eventTitle:string;
  startsAt:string;
  remindBeforeMinutes:number;
  status:'PENDING'|'NOTIFIED'|'CANCELLED';
  createdAt:string;
};

export type RefundStep={
  stepNumber:number;
  title:string;
  status:'COMPLETED'|'IN_PROGRESS'|'PENDING'|'REJECTED';
  timestamp:string;
  description:string;
};

export type RefundProgress={
  bookingId:string;
  bookingStatus:string;
  amountVnd:number;
  reason:string;
  currentStage:string;
  steps:RefundStep[];
};

export type TransferItem={
  id:string;
  ticketId:string;
  ticketCode:string;
  eventTitle:string;
  toEmail:string;
  status:'PENDING'|'ACCEPTED'|'CANCELLED'|'EXPIRED';
  transferToken:string;
  note?:string;
  expiresAt:string;
  createdAt:string;
};

export type TransferDetail={
  id:string;
  ticketId:string;
  ticketCode:string;
  eventTitle:string;
  startsAt:string;
  seatLabel?:string;
  fromEmail:string;
  fromName:string;
  toEmail:string;
  note?:string;
  status:'PENDING'|'ACCEPTED'|'CANCELLED'|'EXPIRED';
  transferToken:string;
  expiresAt:string;
  createdAt:string;
};

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  phone: string;
  avatarUrl?: string;
  demoRole: string;
  roles: string[];
  googleLinked: boolean;
}

export interface ActiveSession {
  familyId: string;
  userAgent: string;
  ipAddress: string;
  createdAt: string;
  expiresAt: string;
  current: boolean;
}

export interface VatDefaults {
  companyName?: string;
  taxCode?: string;
  companyAddress?: string;
  recipientEmail?: string;
}

export interface ChangePasswordRequest {
  oldPassword?: string;
  newPassword: string;
}

export type AcceptTransferResult={
  transferId:string;
  oldTicketId:string;
  newTicketId:string;
  newTicketCode:string;
  status:string;
  message:string;
};

export type ResaleListingItem={
  id:string;
  ticketId:string;
  ticketCode:string;
  eventId:string;
  eventTitle:string;
  ticketTypeTitle:string;
  seatLabel?:string|null;
  sellerUserId:string;
  sellerName:string;
  originalPriceVnd:number;
  askingPriceVnd:number;
  status:'LISTED'|'RESERVED'|'SOLD'|'CANCELLED';
  lockedByUserId?:string|null;
  lockExpiresAt?:string|null;
  createdAt:string;
  soldAt?:string|null;
};

export type ReserveResaleResult={
  listingId:string;
  lockExpiresAt:string;
  status:string;
  message:string;
};

export type SettleResaleResult={
  listingId:string;
  newTicketId:string;
  newTicketCode:string;
  status:string;
  message:string;
};

export type OrganizerProfile={
  id:string;
  userId:string;
  organizationName:string;
  contactEmail:string;
  phone:string;
  taxCode?:string;
  status:string;
  createdAt:string;
  updatedAt:string;
};

export type Venue={
  id:string;
  name:string;
  address:string;
  city:string;
  country:string;
  capacity:number;
  seatingLayoutJson?:string;
};

export type EventCategory={
  id:string;
  name:string;
  description?:string;
  iconUrl?:string;
};

export type OrganizerEvent={
  id:string;
  title:string;
  description:string;
  venue:string;
  venueId?:string;
  categoryId?:string;
  organizerId?:string;
  saleStartAt:string;
  saleEndAt:string;
  startsAt:string;
  status:'DRAFT'|'PUBLISHED'|'CLOSED';
  ticketTypes:{
    id:string;
    name:string;
    mode:'GA'|'SEATED';
    priceVnd:number;
    active:boolean;
    capacity?:number;
    available?:number;
    seats?:{id:string;label:string;status:string}[];
  }[];
  totalCapacity:number;
  soldTickets:number;
  createdAt:string;
};

export type StaffAssignment={
  id:string;
  eventId:string;
  staffUserId:string;
  staffName:string;
  staffEmail:string;
  assignedBy:string;
  assignedAt:string;
  revokedAt?:string;
};

export type StaffCandidate={
  id:string;
  displayName:string;
  email:string;
  phone?:string;
};

export type AssignedEvent={
  eventId:string;
  title:string;
  venue:string;
  startsAt:string|null;
  endsAt:string|null;
  status:string;
  assignedAt:string|null;
};

export type CheckInRequest={
  qrToken?:string;
  ticketCode?:string;
  gateName?:string;
  deviceInfo?:string;
};

export type CheckInResult={
  status:string;
  ticketId:string;
  ticketCode:string;
  attendeeName:string;
  ticketType:string;
  seatLabel:string;
  checkedInAt:string;
  gate:string;
  message:string;
};

export type CheckInStats={
  totalSold:number;
  checkedInCount:number;
  remainingCount:number;
  checkInRatePercent:number;
};

export type CheckInHistoryItem={
  checkInId:string;
  ticketId:string;
  ticketCode:string;
  attendeeName:string;
  ticketType:string;
  seatLabel:string;
  gateName:string;
  checkedInAt:string;
  staffName:string;
};

export type BestSeatPick={id:string;label:string;rowLabel:string;seatNumber:number};
export type BestSeatsResult={
  eventId:string;
  ticketTypeId:string;
  quantity:number;
  contiguous:boolean;
  rowLabel:string;
  centerDistance:number;
  seats:BestSeatPick[];
  explanation:string;
};

export type EventV3Item=Event&{
  categoryId?:string;
  eventFormat:'OFFLINE_SEATED'|'OFFLINE_GA'|'ONLINE';
  onlineJoinUrl?:string;
  minPriceVnd:number;
  maxPriceVnd:number;
};

export type WaitlistEntry={
  id:string;
  eventId:string;
  eventTitle:string;
  ticketTypeId?:string|null;
  desiredQuantity:number;
  status:'WAITING'|'NOTIFIED'|'CONVERTED'|'CANCELLED';
  queuePosition:number;
  notifiedAt?:string|null;
  createdAt:string;
};

export type WaitingRoomStatus={
  eventId:string;
  sessionId:string;
  status:'ADMITTED'|'QUEUED';
  position:number;
  estimatedWaitSeconds:number;
  activeUsers:number;
  maxActiveUsers:number;
  queueLength:number;
  admissionToken?:string|null;
};

export type AiSeatSuggestion={
  eventId:string;
  eventTitle:string;
  ticketTypeId:string;
  ticketTypeName:string;
  mode:'GA'|'SEATED';
  unitPriceVnd:number;
  totalPriceVnd:number;
  contiguous:boolean;
  seatIds:string[];
  seatLabels:string[];
  quantity:number;
  reason:string;
};

export type AiConciergeResponse={
  conversationId:string;
  reply:string;
  adapterUsed:string;
  suggestions:AiSeatSuggestion[];
  bookingSummaries:Record<string,unknown>[];
};

export type AiOrganizerResponse={
  adapterUsed:string;
  suggestedTitle:string;
  seoDescription:string;
  agenda:string[];
  ticketTiers:{name:string;mode:string;priceVnd:number;capacity:number}[];
  seatMatrixConfig:{rows:string[];seatsPerRow:number;totalGeneratedSeats:number};
};

export type AiAdminDiagnoseResponse={
  adapterUsed:string;
  severity:'HIGH'|'MEDIUM'|'LOW';
  summaryVi:string;
  rootCause:string;
  recommendedAction:string;
  safeToReplay:boolean;
  evidence:Record<string,unknown>;
};

export interface VoucherValidationResult {
  valid: boolean;
  voucherId?: string;
  code: string;
  discountType: string;
  discountValue: number;
  discountAmountVnd: number;
  finalAmountVnd: number;
  message: string;
}

export interface ApplyVoucherResponse {
  bookingId: string;
  voucherId: string;
  code: string;
  originalAmountVnd: number;
  discountAmountVnd: number;
  finalAmountVnd: number;
  message: string;
}

export interface VoucherItem {
  id: string;
  organizerId?: string;
  eventId?: string;
  ticketTypeId?: string;
  code: string;
  discountType: string;
  discountValue: number;
  minOrderAmountVnd: number;
  maxDiscountAmountVnd?: number;
  maxUses: number;
  usedCount: number;
  validFrom: string;
  validTo: string;
  isActive: boolean;
  createdAt: string;
}

export interface CreateVoucherRequest {
  eventId?: string;
  ticketTypeId?: string;
  code: string;
  discountType: 'PERCENTAGE' | 'FIXED_VND';
  discountValue: number;
  minOrderAmountVnd: number;
  maxDiscountAmountVnd?: number;
  maxUses: number;
  validFrom: string;
  validTo: string;
}

export interface EInvoiceItem {
  id: string;
  bookingId: string;
  accountId: string;
  taxCode: string;
  companyName: string;
  companyAddress: string;
  recipientEmail: string;
  invoiceLookupCode: string;
  totalAmountVnd: number;
  vatRate: number;
  vatAmountVnd: number;
  status: string;
  issuedAt: string;
}

export interface PayoutSummary {
  eventId: string;
  eventTitle: string;
  saasPlan: string;
  platformFeePercent: number;
  grossRevenueVnd: number;
  totalTicketsSold: number;
  platformFeeVnd: number;
  netPayoutTotalVnd: number;
  totalPayoutsRequestedVnd: number;
  availableBalanceVnd: number;
}

export interface OrganizerPayoutItem {
  id: string;
  organizerId: string;
  organizationName: string;
  eventId: string;
  eventTitle: string;
  grossRevenueVnd: number;
  platformFeePercent: number;
  platformFeeVnd: number;
  netPayoutVnd: number;
  bankName: string;
  bankAccountNumber: string;
  bankAccountHolder: string;
  status: 'REQUESTED' | 'APPROVED' | 'PAID' | 'REJECTED';
  requestedAt: string;
  processedAt?: string;
  adminNotes?: string;
}

export interface OfflineTicketItem {
  ticketId: string;
  ticketCode: string;
  attendeeName: string;
  ticketType: string;
  seatLabel: string;
  qrToken: string;
  checkedIn: boolean;
  checkedInAt: string | null;
}

export interface OfflineManifest {
  eventId: string;
  title: string;
  asOf: string;
  totalTickets: number;
  tickets: OfflineTicketItem[];
}

export interface OfflineSyncItem {
  clientSyncId: string;
  ticketId?: string;
  ticketCode?: string;
  qrToken?: string;
  gateName?: string;
  clientTimestamp?: string;
  deviceInfo?: string;
}

export interface OfflineSyncResultItem {
  clientSyncId: string;
  ticketId: string | null;
  ticketCode: string;
  status: string;
  gateName: string;
  checkedInAt: string | null;
  message: string;
}

export interface BatchSyncResponse {
  eventId: string;
  syncedAt: string;
  total: number;
  syncedCount: number;
  conflictCount: number;
  results: OfflineSyncResultItem[];
}

export interface AntiBotChallenge {
  challengeToken: string;
  question: string;
  expiresInSeconds: number;
}

export interface AntiBotVerifyResponse {
  verified: boolean;
  admissionToken: string | null;
  message: string;
  expiresInSeconds: number;
}

export type AuthResponse={
  token:string;
  user:User;
};

export const AUTH_TOKEN_KEY='eventflow_token';
export const AUTH_USER_KEY='eventflow_user';

export function getStoredToken():string|null{
  try{
    if(typeof localStorage!=='undefined')return localStorage.getItem(AUTH_TOKEN_KEY);
  }catch{/* ignore */}
  return null;
}

export function getStoredUser():User|null{
  try{
    if(typeof localStorage!=='undefined'){
      const raw=localStorage.getItem(AUTH_USER_KEY);
      return raw?JSON.parse(raw):null;
    }
  }catch{/* ignore */}
  return null;
}

export function setStoredAuth(token:string,user:User):void{
  try{
    if(typeof localStorage!=='undefined'){
      localStorage.setItem(AUTH_TOKEN_KEY,token);
      localStorage.setItem(AUTH_USER_KEY,JSON.stringify(user));
    }
  }catch{/* ignore */}
}

export function clearStoredAuth():void{
  try{
    if(typeof localStorage!=='undefined'){
      localStorage.removeItem(AUTH_TOKEN_KEY);
      localStorage.removeItem(AUTH_USER_KEY);
    }
    if(typeof window!=='undefined'){
      window.dispatchEvent(new CustomEvent('eventflow:auth-cleared'));
    }
  }catch{/* ignore */}
}

export class ApiError extends Error{
  code:string;status:number;correlationId?:string;
  constructor(code:string,message:string,status=0,correlationId?:string){super(message);this.name='ApiError';this.code=code;this.status=status;this.correlationId=correlationId;}
}

export interface OnboardingStatusResponse {
  hasSubmitted: boolean;
  status?: string;
  statusDisplay?: string;
  organizationName?: string;
  contactEmail?: string;
  saasPlan?: string;
  notes?: string;
  submittedAt?: string;
}

export interface Api{
  events():Promise<{items:Event[]}>;detail(id:string):Promise<Event>;availability(id:string):Promise<Availability>;
  create(attempt:Attempt):Promise<Booking>;booking(id:string):Promise<Booking>;mine():Promise<{items:Booking[]}>;
  simulate(attempt:PaymentAttempt):Promise<{paymentIntentId:string;status:'ACCEPTED'}>;cancel(id:string):Promise<Booking>;tickets(id:string):Promise<Ticket[]>;
  ticketQr?(id:string):Promise<TicketQrResponse>;
  payments?:{
    checkout(data:{bookingId:string;provider?:string;returnUrl?:string}):Promise<CheckoutResult>;
    status(bookingId:string):Promise<PaymentStatus>;
    vnpayReturn(params:Record<string,string>):Promise<VnPayReturnResult>;
  };
  auth?:{
    register(data:{displayName:string;email:string;password:string;phone?:string}):Promise<AuthResponse>;
    login(data:{email:string;password:string}):Promise<AuthResponse>;
    refresh():Promise<AuthResponse>;
    logout():Promise<{success:boolean;message?:string}>;
    me():Promise<User>;
  };
  oauth?:{
    getGoogleConfig():Promise<OAuthProviderConfig>;
    googleCallback(data:OAuthCallbackRequest):Promise<AuthResponse>;
  };
  contact?:{
    submitLead(data:{fullName:string;email:string;phone:string;companyName?:string;eventScale?:string;message?:string}):Promise<{id:string;message:string}>;
  };
  organizer?:{
    getProfile():Promise<OrganizerProfile|null>;
    saveProfile(data:{organizationName:string;contactEmail:string;phone:string;taxCode?:string}):Promise<OrganizerProfile>;
    venues():Promise<Venue[]>;
    categories():Promise<EventCategory[]>;
    events():Promise<OrganizerEvent[]>;
    event(id:string):Promise<OrganizerEvent>;
    createEvent(data:{title:string;description?:string;venue?:string;venueId?:string;categoryId?:string;saleStartAt:string;saleEndAt:string;startsAt:string}):Promise<OrganizerEvent>;
    updateEvent(id:string,data:{title:string;description?:string;venue?:string;venueId?:string;categoryId?:string;saleStartAt:string;saleEndAt:string;startsAt:string}):Promise<OrganizerEvent>;
    publishEvent(id:string):Promise<OrganizerEvent>;
    addTicketType(id:string,data:{name:string;mode:'GA'|'SEATED';priceVnd:number;capacity?:number;seatLabels?:string[]}):Promise<OrganizerEvent>;
    staff(id:string):Promise<StaffAssignment[]>;
    assignStaff(id:string,data:{staffUserId?:string;staffEmail?:string}):Promise<StaffAssignment>;
    revokeStaff(id:string,staffUserId:string):Promise<void>;
    staffCandidates(q?:string):Promise<StaffCandidate[]>;
  };
  staff?:{
    events():Promise<AssignedEvent[]>;
    checkIn(eventId:string,data:CheckInRequest):Promise<CheckInResult>;
    stats(eventId:string):Promise<CheckInStats>;
    history(eventId:string,limit?:number):Promise<CheckInHistoryItem[]>;
    getOfflineManifest(eventId:string):Promise<OfflineManifest>;
    batchSync(data:{eventId:string;items:OfflineSyncItem[]}):Promise<BatchSyncResponse>;
  };
  v3?:{
    discoverEvents(params?:{q?:string;format?:string;categoryId?:string;minPrice?:number;maxPrice?:number}):Promise<{items:EventV3Item[];totalElements:number}>;
    bestSeats(eventId:string,ticketTypeId:string,quantity:number):Promise<BestSeatsResult>;
    streamUrl(eventId:string):string;
    calendarUrl(eventId:string):string;
    updateEventFormat(eventId:string,data:{eventFormat:'OFFLINE_SEATED'|'OFFLINE_GA'|'ONLINE';onlineJoinUrl?:string}):Promise<{eventId:string;eventFormat:string;onlineJoinUrl:string}>;
    generateSeatMatrix(eventId:string,data:{ticketTypeId:string;rows:string[];seatsPerRow:number}):Promise<{eventId:string;ticketTypeId:string;createdCount:number;sampleLabels:string[]}>;
    joinWaitlist(eventId:string,data?:{ticketTypeId?:string;desiredQuantity?:number}):Promise<WaitlistEntry>;
    cancelWaitlist(eventId:string):Promise<{eventId:string;cancelled:boolean}>;
    myWaitlists():Promise<{items:WaitlistEntry[]}>;
    enterWaitingRoom(eventId:string,sessionId?:string):Promise<WaitingRoomStatus>;
    waitingRoomStatus(eventId:string,sessionId:string):Promise<WaitingRoomStatus>;
  };
  ai?:{
    status():Promise<{activeAdapter:string;fallbackAvailable:boolean;capabilities:string[]}>;
    conciergeChat(data:{conversationId?:string;message:string}):Promise<AiConciergeResponse>;
    organizerGenerate(data:{keywords:string;category?:string;targetCapacity?:number;basePriceVnd?:number}):Promise<AiOrganizerResponse>;
    adminDiagnose(data?:{bookingId?:string;failureId?:string}):Promise<AiAdminDiagnoseResponse>;
  };
  vouchers?: {
    validate(data: { code: string; eventId?: string; ticketTypeId?: string; orderAmountVnd: number }): Promise<VoucherValidationResult>;
    apply(bookingId: string, code: string): Promise<ApplyVoucherResponse>;
    list(): Promise<VoucherItem[]>;
    create(data: CreateVoucherRequest): Promise<VoucherItem>;
  };
  invoices?: {
    issue(bookingId: string, data: { taxCode: string; companyName: string; companyAddress: string; recipientEmail: string }): Promise<EInvoiceItem>;
    get(bookingId: string): Promise<EInvoiceItem>;
    lookup(lookupCode: string): Promise<{ found: boolean; invoice?: EInvoiceItem; message: string }>;
  };
  payouts?: {
    summary(eventId: string): Promise<PayoutSummary>;
    request(data: { eventId: string; amountVnd: number; bankName: string; bankAccountNumber: string; bankAccountHolder: string }): Promise<OrganizerPayoutItem>;
    list(): Promise<OrganizerPayoutItem[]>;
  };
  onboard?: {
    status(): Promise<OnboardingStatusResponse>;
    submit(data: { organizationName: string; contactEmail?: string; phone: string; taxCode?: string; saasPlan?: string; message?: string }): Promise<{ leadId: string; message: string }>;
  };
  antiBot?: {
    getChallenge(eventId?: string): Promise<AntiBotChallenge>;
    verify(challengeToken: string, solution: string): Promise<AntiBotVerifyResponse>;
    getStatus(eventId: string): Promise<{ eventId: string; antiBotRequired: boolean }>;
  };
  seats?: {
    getView(eventId: string, seatId: string): Promise<SeatView>;
  };
  reminders?: {
    register(eventId: string, minutes?: number): Promise<{ id: string; eventId: string; remindBeforeMinutes: number; status: string; message: string }>;
    list(): Promise<{ items: EventReminder[]; total: number }>;
    cancel(eventId: string): Promise<{ success: boolean; message: string }>;
  };
  refunds?: {
    getProgress(bookingId: string): Promise<RefundProgress>;
  };
  transfers?: {
    initiate(data: { ticketId: string; toEmail: string; note?: string }): Promise<TransferItem>;
    lookup(token: string): Promise<TransferDetail>;
    accept(token: string): Promise<AcceptTransferResult>;
    cancel(id: string): Promise<{ success: boolean; message: string }>;
    sent(): Promise<{ items: TransferItem[]; total: number }>;
    received(): Promise<{ items: TransferItem[]; total: number }>;
  };
  profile?: {
    get(): Promise<UserProfile>;
    update(data: { displayName: string; phone?: string; avatarUrl?: string }): Promise<UserProfile>;
    changePassword(data: ChangePasswordRequest): Promise<{ success: boolean; message: string }>;
    vatDefaults(): Promise<VatDefaults>;
    updateVatDefaults(data: VatDefaults): Promise<VatDefaults>;
    sessions(): Promise<{ sessions: ActiveSession[]; total: number }>;
    revokeSession(familyId: string): Promise<{ success: boolean; message: string }>;
    revokeOtherSessions(): Promise<{ success: boolean; message: string }>;
  };
  resale?: {
    create(data: { ticketId: string; askingPriceVnd: number }): Promise<ResaleListingItem>;
    listForEvent(eventId: string): Promise<{ items: ResaleListingItem[]; total: number }>;
    reserve(listingId: string): Promise<ReserveResaleResult>;
    settle(listingId: string): Promise<SettleResaleResult>;
    cancel(listingId: string): Promise<{ success: boolean; message: string }>;
    myListings(): Promise<{ items: ResaleListingItem[]; total: number }>;
  };
}

let activeRefreshPromise:Promise<string|null>|null=null;

export function demoTransport(base:string,fetcher:typeof fetch,token:string|null|(()=>string|null)){
  async function doRefresh():Promise<string|null>{
    if(activeRefreshPromise){
      return activeRefreshPromise;
    }
    activeRefreshPromise=(async()=>{
      try{
        const res=await fetcher(base+'/api/auth/refresh',{
          method:'POST',
          credentials:'include',
          headers:{'Content-Type':'application/json'}
        });
        if(!res.ok){
          clearStoredAuth();
          return null;
        }
        const data=await res.json() as AuthResponse;
        if(data&&data.token&&data.user){
          setStoredAuth(data.token,data.user);
          return data.token;
        }
        clearStoredAuth();
        return null;
      }catch{
        return null;
      }finally{
        activeRefreshPromise=null;
      }
    })();
    return activeRefreshPromise;
  }

  function isPersonalEndpoint(path:string):boolean{
    return path.startsWith('/api/me') || path.startsWith('/api/bookings') || path.startsWith('/api/tickets');
  }

  async function call<T>(path:string,init:RequestInit={}):Promise<T>{
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);
    const resolvedToken=typeof token==='function'?token():token;
    try{
      const headers:Record<string,string>={
        ...(init.headers as Record<string,string>|undefined)
      };
      if(resolvedToken && !headers['Authorization'] && !headers['authorization']){
        headers['Authorization']='Bearer '+resolvedToken;
      }
      let response=await fetcher(base+path,{...init,signal:controller.signal,headers});

      // 401 interceptor with token family refresh
      if(response.status===401&&!path.startsWith('/api/auth/login')&&!path.startsWith('/api/auth/register')&&!path.startsWith('/api/auth/refresh')){
        const refreshedToken=await doRefresh();
        if(refreshedToken){
          const retryHeaders:Record<string,string>={
            ...(init.headers as Record<string,string>|undefined),
            'Authorization':'Bearer '+refreshedToken
          };
          response=await fetcher(base+path,{...init,signal:controller.signal,headers:retryHeaders});
        }else if(!isPersonalEndpoint(path)){
          // Stored token was expired or invalid and refresh failed (stored auth was cleared).
          // Fallback retry using current tokenProvider() or clean anonymous headers for public endpoints only.
          const fallbackToken=typeof token==='function'?token():null;
          const retryHeaders:Record<string,string>={
            ...(init.headers as Record<string,string>|undefined)
          };
          if(fallbackToken&&fallbackToken!==resolvedToken){
            retryHeaders['Authorization']='Bearer '+fallbackToken;
          }else{
            delete retryHeaders['Authorization'];
            delete retryHeaders['authorization'];
          }
          const retryResponse=await fetcher(base+path,{...init,signal:controller.signal,headers:retryHeaders});
          if(retryResponse.ok||retryResponse.status!==401){
            response=retryResponse;
          }
        }
      }

      let body:Record<string,unknown>;
      try{body=await response.json();}catch{throw new ApiError('NETWORK_ERROR','Chưa xác định được kết quả. Hãy thử lại cùng yêu cầu.');}
      if(!response.ok)throw new ApiError(String(body.code??'INTERNAL_ERROR'),String(body.message??'Không thể hoàn tất yêu cầu'),response.status,body.correlationId as string|undefined);
      return body as T;
    }catch(error){if(error instanceof ApiError)throw error;throw new ApiError('NETWORK_ERROR','Không nhận được phản hồi. Hãy thử lại cùng yêu cầu.');}
    finally{clearTimeout(timer);}
  }
  return call;
}

export function createApi(base='http://127.0.0.1:8080',fetcher:typeof fetch=globalThis.fetch):Api{
  const tokenProvider=()=>{
    const stored=getStoredToken();
    if(stored)return stored;
    if(typeof window!=='undefined')return null;
    return 'demo-customer-token';
  };
  const call=demoTransport(base,fetcher,tokenProvider);
  return{
    events:()=>call('/api/events?size=100'),
    detail:id=>call('/api/events/'+encodeURIComponent(id)),
    availability:id=>call('/api/events/'+encodeURIComponent(id)+'/availability'),
    create:attempt=>call('/api/bookings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':attempt.key},body:JSON.stringify(attempt.payload)}),
    booking:id=>call('/api/bookings/'+encodeURIComponent(id)),
    mine:()=>call('/api/me/bookings?size=100'),
    simulate:attempt=>call('/api/bookings/'+encodeURIComponent(attempt.bookingId)+'/simulate-payment',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({scenario:attempt.scenario,requestId:attempt.requestId})}),
    cancel:id=>call('/api/bookings/'+encodeURIComponent(id)+'/cancel',{method:'POST'}),
    tickets:id=>call('/api/bookings/'+encodeURIComponent(id)+'/tickets'),
    ticketQr:id=>call('/api/tickets/'+encodeURIComponent(id)+'/qr'),
    auth:{
      register:(data)=>call('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      login:(data)=>call('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      refresh:()=>call('/api/auth/refresh',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'}}),
      logout:()=>call('/api/auth/logout',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'}}),
      me:()=>call('/api/auth/me')
    },
    oauth:{
      getGoogleConfig:()=>call('/api/auth/oauth/google/config'),
      googleCallback:(data)=>call('/api/auth/oauth/google/callback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})
    },
    contact:{
      submitLead:(data)=>call('/api/contact/leads',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})
    },
    organizer:{
      getProfile:()=>call('/api/organizer/profile'),
      saveProfile:(data)=>call('/api/organizer/profile',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      venues:()=>call('/api/organizer/venues'),
      categories:()=>call('/api/organizer/categories'),
      events:()=>call('/api/organizer/events'),
      event:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)),
      createEvent:(data)=>call('/api/organizer/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      updateEvent:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id),{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      publishEvent:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/publish',{method:'POST'}),
      addTicketType:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/ticket-types',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      staff:(id)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff'),
      assignStaff:(id,data)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      revokeStaff:(id,staffUserId)=>call('/api/organizer/events/'+encodeURIComponent(id)+'/staff/'+encodeURIComponent(staffUserId),{method:'DELETE'}),
      staffCandidates:(q)=>call('/api/organizer/staff-candidates'+(q?'?q='+encodeURIComponent(q):''))
    },
    payments:{
      checkout:(data)=>call('/api/payments/checkout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}),
      status:(id)=>call('/api/bookings/'+encodeURIComponent(id)+'/payment-status'),
      vnpayReturn:(params)=>{
        const qs=new URLSearchParams(params).toString();
        return call('/api/payments/vnpay/return?'+qs);
      }
    },
    staff:{
      events:()=>call('/api/staff/events'),
      checkIn:(eventId,data)=>call('/api/staff/events/'+encodeURIComponent(eventId)+'/check-in',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      stats:(eventId)=>call('/api/staff/events/'+encodeURIComponent(eventId)+'/stats'),
      history:(eventId,limit=50)=>call('/api/staff/events/'+encodeURIComponent(eventId)+'/history?limit='+limit),
      getOfflineManifest:(eventId)=>call('/api/staff/events/'+encodeURIComponent(eventId)+'/offline-manifest'),
      batchSync:(data)=>call('/api/staff/check-ins/batch-sync',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      })
    },
    v3:{
      discoverEvents:(params={})=>{
        const sp=new URLSearchParams();
        if(params.q)sp.set('q',params.q);
        if(params.format)sp.set('format',params.format);
        if(params.categoryId)sp.set('categoryId',params.categoryId);
        if(params.minPrice!=null)sp.set('minPrice',String(params.minPrice));
        if(params.maxPrice!=null)sp.set('maxPrice',String(params.maxPrice));
        const qs=sp.toString();
        return call('/api/v3/events'+(qs?'?'+qs:''));
      },
      bestSeats:(eventId,ticketTypeId,quantity)=>call('/api/events/'+encodeURIComponent(eventId)+'/seats/best-available?ticketTypeId='+encodeURIComponent(ticketTypeId)+'&quantity='+quantity),
      streamUrl:(eventId)=>base+'/api/events/'+encodeURIComponent(eventId)+'/stream',
      calendarUrl:(eventId)=>base+'/api/events/'+encodeURIComponent(eventId)+'/calendar.ics',
      updateEventFormat:(eventId,data)=>call('/api/organizer/events/'+encodeURIComponent(eventId)+'/format',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      generateSeatMatrix:(eventId,data)=>call('/api/organizer/events/'+encodeURIComponent(eventId)+'/seat-matrix',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      joinWaitlist:(eventId,data={})=>call('/api/events/'+encodeURIComponent(eventId)+'/waitlist',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      cancelWaitlist:(eventId)=>call('/api/events/'+encodeURIComponent(eventId)+'/waitlist/cancel',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({})
      }),
      myWaitlists:()=>call('/api/me/waitlists'),
      enterWaitingRoom:(eventId,sessionId)=>call('/api/events/'+encodeURIComponent(eventId)+'/waiting-room/enter',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(sessionId?{sessionId}:{})
      }),
      waitingRoomStatus:(eventId,sessionId)=>call('/api/events/'+encodeURIComponent(eventId)+'/waiting-room/status?sessionId='+encodeURIComponent(sessionId))
    },
    ai:{
      status:()=>call('/api/ai/status'),
      conciergeChat:(data)=>call('/api/ai/concierge/chat',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      organizerGenerate:(data)=>call('/api/ai/organizer/generate-event',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      }),
      adminDiagnose:(data={})=>call('/api/ai/admin/diagnose',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(data)
      })
    },
    vouchers: {
      validate: (data) => call('/api/vouchers/validate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      apply: (bookingId, code) => call('/api/bookings/' + encodeURIComponent(bookingId) + '/apply-voucher', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code })
      }),
      list: () => call('/api/organizer/vouchers'),
      create: (data) => call('/api/organizer/vouchers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      })
    },
    invoices: {
      issue: (bookingId, data) => call('/api/me/bookings/' + encodeURIComponent(bookingId) + '/e-invoice', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      get: (bookingId) => call('/api/me/bookings/' + encodeURIComponent(bookingId) + '/e-invoice'),
      lookup: (lookupCode) => call('/api/e-invoices/lookup/' + encodeURIComponent(lookupCode))
    },
    payouts: {
      summary: (eventId) => call('/api/organizer/payouts/summary?eventId=' + encodeURIComponent(eventId)),
      request: (data) => call('/api/organizer/payouts/request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      list: () => call('/api/organizer/payouts')
    },
    onboard: {
      status: () => call('/api/organizer/onboard/status'),
      submit: (data) => call('/api/organizer/onboard', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      })
    },
    antiBot: {
      getChallenge: (eventId) => call('/api/security/anti-bot/challenge', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(eventId ? { eventId } : {})
      }),
      verify: (challengeToken, solution) => call('/api/security/anti-bot/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeToken, solution })
      }),
      getStatus: (eventId) => call('/api/security/anti-bot/events/' + encodeURIComponent(eventId) + '/status')
    },
    seats: {
      getView: (eventId, seatId) => call('/api/events/' + encodeURIComponent(eventId) + '/seats/' + encodeURIComponent(seatId) + '/view')
    },
    reminders: {
      register: (eventId, minutes = 15) => call('/api/events/' + encodeURIComponent(eventId) + '/reminders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ remindBeforeMinutes: minutes })
      }),
      list: () => call('/api/me/reminders'),
      cancel: (eventId) => call('/api/events/' + encodeURIComponent(eventId) + '/reminders', { method: 'DELETE' })
    },
    refunds: {
      getProgress: (bookingId) => call('/api/me/bookings/' + encodeURIComponent(bookingId) + '/refund-progress')
    },
    transfers: {
      initiate: (data) => call('/api/transfers/initiate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      lookup: (token) => call('/api/transfers/lookup/' + encodeURIComponent(token)),
      accept: (token) => call('/api/transfers/accept/' + encodeURIComponent(token), { method: 'POST' }),
      cancel: (id) => call('/api/transfers/' + encodeURIComponent(id) + '/cancel', { method: 'POST' }),
      sent: () => call('/api/me/transfers/sent'),
      received: () => call('/api/me/transfers/received')
    },
    profile: {
      get: () => call('/api/me/profile'),
      update: (data) => call('/api/me/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      changePassword: (data) => call('/api/me/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      vatDefaults: () => call('/api/me/vat-defaults'),
      updateVatDefaults: (data) => call('/api/me/vat-defaults', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      sessions: () => call('/api/me/sessions', { credentials: 'include' }),
      revokeSession: (familyId) => call('/api/me/sessions/' + encodeURIComponent(familyId), { method: 'DELETE' }),
      revokeOtherSessions: () => call('/api/me/sessions/revoke-others', { method: 'DELETE', credentials: 'include' })
    },
    resale: {
      create: (data) => call('/api/resale/listings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      }),
      listForEvent: (eventId) => call('/api/events/' + encodeURIComponent(eventId) + '/resale-listings'),
      reserve: (listingId) => call('/api/resale/listings/' + encodeURIComponent(listingId) + '/reserve', { method: 'POST' }),
      settle: (listingId) => call('/api/resale/listings/' + encodeURIComponent(listingId) + '/settle', { method: 'POST' }),
      cancel: (listingId) => call('/api/resale/listings/' + encodeURIComponent(listingId) + '/cancel', { method: 'POST' }),
      myListings: () => call('/api/me/resale/listings')
    }
  };
}

const STORAGE_KEY='eventflow.pending-ga.v1';
export type Store=Pick<Storage,'getItem'|'setItem'|'removeItem'>;
export function readAttempt(store:Store):Attempt|null{
  try{
    const value=JSON.parse(store.getItem(STORAGE_KEY)??'null');const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if(value&&uuid.test(value.key)&&uuid.test(value.payload?.eventId)&&uuid.test(value.payload?.ticketTypeId)){
      const p=value.payload;
      if(!('seatIds' in p)&&Number.isInteger(p.quantity)&&p.quantity>=1&&p.quantity<=6)return{key:value.key,payload:{eventId:p.eventId,ticketTypeId:p.ticketTypeId,quantity:p.quantity}};
      if(!('quantity' in p)&&Array.isArray(p.seatIds)&&p.seatIds.length>=1&&p.seatIds.length<=6&&p.seatIds.every((id:unknown)=>typeof id==='string'&&uuid.test(id))&&new Set(p.seatIds.map((id:string)=>id.toLowerCase())).size===p.seatIds.length)
        return{key:value.key,payload:{eventId:p.eventId,ticketTypeId:p.ticketTypeId,seatIds:[...p.seatIds]}};
    }
  }catch{/* Never automatically POST invalid or unavailable session data. */}
  return null;
}
export function saveAttempt(store:Store,value:Attempt|null){if(value)store.setItem(STORAGE_KEY,JSON.stringify(value));else store.removeItem(STORAGE_KEY);}
export function unknownOutcome(error:ApiError){return error.status===0||error.status>=500||error.code==='IDEMPOTENCY_KEY_REUSED';}

const PAYMENT_KEY='eventflow.pending-payment.v1';
export function readPaymentAttempt(storage:Store):PaymentAttempt|null{
  try{
    const value=JSON.parse(storage.getItem(PAYMENT_KEY)??'null');const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if(value&&uuid.test(value.bookingId)&&uuid.test(value.requestId)&&['SUCCESS','FAILURE','DELAY_TIMEOUT'].includes(value.scenario))
      return{bookingId:value.bookingId,requestId:value.requestId,scenario:value.scenario};
  }catch{/* Ignore malformed session state. */}
  return null;
}
export function savePaymentAttempt(storage:Store,value:PaymentAttempt|null){if(value)storage.setItem(PAYMENT_KEY,JSON.stringify(value));else storage.removeItem(PAYMENT_KEY);}
