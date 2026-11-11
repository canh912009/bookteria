package com.devteria.search.dto.response;

import java.time.Instant;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * DTO trả về thông tin của một tài liệu tìm kiếm cho client (web-app).
 * Tách biệt với SearchDocument entity để đảm bảo tính đóng gói của tầng dữ liệu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchDocumentResponse {
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
