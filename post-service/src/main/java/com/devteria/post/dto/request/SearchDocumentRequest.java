package com.devteria.post.dto.request;

import java.time.Instant;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * DTO dùng để gửi dữ liệu bài viết từ post-service sang search-service
 * nhằm đánh chỉ mục (index) vào Elasticsearch ngay khi bài viết được tạo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchDocumentRequest {
    String id;
    String type;
    String title;
    String content;
    String authorId;
    String authorName;
    Set<String> tags;
    String thumbnailUrl;
    Instant createdAt;
    Instant updatedAt;
}
