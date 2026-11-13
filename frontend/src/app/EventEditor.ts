import {createElement as h,useRef,useState} from 'react';
import type {FormEvent} from 'react';
import {ApiError} from '../api/client.ts';
import type {AdminEvent,AdminInventory,CreateEvent,PatchEvent,TypeInput} from '../api/admin.ts';

type DraftType={id?:string;name:string;mode:'GA'|'SEATED';priceVnd:number;capacity:number;labels:string;active:boolean};
const localTime=(value:string)=>{const date=new Date(value);return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);};
const utc=(value:string)=>new Date(value).toISOString();
export function EventEditor({event,inventory,onSave,onCancel,blocked=false}:{event:AdminEvent|null;inventory:AdminInventory|null;onSave:(body:CreateEvent|PatchEvent)=>Promise<void>;onCancel:()=>void;blocked?:boolean}){
  const[title,setTitle]=useState(event?.title??''),[description,setDescription]=useState(event?.description??''),[venue,setVenue]=useState(event?.venue??'');
  const[start,setStart]=useState(localTime(event?.saleStartAt??new Date(Date.now()-3600000).toISOString()));
  const[end,setEnd]=useState(localTime(event?.saleEndAt??new Date(Date.now()+86400000).toISOString()));
  const[show,setShow]=useState(localTime(event?.startsAt??new Date(Date.now()+172800000).toISOString()));
  const[status,setStatus]=useState<AdminEvent['status']>(event?.status??'DRAFT');
  const[types,setTypes]=useState<DraftType[]>(()=>event?event.ticketTypes.map(t=>{const stock=inventory?.ticketTypes.find(i=>i.ticketTypeId===t.id);return{...t,capacity:stock?.mode==='GA'?stock.capacity:0,labels:stock?.mode==='SEATED'?stock.seats.map(s=>s.label).join('\n'):''};}):[{name:'Vào cửa',mode:'GA',priceVnd:100000,capacity:100,labels:'',active:true}]);
  const[busy,setBusy]=useState(false),[error,setError]=useState('');const sending=useRef(false);
  function change(index:number,update:Partial<DraftType>){setTypes(all=>all.map((t,i)=>i===index?{...t,...update}:t));}
  async function submit(e:FormEvent){
    e.preventDefault();if(sending.current||blocked)return;setError('');
    try{
      if(!title.trim()||!venue.trim()||!types.length||types.some(t=>!t.name.trim()||!Number.isSafeInteger(t.priceVnd)||t.priceVnd<0||t.priceVnd>1e12||t.mode==='GA'&&(!Number.isSafeInteger(t.capacity)||t.capacity<0||t.capacity>1000000)))throw new Error('Kiểm tra tên, địa điểm, giá và sức chứa.');
      const dates={saleStartAt:utc(start),saleEndAt:utc(end),startsAt:utc(show)};
      if(dates.saleStartAt>=dates.saleEndAt||dates.saleEndAt>dates.startsAt)throw new Error('Thứ tự thời gian cần đúng: mở bán < đóng bán ≤ bắt đầu.');
      const common={title,description,venue,...dates,status};let body:CreateEvent|PatchEvent;
      if(event)body={...common,ticketTypes:types.map(t=>({id:t.id!,name:t.name,priceVnd:t.priceVnd,active:t.active,...(t.mode==='GA'?{capacity:t.capacity}:{})}))};
      else{
        const labels=new Set<string>();
        const ticketTypes:TypeInput[]=types.map(t=>{
          if(t.mode==='GA')return{name:t.name,mode:'GA',priceVnd:t.priceVnd,capacity:t.capacity};
          const seats=t.labels.split(/\r?\n/).map(label=>label.trim()).filter(Boolean).map(label=>({label}));
          if(!seats.length||seats.length>200||seats.some(s=>[...s.label].length>40||labels.has(s.label)||(labels.add(s.label),false))||labels.size>200)throw new Error('Mỗi ghế cần nhãn khác nhau, tối đa 40 ký tự; tổng tối đa 200 ghế.');
          return{name:t.name,mode:'SEATED',priceVnd:t.priceVnd,seats};
        });body={...common,ticketTypes};
      }
      sending.current=true;setBusy(true);await onSave(body);
    }catch(value){setError(value instanceof ApiError?value.message+' · '+value.code+(value.correlationId?' · Mã tra cứu: '+value.correlationId:''):value instanceof Error?value.message:'Không lưu được sự kiện.');}
    finally{sending.current=false;setBusy(false);}
  }
  const field=(id:string,label:string,value:string,set:(value:string)=>void,type='text',max?:number)=>h('div',null,h('label',{htmlFor:id},label),h('input',{id,name:id,type,value,required:true,maxLength:max,disabled:busy,onChange:(e:React.ChangeEvent<HTMLInputElement>)=>set(e.target.value)}));
  return h('form',{className:'event-editor',onSubmit:submit,'aria-label':event?'Sửa sự kiện':'Tạo sự kiện'},
    h('p',{className:'muted'},'Thời gian theo múi giờ trên máy, lưu UTC. Giá và sức chứa được máy chủ kiểm tra lại.'),
    error&&h('p',{role:'alert',className:'alert error'},error),
    field('admin-title','Tên sự kiện',title,setTitle,'text',200),field('admin-venue','Địa điểm',venue,setVenue,'text',200),
    h('label',{htmlFor:'admin-description'},'Mô tả'),h('textarea',{id:'admin-description',value:description,maxLength:2000,disabled:busy,onChange:(e:React.ChangeEvent<HTMLTextAreaElement>)=>setDescription(e.target.value)}),
    h('div',{className:'form-grid'},field('admin-sale-start','Mở bán',start,setStart,'datetime-local'),field('admin-sale-end','Đóng bán',end,setEnd,'datetime-local'),field('admin-starts','Bắt đầu',show,setShow,'datetime-local')),
    h('label',{htmlFor:'admin-status'},'Trạng thái sự kiện'),h('select',{id:'admin-status',value:status,disabled:busy,onChange:(e:React.ChangeEvent<HTMLSelectElement>)=>setStatus(e.target.value as AdminEvent['status'])},...['DRAFT','PUBLISHED','CLOSED'].map(s=>h('option',{key:s,value:s},s))),
    ...types.map((t,index)=>h('fieldset',{key:t.id??index,disabled:busy},h('legend',null,'Loại vé '+(index+1)+' · '+t.mode),
      h('label',{htmlFor:'type-name-'+index},'Tên loại vé'),h('input',{id:'type-name-'+index,value:t.name,maxLength:100,required:true,onChange:(e:React.ChangeEvent<HTMLInputElement>)=>change(index,{name:e.target.value})}),
      h('div',{className:'form-grid'},h('div',null,h('label',{htmlFor:'type-price-'+index},'Giá vé (VND)'),h('input',{id:'type-price-'+index,type:'number',min:0,max:1e12,step:1,value:t.priceVnd,required:true,onChange:(e:React.ChangeEvent<HTMLInputElement>)=>change(index,{priceVnd:Number(e.target.value)})})),
      t.mode==='GA'&&h('div',null,h('label',{htmlFor:'type-capacity-'+index},'Sức chứa GA'),h('input',{id:'type-capacity-'+index,type:'number',min:0,max:1000000,step:1,value:t.capacity,required:true,onChange:(e:React.ChangeEvent<HTMLInputElement>)=>change(index,{capacity:Number(e.target.value)})}))),
      t.mode==='SEATED'&&h('div',null,h('label',{htmlFor:'type-seats-'+index},event?'Ghế đã cấu hình (không sửa)':'Nhãn ghế, mỗi dòng một ghế'),h('textarea',{id:'type-seats-'+index,value:t.labels,readOnly:!!event,required:true,onChange:(e:React.ChangeEvent<HTMLTextAreaElement>)=>change(index,{labels:e.target.value})})),
      event?h('label',{className:'check-label'},h('input',{type:'checkbox',checked:t.active,onChange:(e:React.ChangeEvent<HTMLInputElement>)=>change(index,{active:e.target.checked})}),'Đang bán loại vé này'):h('button',{type:'button',className:'quiet',disabled:types.length===1,onClick:()=>setTypes(all=>all.filter((_,i)=>i!==index))},'Bỏ loại vé '+(index+1)))),
    !event&&h('div',{className:'actions'},...(['GA','SEATED'] as const).map(mode=>h('button',{key:mode,type:'button',disabled:busy||types.length>=20,onClick:()=>setTypes(all=>[...all,{name:mode==='GA'?'Vào cửa':'Ghế chỉ định',mode,priceVnd:100000,capacity:100,labels:'A1\nA2',active:true}])},mode==='GA'?'Thêm loại vé GA':'Thêm loại vé ghế'))),
    h('div',{className:'actions'},h('button',{type:'submit',className:'primary',disabled:busy||blocked},busy?'Đang lưu…':event?'Lưu thay đổi':'Tạo sự kiện'),h('button',{type:'button',disabled:busy,onClick:onCancel},'Hủy')));
}
