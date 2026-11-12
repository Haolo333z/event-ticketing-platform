package com.haolo.ticketing.shared;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiErrors {
    private final ObjectMapper mapper;
    public ApiErrors(ObjectMapper mapper){this.mapper=mapper;}
    public static Map<String,Object> body(HttpServletRequest request,String code,String message){
        Object id=request.getAttribute("correlationId");
        return Map.of("code",code,"message",message,"correlationId",id==null?UUID.randomUUID().toString():id.toString());
    }
    public void write(HttpServletRequest request,HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        if(status==503)response.setHeader("Retry-After","1");
        response.getWriter().write(mapper.writeValueAsString(body(request,code,message)));
    }
}
