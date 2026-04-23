package com.newsquery.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(
            new ErrorResponse("잘못된 요청: " + e.getMessage(), "INVALID_ARGUMENT")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception e) {
        return ResponseEntity.internalServerError().body(
            new ErrorResponse("서버 오류가 발생했습니다. 관리자에게 문의해주세요.", "INTERNAL_SERVER_ERROR")
        );
    }
}
