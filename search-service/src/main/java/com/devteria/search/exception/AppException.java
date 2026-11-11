package com.devteria.search.exception;

/**
 * Ngoại lệ nghiệp vụ tùy chỉnh (Custom Business Exception) của search-service.
 * Khi gặp lỗi nghiệp vụ (ví dụ: không tìm thấy document), tầng Service sẽ ném AppException kèm ErrorCode.
 */
public class AppException extends RuntimeException {
    private ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
