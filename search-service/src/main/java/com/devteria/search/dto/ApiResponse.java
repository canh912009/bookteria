package com.devteria.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Cấu trúc phản hồi API chuẩn (Standard API Wrapper) dùng chung trên toàn hệ thống Bookteria.
 * - code = 1000: Thành công (mặc định).
 * - message: Thông báo lỗi (nếu có).
 * - result: Dữ liệu kết quả trả về.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Builder.Default
    int code = 1000;

    String message;
    T result;
}
