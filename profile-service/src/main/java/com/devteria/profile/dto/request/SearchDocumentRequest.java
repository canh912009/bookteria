package com.devteria.profile.dto.request;

import java.time.Instant;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * DTO gửi dữ liệu hồ sơ người dùng (User Profile) từ profile-service sang search-service
 * để đánh chỉ mục vào Elasticsearch khi tạo mới hoặc cập nhật hồ sơ.
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
