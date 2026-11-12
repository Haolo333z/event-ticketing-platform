package com.haolo.ticketing.shared;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status,String code,String message){super(message);this.status=status;this.code=code;}
    public static ApiException invalid(){return new ApiException(400,"VALIDATION_ERROR","Dữ liệu không hợp lệ");}
    public static ApiException missing(){return new ApiException(404,"NOT_FOUND","Không tìm thấy tài nguyên");}
    public static ApiException conflict(){return new ApiException(409,"CATALOG_CONFLICT","Thay đổi xung đột với dữ liệu hiện tại");}
}
