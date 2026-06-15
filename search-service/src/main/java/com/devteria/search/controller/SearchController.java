package com.devteria.search.controller;

import com.devteria.search.dto.ApiResponse;
import com.devteria.search.dto.PageResponse;
import com.devteria.search.dto.request.SearchDocumentRequest;
import com.devteria.search.dto.response.SearchDocumentResponse;
import com.devteria.search.service.SearchService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchController {
    SearchService searchService;

    @PostMapping("/documents")
    ApiResponse<SearchDocumentResponse> index(@RequestBody @Valid SearchDocumentRequest request) {
        return ApiResponse.<SearchDocumentResponse>builder().result(searchService.index(request)).build();
    }

    @GetMapping("/documents/{id}")
    ApiResponse<SearchDocumentResponse> getById(@PathVariable String id) {
        return ApiResponse.<SearchDocumentResponse>builder().result(searchService.getById(id)).build();
    }

    @DeleteMapping("/documents/{id}")
    ApiResponse<Void> deleteById(@PathVariable String id) {
        searchService.deleteById(id);
        return ApiResponse.<Void>builder().build();
    }

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
