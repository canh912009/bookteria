package com.devteria.search.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devteria.search.dto.ApiResponse;
import com.devteria.search.dto.PageResponse;
import com.devteria.search.dto.request.SearchDocumentRequest;
import com.devteria.search.dto.response.SearchDocumentResponse;
import com.devteria.search.service.SearchService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * REST Controller cung cấp các API tìm kiếm và quản lý chỉ mục (Search Index).
 * Do context-path là "/search", khi đi qua API Gateway (/api/v1/search/** với StripPrefix=2):
 * - POST   /api/v1/search/documents      -> index()
 * - GET    /api/v1/search/documents/{id} -> getById()
 * - DELETE /api/v1/search/documents/{id} -> deleteById()
 * - GET    /api/v1/search?q=...&type=... -> search()
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchController {
    SearchService searchService;

    /**
     * API thêm mới hoặc cập nhật một tài liệu vào chỉ mục Elasticsearch.
     * Được gọi từ các service khác (post-service, profile-service) hoặc từ giao diện quản lý/test trên web-app.
     */
    @PostMapping("/documents")
    ApiResponse<SearchDocumentResponse> index(@RequestBody @Valid SearchDocumentRequest request) {
        return ApiResponse.<SearchDocumentResponse>builder()
                .result(searchService.index(request))
                .build();
    }

    /**
     * API lấy thông tin chi tiết một tài liệu trong chỉ mục theo ID.
     */
    @GetMapping("/documents/{id}")
    ApiResponse<SearchDocumentResponse> getById(@PathVariable String id) {
        return ApiResponse.<SearchDocumentResponse>builder()
                .result(searchService.getById(id))
                .build();
    }

    /**
     * API xóa một tài liệu khỏi chỉ mục Elasticsearch theo ID.
     */
    @DeleteMapping("/documents/{id}")
    ApiResponse<Void> deleteById(@PathVariable String id) {
        searchService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Document deleted successfully")
                .build();
    }

    /**
     * API tìm kiếm toàn văn (Full-Text Search) có hỗ trợ lọc theo phân loại (type) và phân trang.
     * Ví dụ: GET /api/v1/search?q=java&type=book&page=1&size=10
     */
    @GetMapping
    ApiResponse<PageResponse<SearchDocumentResponse>> search(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<SearchDocumentResponse>>builder()
                .result(searchService.search(keyword, type, page, size))
                .build();
    }
}
