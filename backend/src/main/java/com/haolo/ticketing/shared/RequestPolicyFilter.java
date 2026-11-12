package com.haolo.ticketing.shared;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.haolo.ticketing.identity.DemoSettings;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+1)
public class RequestPolicyFilter extends OncePerRequestFilter {
    private final ApiErrors errors;private final DemoSettings settings;
    private static final Set<String> ORIGINS=Set.of("http://127.0.0.1:5173","http://localhost:5173");
    private static final Set<String> HEADERS=Set.of("authorization","content-type","idempotency-key","x-correlation-id");
    public RequestPolicyFilter(ApiErrors errors,DemoSettings settings){this.errors=errors;this.settings=settings;}
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if(!req.getRequestURI().startsWith("/api/")){chain.doFilter(req,res);return;}
        String origin=req.getHeader("Origin");boolean allowed=settings.safeProfile && ORIGINS.contains(origin==null?"":origin);
        if(allowed){res.setHeader("Access-Control-Allow-Origin",origin);res.setHeader("Vary","Origin");res.setHeader("Access-Control-Expose-Headers","Location, Retry-After, X-Correlation-Id");}
        if(req.getMethod().equals("OPTIONS") && req.getHeader("Access-Control-Request-Method")!=null){
            String method=req.getHeader("Access-Control-Request-Method"),headers=req.getHeader("Access-Control-Request-Headers");
            boolean valid=allowed && Set.of("GET","POST","PATCH","OPTIONS").contains(method)
                && (headers==null||Arrays.stream(headers.split(",")).allMatch(h->HEADERS.contains(h.trim().toLowerCase(Locale.ROOT))));
            if(!valid){errors.write(req,res,403,"FORBIDDEN","Origin hoặc preflight không được phép");return;}
            res.setHeader("Access-Control-Allow-Methods","GET, POST, PATCH, OPTIONS");res.setHeader("Access-Control-Allow-Headers","Authorization, Content-Type, Idempotency-Key, X-Correlation-Id");res.setHeader("Access-Control-Max-Age","600");res.setStatus(204);return;
        }
        boolean bodyMethod=(req.getMethod().equals("POST")||req.getMethod().equals("PATCH"))&&!req.getRequestURI().matches("/api/bookings/[^/]+/cancel");
        if(!bodyMethod){if(req.getInputStream().read()!=-1){errors.write(req,res,400,"VALIDATION_ERROR","Endpoint không nhận body");return;}chain.doFilter(req,res);return;}
        String encoding=req.getHeader("Content-Encoding");
        String contentType=req.getContentType();
        if((encoding!=null&&!encoding.equalsIgnoreCase("identity"))||contentType==null||!contentType.split(";")[0].trim().equalsIgnoreCase("application/json")){
            errors.write(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Chỉ nhận JSON không nén");return;
        }
        try {
            var charset=org.springframework.http.MediaType.parseMediaType(contentType).getCharset();
            if(charset!=null&&!charset.equals(StandardCharsets.UTF_8))throw new IllegalArgumentException();
        } catch(IllegalArgumentException ex){errors.write(req,res,415,"UNSUPPORTED_MEDIA_TYPE","Chỉ nhận UTF-8");return;}
        int limit=req.getRequestURI().matches("/api/admin/events(?:/[^/]+)?")?131072:8192;
        byte[] bytes=req.getInputStream().readNBytes(limit+1);
        if(bytes.length>limit){errors.write(req,res,413,"PAYLOAD_TOO_LARGE","Body vượt giới hạn");return;}
        try {
            var decoded=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            if(decoded.toString().indexOf('\0')>=0)throw new java.nio.charset.CharacterCodingException();
        } catch(java.nio.charset.CharacterCodingException ex){errors.write(req,res,400,"VALIDATION_ERROR","Body phải là UTF-8 hợp lệ");return;}
        int depth=0;boolean quoted=false,escaped=false;
        for(byte b:bytes){
            if(quoted){if(escaped)escaped=false;else if(b=='\\')escaped=true;else if(b=='"')quoted=false;}
            else if(b=='"')quoted=true;
            else if(b=='{'||b=='['){if(++depth>10){errors.write(req,res,400,"VALIDATION_ERROR","JSON lồng quá sâu");return;}}
            else if(b=='}'||b==']')depth--;
        }
        chain.doFilter(new HttpServletRequestWrapper(req){
            public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){
                public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}
                public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
            };}
            public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },res);
    }
}
