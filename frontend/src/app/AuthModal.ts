import {createElement as h,useState} from 'react';
import type {Api,User} from '../api/client.ts';
import {setStoredAuth} from '../api/client.ts';

export interface AuthModalProps {
  isOpen:boolean;
  initialTab?:'login'|'register';
  onClose:()=>void;
  onSuccess:(user:User)=>void;
  api:Api;
}

export function AuthModal({isOpen,initialTab='login',onClose,onSuccess,api}:AuthModalProps){
  const[tab,setTab]=useState<'login'|'register'>(initialTab);
  const[email,setEmail]=useState('');
  const[password,setPassword]=useState('');
  const[displayName,setDisplayName]=useState('');
  const[phone,setPhone]=useState('');
  const[loading,setLoading]=useState(false);
  const[error,setError]=useState<string|null>(null);

  if(!isOpen)return null;

  async function handleSubmit(e:React.FormEvent){
    e.preventDefault();
    setError(null);
    setLoading(true);

    if(!api.auth){
      setError('Dịch vụ xác thực chưa sẵn sàng.');
      setLoading(false);
      return;
    }

    try{
      if(tab==='login'){
        const res=await api.auth.login({email:email.trim(),password});
        setStoredAuth(res.token,res.user);
        onSuccess(res.user);
        onClose();
      }else{
        if(password.length<6){
          setError('Mật khẩu phải có tối thiểu 6 ký tự');
          setLoading(false);
          return;
        }
        const res=await api.auth.register({
          displayName:displayName.trim(),
          email:email.trim(),
          password,
          phone:phone.trim()
        });
        setStoredAuth(res.token,res.user);
        onSuccess(res.user);
        onClose();
      }
    }catch(err:any){
      setError(err?.message||'Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.');
    }finally{
      setLoading(false);
    }
  }

  function fillDemo(emailVal:string,passVal:string){
    setEmail(emailVal);
    setPassword(passVal);
    setError(null);
  }

  return h('div',{className:'auth-modal-backdrop',onClick:(e)=>{if(e.target===e.currentTarget)onClose();}},
    h('div',{className:'auth-modal-card',role:'dialog','aria-modal':true},
      h('div',{className:'auth-modal-header'},
        h('div',{className:'auth-brand'},
          h('span',{className:'auth-brand-logo'},'⚡ EventFlow'),
          h('p',{className:'auth-brand-subtitle'},tab==='login'?'Đăng nhập để đặt vé & quản lý đơn hàng':'Tạo tài khoản mới để trải nghiệm đầy đủ')
        ),
        h('button',{type:'button',className:'auth-close-btn',onClick:onClose,'aria-label':'Đóng'},'×')
      ),

      h('div',{className:'auth-tabs'},
        h('button',{
          type:'button',
          className:'auth-tab-btn'+(tab==='login'?' active':''),
          onClick:()=>{setTab('login');setError(null);}
        },'Đăng nhập'),
        h('button',{
          type:'button',
          className:'auth-tab-btn'+(tab==='register'?' active':''),
          onClick:()=>{setTab('register');setError(null);}
        },'Đăng ký tài khoản')
      ),

      error&&h('div',{className:'auth-error-alert',role:'alert'},
        h('span',null,'⚠️ '),
        error
      ),

      h('form',{className:'auth-form',onSubmit:handleSubmit},
        tab==='register'&&h('div',{className:'form-group'},
          h('label',{htmlFor:'auth-name'},'Họ và tên *'),
          h('input',{
            id:'auth-name',
            type:'text',
            required:true,
            placeholder:'Nguyễn Văn A',
            value:displayName,
            onChange:e=>setDisplayName((e.target as HTMLInputElement).value)
          })
        ),

        h('div',{className:'form-group'},
          h('label',{htmlFor:'auth-email'},'Địa chỉ Email *'),
          h('input',{
            id:'auth-email',
            type:'email',
            required:true,
            placeholder:'ban@email.com',
            value:email,
            onChange:e=>setEmail((e.target as HTMLInputElement).value)
          })
        ),

        tab==='register'&&h('div',{className:'form-group'},
          h('label',{htmlFor:'auth-phone'},'Số điện thoại'),
          h('input',{
            id:'auth-phone',
            type:'tel',
            placeholder:'0901234567',
            value:phone,
            onChange:e=>setPhone((e.target as HTMLInputElement).value)
          })
        ),

        h('div',{className:'form-group'},
          h('label',{htmlFor:'auth-pass'},'Mật khẩu *'),
          h('input',{
            id:'auth-pass',
            type:'password',
            required:true,
            placeholder:'Tối thiểu 6 ký tự',
            value:password,
            onChange:e=>setPassword((e.target as HTMLInputElement).value)
          })
        ),

        h('button',{
          type:'submit',
          className:'primary auth-submit-btn',
          disabled:loading
        },loading?'Đang xử lý...':(tab==='login'?'Đăng nhập ngay':'Đăng ký tài khoản'))
      ),

      tab==='login'&&h('div',{className:'auth-demo-hints'},
        h('span',{className:'hint-label'},'Tài khoản có sẵn:'),
        h('div',{className:'hint-buttons'},
          h('button',{
            type:'button',
            className:'hint-chip',
            onClick:()=>fillDemo('customer@eventflow.vn','Customer@123')
          },'👤 Khách hàng (customer@eventflow.vn)'),
          h('button',{
            type:'button',
            className:'hint-chip',
            onClick:()=>fillDemo('admin@eventflow.vn','Admin@123')
          },'🛡️ Quản trị viên (admin@eventflow.vn)')
        )
      )
    )
  );
}
