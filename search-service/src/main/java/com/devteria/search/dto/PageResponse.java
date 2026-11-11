package com.devteria.search.dto;

import java.util.Collections;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Cấu trúc dữ liệu phân trang (Pagination Response) trả về khi tìm kiếm danh sách tài liệu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {
    // Trang hiện tại (bắt đầu từ 1)
    int currentPage;
    // Số lượng phần tử tối đa trên mỗi trang
    int pageSize;
    // Tổng số phần tử khớp với điều kiện tìm kiếm
    long totalElements;
    // Tổng số trang
    int totalPages;

    // Danh sách dữ liệu của trang hiện tại
    @Builder.Default
    List<T> data = Collections.emptyList();
}
