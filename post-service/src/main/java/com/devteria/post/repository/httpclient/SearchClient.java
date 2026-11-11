package com.devteria.post.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devteria.post.dto.ApiResponse;
import com.devteria.post.dto.request.SearchDocumentRequest;

/**
 * OpenFeign Client để post-service gọi sang search-service đồng bộ bài viết mới vào Elasticsearch.
 */
@FeignClient(name = "search-service", url = "${app.services.search.url}")
public interface SearchClient {
    @PostMapping("/documents")
    ApiResponse<Object> indexDocument(@RequestBody SearchDocumentRequest request);
}
