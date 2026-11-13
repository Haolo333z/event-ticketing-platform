import {createElement as h,useEffect,useRef,useState} from 'react';
import type {ChangeEvent,FormEvent} from 'react';
import {ApiError} from '../api/client.ts';
import type {Store} from '../api/client.ts';
import type {AdminApi,Page,RecoveryItem,RecoveryEvent,RecoveryFilter,ReplayAccepted} from '../api/admin.ts';
import {readReplayAttempt,saveReplayAttempt,replayStore} from './replayAttempt.ts';
import type {ReplayAttempt} from './replayAttempt.ts';

const errorText=(e:unknown)=>e instanceof ApiError?e.message+' · '+e.code+(e.correlationId?' · Mã tra cứu: '+e.correlationId:''):'Không nhận được phản hồi. Kiểm tra audit trước khi thử lại.';
export function RecoveryView({api,initialEventId,revision=0,store=replayStore()}:{api:AdminApi;initialEventId?:string;revision?:number;store?:Store|null}){
  const[attempt,setAttempt]=useState<ReplayAttempt|null>(()=>readReplayAttempt(store));
  const[selected,setSelected]=useState<string|null>(()=>readReplayAttempt(store)?.eventId??initialEventId??null);
  const[filter,setFilter]=useState<RecoveryFilter>('OPEN'),[page,setPage]=useState(0),[generation,setGeneration]=useState(0);
  const[list,setList]=useState<Page<RecoveryItem>|null>(null),[loading,setLoading]=useState(true),[listError,setListError]=useState('');
  const[detail,setDetail]=useState<RecoveryEvent|null>(null),[detailError,setDetailError]=useState(''),[detailLoading,setDetailLoading]=useState(false);
  const[reason,setReason]=useState(()=>readReplayAttempt(store)?.reason??''),[error,setError]=useState(''),[receipt,setReceipt]=useState<ReplayAccepted|null>(null),[busy,setBusy]=useState(false);
  const sending=useRef(false),panel=useRef<HTMLElement>(null),mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
  useEffect(()=>{if(initialEventId&&!attempt)setSelected(initialEventId);},[initialEventId]);
  useEffect(()=>{
    let active=true;setLoading(true);setListError('');setList(null);
    api.recovery(page,20,filter).then(value=>{if(active)setList(value);}).catch(e=>{if(active)setListError(errorText(e));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[api,page,filter,generation,revision]);
  useEffect(()=>{
    let active=true;setDetail(null);setDetailError('');if(!selected){setDetailLoading(false);return;}setDetailLoading(true);
    api.recoveryEvent(selected).then(value=>{if(active)setDetail(value);}).catch(e=>{if(active)setDetailError(errorText(e));}).finally(()=>{if(active)setDetailLoading(false);});
    return()=>{active=false;};
  },[api,selected,generation,revision]);
  useEffect(()=>{if(detail){panel.current?.scrollIntoView?.({behavior:'smooth',block:'start'});(panel.current?.querySelector<HTMLTextAreaElement>('#recovery-reason:not([disabled])')??panel.current)?.focus({preventScroll:true});}},[selected,detail?.eventId]);
  function choose(id:string){if(attempt||sending.current)return;setSelected(id);setReason('');setError('');setReceipt(null);}
  async function send(command:ReplayAttempt){
    if(sending.current)return;sending.current=true;setBusy(true);setError('');setReceipt(null);setAttempt(command);saveReplayAttempt(store,command);
    try{
      const result=await api.replayEvent(command.eventId,command);saveReplayAttempt(store,null);
      if(mounted.current){setReceipt(result);setAttempt(null);setReason('');setGeneration(v=>v+1);}
    }catch(e){
      if(mounted.current){setError(errorText(e));
        if(e instanceof ApiError&&e.status>0&&e.status<500){setAttempt(null);saveReplayAttempt(store,null);setGeneration(v=>v+1);}
      }
    }finally{sending.current=false;if(mounted.current)setBusy(false);}
  }
  function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(!detail||attempt||detail.remainingReplays===0||sending.current)return;
    if(!reason.trim()||Array.from(reason).length>500){setError('Lý do phải có 1–500 ký tự Unicode, không chỉ khoảng trắng.');return;}
    void send({eventId:detail.eventId,requestId:crypto.randomUUID(),reason});
  }
  const known=attempt&&detail?.audit.some(a=>a.requestId===attempt.requestId&&a.reason===attempt.reason);
  return h('section',{'aria-label':'Khôi phục sự kiện'},
    h('p',null,'Chỉ ADMIN local/test. Replay giữ nguyên event và inbox; tối đa 3 yêu cầu khác nhau/event. Không sửa raw record hoặc bỏ qua giới hạn.'),
    h('label',{htmlFor:'recovery-filter'},'Lọc recovery'),h('select',{id:'recovery-filter',value:filter,onChange:(e:ChangeEvent<HTMLSelectElement>)=>{setFilter(e.target.value as RecoveryFilter);setPage(0);}},h('option',{value:'OPEN'},'Cần xử lý'),h('option',{value:'RESOLVED'},'Đã phục hồi / đã gửi'),h('option',{value:'ALL'},'Tất cả lỗi và replay')),
    h('button',{type:'button',disabled:busy,onClick:()=>setGeneration(v=>v+1)},'Làm mới recovery'),
    receipt&&h('p',{role:'status',className:'alert success'},'Đã chấp nhận yêu cầu '+receipt.requestId+'. Đây là xác nhận ghi audit/xếp hàng, chưa phải xác nhận xử lý xong. Làm mới để xem publisher và consumer.'),
    error&&h('p',{role:'alert',className:'alert error'},error),
    attempt&&h('div',{role:'status',className:'alert pending'},h('p',null,'Chưa xác định kết quả yêu cầu '+attempt.requestId+'. Giữ nguyên event và lý do; không tạo requestId mới.'),known&&h('p',null,'Audit đã có yêu cầu này. Gửi lại cùng yêu cầu không tăng ngân sách hoặc reset lần nữa.'),h('button',{type:'button',disabled:busy,onClick:()=>void send(attempt)},'Gửi lại cùng yêu cầu')),
    selected&&h('section',{ref:panel,tabIndex:-1,className:'recovery-detail editor-panel','aria-label':'Chi tiết recovery'},
      h('div',{className:'section-heading'},h('h3',null,'Sự kiện '+selected),h('button',{type:'button',disabled:busy||!!attempt,onClick:()=>{setSelected(null);setDetail(null);setReceipt(null);}},'Đóng chi tiết recovery')),
      detailLoading&&h('p',{role:'status'},'Đang tải chi tiết recovery…'),detailError&&h('p',{role:'alert'},detailError),
      detail&&h('div',null,h('p',null,detail.eventType+' · '+detail.status+' · version '+detail.aggregateVersion),h('p',null,'Booking: '+detail.bookingId),h('p',null,'Correlation: '+detail.correlationId),h('p',null,'Số lần gửi: '+detail.attempts+' · Lần gửi được xác nhận: '+(detail.publishedAt??'chưa có')),h('p',null,detail.publisherError),
        h('p',{className:'replay-budget'},'Ngân sách còn '+detail.remainingReplays+'/3 yêu cầu mới.'),
        h('h4',null,'Consumer đã ghi nhận'),detail.consumers.length?h('ul',null,...detail.consumers.map(c=>h('li',{key:c.consumer},c.consumer+' · '+c.processedAt))):h('p',null,'Chưa có consumer receipt. Không suy PUBLISHED là nghiệp vụ đã hoàn tất.'),
        detail.consumers.length>0&&h('p',null,'Consumer đã ghi nhận sẽ chống trùng khi phát lại cùng eventId. Consumer chưa ghi nhận có thể xử lý lần đầu. Receipt audit không chứng minh payment đã hoàn tất hoặc bản replay mới đã được nhận.'),
        h('h4',null,'Lỗi consumer/DLQ gần nhất (tối đa 100)'),detail.failures.length?h('ul',null,...detail.failures.map(f=>h('li',{key:f.id},f.consumer+' · '+f.status+' · '+f.error,h('p',null,'DLQ '+f.dlqStatus+' · '+f.dlqAttempts+'/3 lần gửi · Ghi nhận '+f.createdAt+(f.resolvedAt?' · Phục hồi '+f.resolvedAt:''))))):h('p',null,'Chưa có lỗi consumer được lưu cho event này.'),
        h('h4',null,'Lịch sử audit replay'),detail.audit.length?h('ol',{className:'replay-audit'},...detail.audit.map(a=>h('li',{key:a.requestId},h('p',null,'Request: '+a.requestId),h('p',null,'ADMIN '+a.adminAccountId+' · '+a.requestedAt),h('p',null,a.reason)))):h('p',null,'Chưa có yêu cầu replay.'),
        detail.remainingReplays===0&&h('p',{role:'status'},'Đã hết ngân sách replay. Không có thao tác bỏ qua giới hạn.'),
        h('form',{onSubmit:submit},h('label',{htmlFor:'recovery-reason'},'Lý do replay (không nhập token hoặc dữ liệu thanh toán)'),h('textarea',{id:'recovery-reason',rows:3,value:reason,disabled:busy||!!attempt||detail.remainingReplays===0,onChange:(e:ChangeEvent<HTMLTextAreaElement>)=>setReason(e.target.value)}),h('p',null,Array.from(reason).length+'/500 ký tự Unicode'),h('button',{type:'submit',className:'primary',disabled:busy||!!attempt||detail.remainingReplays===0},busy?'Đang gửi replay…':'Gửi yêu cầu replay')))),
    loading&&h('p',{role:'status'},'Đang tải recovery…'),listError&&h('p',{role:'alert'},listError),
    list&&!loading&&h('div',null,list.items.length===0?h('p',{className:'empty-state'},'Không có sự kiện phù hợp bộ lọc recovery.'):h('div',{className:'table-scroll'},h('table',null,h('caption',null,'Lỗi publisher và recovery consumer/DLQ'),h('thead',null,h('tr',null,...['Event / nguồn','Trạng thái','Lý do ghi nhận','DLQ','Thao tác'].map(t=>h('th',{key:t,scope:'col'},t)))),h('tbody',null,...list.items.map(item=>h('tr',{key:item.source+item.id},h('td',{className:'booking-id'},item.eventId??'Không có event hợp lệ',h('small',null,item.source+' · '+(item.eventType??item.consumer??'record chưa liên kết'))),h('td',null,item.status),h('td',null,item.error),h('td',null,item.dlqStatus??'—'),h('td',null,item.eventId&&item.bookingId?h('button',{type:'button',disabled:busy||!!attempt,onClick:()=>choose(item.eventId!)},'Xem event '+item.eventId):h('p',null,'Chỉ điều tra; không có outbox hợp lệ để replay.'))))))),
      h('nav',{'aria-label':'Phân trang recovery',className:'pagination'},h('button',{type:'button',disabled:page===0,onClick:()=>setPage(p=>p-1)},'Recovery trước'),h('span',null,'Trang '+(page+1)+' · '+list.totalElements+' bản ghi'),h('button',{type:'button',disabled:(page+1)*list.size>=list.totalElements,onClick:()=>setPage(p=>p+1)},'Recovery sau'))));
}
