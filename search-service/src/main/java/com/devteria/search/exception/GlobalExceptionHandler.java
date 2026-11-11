package com.devteria.search.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.devteria.search.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Bộ xử lý ngoại lệ toàn cục (Global Exception Handler).
 * Tự động bắt các Exception phát sinh từ Controller/Service và chuyển đổi thành ApiResponse thống nhất.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Xử lý các lỗi hệ thống chưa xác định (Exception chung).
     */
    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handlingException(Exception exception) {
        log.error("Unhandled exception occurred in search-service: ", exception);
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;

        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    /**
     * Xử lý lỗi nghiệp vụ do ứng dụng chủ động ném ra (AppException).
     */
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    /**
     * Xử lý lỗi không hợp lệ dữ liệu đầu vào khi dùng @Valid trên RequestBody.
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getFieldError();
        String enumKey = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_KEY.name();

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
        } catch (IllegalArgumentException ignored) {
            // Giữ mặc định INVALID_KEY nếu message trên annotation không khớp tên enum
        }

        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }
}
