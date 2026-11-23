import {createElement as h,useState,useEffect} from 'react';
import type {Api,User} from '../api/client.ts';
import {setStoredAuth} from '../api/client.ts';

export interface AuthModalProps {
  isOpen:boolean;
  initialTab?:'login'|'register';
  gateNotice?:string;
  onClose:()=>void;
  onSuccess:(user:User)=>void;
  api:Api;
}

export function AuthModal({isOpen,initialTab='login',gateNotice,onClose,onSuccess,api}:AuthModalProps){
  const[tab,setTab]=useState<'login'|'register'>(initialTab);
  const[email,setEmail]=useState('');
  const[password,setPassword]=useState('');
  const[displayName,setDisplayName]=useState('');
  const[phone,setPhone]=useState('');
  const[loading,setLoading]=useState(false);
  const[error,setError]=useState<string|null>(null);
  const[showGoogleMenu,setShowGoogleMenu]=useState(false);
  const[googleLoading,setGoogleLoading]=useState(false);

  useEffect(() => {
    if (isOpen) {
      setError(null);
      setLoading(false);
      setShowGoogleMenu(false);
      setGoogleLoading(false);
      if (initialTab) {
        setTab(initialTab);
      }
    }
  }, [isOpen, initialTab]);

  if(!isOpen)return null;

  async function handleGoogleLogin(demoAccountId?:string){
    setError(null);
    setGoogleLoading(true);
    try{
      if(!api.oauth){
        throw new Error('Dịch vụ OAuth2 chưa sẵn sàng.');
      }
      const res=await api.oauth.googleCallback({
        demoAccountId:demoAccountId||'google-demo-customer'
      });
      setStoredAuth(res.token,res.user);
      onSuccess(res.user);
      onClose();
    }catch(err:any){
      setError(err?.message||'Đăng nhập Google thất bại. Vui lòng thử lại.');
    }finally{
      setGoogleLoading(false);
    }
  }

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

      gateNotice&&h('div',{className:'auth-gate-banner',role:'status'},
        h('span',null,'🔒 '),
        gateNotice
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
            onChange:e=>{setDisplayName((e.target as HTMLInputElement).value);if(error)setError(null);}
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
            onChange:e=>{setEmail((e.target as HTMLInputElement).value);if(error)setError(null);}
          })
        ),

        tab==='register'&&h('div',{className:'form-group'},
          h('label',{htmlFor:'auth-phone'},'Số điện thoại'),
          h('input',{
            id:'auth-phone',
            type:'tel',
            placeholder:'0901234567',
            value:phone,
            onChange:e=>{setPhone((e.target as HTMLInputElement).value);if(error)setError(null);}
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
            onChange:e=>{setPassword((e.target as HTMLInputElement).value);if(error)setError(null);}
          })
        ),

        h('button',{
          type:'submit',
          className:'primary auth-submit-btn',
          disabled:loading
        },loading?'Đang xử lý...':(tab==='login'?'Đăng nhập ngay':'Đăng ký tài khoản'))
      ),

      h('div',{className:'oauth-divider'},
        h('span',null,'hoặc')
      ),

      h('button',{
        type:'button',
        className:'oauth-google-btn',
        disabled:loading||googleLoading,
        onClick:()=>setShowGoogleMenu(!showGoogleMenu)
      },
        h('svg',{width:18,height:18,viewBox:'0 0 24 24',style:{flexShrink:0}},
          h('path',{fill:'#4285F4',d:'M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z'}),
          h('path',{fill:'#34A853',d:'M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z'}),
          h('path',{fill:'#FBBC05',d:'M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z'}),
          h('path',{fill:'#EA4335',d:'M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z'})
        ),
        googleLoading?'Đang kết nối Google...':'Tiếp tục với Google (1-Chạm)'
      ),

      showGoogleMenu&&h('div',{className:'oauth-demo-menu'},
        h('div',{className:'oauth-demo-menu-title'},'✨ Chọn tài khoản Google Demo (1-chạm):'),
        [
          {id:'google-demo-customer',name:'Nguyễn Văn Google',email:'customer.google@eventflow.vn',role:'Khách hàng'},
          {id:'google-demo-vip',name:'Trần Thị VIP Google',email:'vip.google@eventflow.vn',role:'Khách VIP'},
          {id:'google-demo-partner',name:'Lê Hoàng Google Partner',email:'partner.google@eventflow.vn',role:'Đối tác'}
        ].map(acc=>h('button',{
          key:acc.id,
          type:'button',
          className:'oauth-demo-item',
          onClick:()=>handleGoogleLogin(acc.id)
        },
          h('div',{className:'oauth-demo-avatar',style:{display:'flex',alignItems:'center',justifyContent:'center',fontSize:'13px'}},'👤'),
          h('div',{className:'oauth-demo-meta'},
            h('div',{className:'oauth-demo-name'},`${acc.name} (${acc.role})`),
            h('div',{className:'oauth-demo-email'},acc.email)
          )
        ))
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
