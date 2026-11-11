package com.devteria.event.dto;

import java.time.Instant;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Sự kiện chuẩn hóa (Event Payload) được gửi qua Kafka topic "search-index-events".
 * Đặt chung package "com.devteria.event.dto" (giống NotificationEvent trong dự án)
 * để JsonSerializer ở Producer và JsonDeserializer ở Consumer tự động ánh xạ kiểu dữ liệu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchIndexEvent {
    /**
     * ID duy nhất của bản ghi OutboxEvent.
     */
    String eventId;

    /**
     * Hành động đồng bộ: "UPSERT" (thêm mới hoặc cập nhật) hoặc "DELETE" (xóa khỏi Elasticsearch).
     */
    String action;

    /**
     * ID của tài liệu trên Elasticsearch (ví dụ: "post-665a...", "user-98b1...").
     */
    String documentId;

    /**
     * Phân loại tài nguyên ("post", "user", "book").
     */
    String type;

    String title;
    String content;
    String authorId;
    String authorName;
    Set<String> tags;
    String thumbnailUrl;
    Instant createdAt;
    Instant updatedAt;

    /**
     * Phiên bản sự kiện (tính bằng epoch毫秒 tại thời điểm ghi vào Outbox).
     * Dùng để bảo vệ tính Idempotent và chống ghi đè ngược (Out-of-order protection):
     * Nếu Elasticsearch đã có bản ghi với sourceVersion > version của event đến sau, event cũ sẽ bị bỏ qua.
     */
    Long version;
}
