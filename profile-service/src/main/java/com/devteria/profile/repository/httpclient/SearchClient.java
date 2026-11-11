package com.devteria.profile.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devteria.profile.dto.ApiResponse;
import com.devteria.profile.dto.request.SearchDocumentRequest;

/**
 * OpenFeign Client để profile-service gọi sang search-service đồng bộ thông tin hồ sơ người dùng.
 */
@FeignClient(name = "search-service", url = "${app.services.search.url}")
public interface SearchClient {
    @PostMapping("/documents")
    ApiResponse<Object> indexDocument(@RequestBody SearchDocumentRequest request);
}
