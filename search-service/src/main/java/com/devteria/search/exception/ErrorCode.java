package com.devteria.search.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

/**
 * Enum định nghĩa tập trung toàn bộ mã lỗi (ErrorCode) của search-service.
 * Giúp chuẩn hóa phản hồi lỗi (code, message, HTTP status) cho client (web-app).
 */
@Getter
public enum ErrorCode {
    // Lỗi hệ thống chưa được phân loại (mặc định trả về HTTP 500)
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    // Lỗi khi key truyền vào annotation validation không khớp với ErrorCode
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    // Các lỗi validation dữ liệu đầu vào khi đánh chỉ mục (index) tài liệu
    ID_REQUIRED(1002, "Document id is required", HttpStatus.BAD_REQUEST),
    TYPE_REQUIRED(1003, "Document type is required", HttpStatus.BAD_REQUEST),
    TITLE_REQUIRED(1004, "Document title is required", HttpStatus.BAD_REQUEST),
    // Lỗi không tìm thấy tài liệu trong Elasticsearch
    DOCUMENT_NOT_FOUND(1005, "Search document not found", HttpStatus.NOT_FOUND),
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
