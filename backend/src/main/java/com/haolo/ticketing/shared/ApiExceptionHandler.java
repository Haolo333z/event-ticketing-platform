package com.haolo.ticketing.shared;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String,Object>> api(ApiException ex,HttpServletRequest req){return result(ex.status,ex.code,ex.getMessage(),req);}
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class})
    ResponseEntity<Map<String,Object>> invalid(Exception ex,HttpServletRequest req){return result(400,"VALIDATION_ERROR","Dữ liệu không hợp lệ",req);}
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String,Object>> method(Exception ex,HttpServletRequest req){return result(405,"VALIDATION_ERROR","Phương thức không hỗ trợ",req);}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String,Object>> conflict(Exception ex,HttpServletRequest req){return result(409,"CATALOG_CONFLICT","Thay đổi xung đột dữ liệu",req);}
    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<Map<String,Object>> busy(Exception ex,HttpServletRequest req){return result(503,"BOOKING_BUSY","Cơ sở dữ liệu đang bận; hãy thử lại",req);}
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String,Object>> database(DataAccessException ex,HttpServletRequest req){
        for(Throwable cause=ex;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql){
            String state=sql.getSQLState();
            if(state!=null&&(state.equals("55P03")||state.equals("57014")||state.equals("40P01")||state.equals("40001")||state.startsWith("08")))return busy(ex,req);
        }
        return result(500,"INTERNAL_ERROR","Lỗi cơ sở dữ liệu",req);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> unexpected(Exception ex,HttpServletRequest req){return result(500,"INTERNAL_ERROR","Lỗi hệ thống",req);}
    private ResponseEntity<Map<String,Object>> result(int status,String code,String message,HttpServletRequest req){
        var builder=ResponseEntity.status(status);if(status==503)builder.header("Retry-After","1");
        return builder.body(ApiErrors.body(req,code,message));
    }
}
