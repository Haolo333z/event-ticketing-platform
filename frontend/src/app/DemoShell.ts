import {createElement as h,useState} from 'react';
import {App} from './App.ts';
import {AdminDashboard} from './AdminDashboard.ts';
import type {Api,Store} from '../api/client.ts';
import type {AdminApi} from '../api/admin.ts';

export function DemoShell({customer,admin,store}:{customer:Api;admin:AdminApi;store:Store}){
  const[persona,setPersona]=useState<'customer'|'admin'>('customer');
  const[adminVisited,setAdminVisited]=useState(false);
  return h('div',null,h('header',{className:'demo-topbar shell'},h('a',{href:'/',className:'brand'},'EventFlow',h('span',null,'Demo local · Không thu tiền thật')),
    h('nav',{'aria-label':'Vai trò demo',className:'persona-picker'},h('button',{type:'button','aria-pressed':persona==='customer',onClick:()=>setPersona('customer')},'Khách hàng demo'),h('button',{type:'button','aria-pressed':persona==='admin',onClick:()=>{setAdminVisited(true);setPersona('admin');}},'Quản trị demo'))),
    h('div',{hidden:persona!=='customer'},h(App,{api:customer,store,embedded:true})),adminVisited&&h('div',{hidden:persona!=='admin'},h(AdminDashboard,{api:admin})));
}
