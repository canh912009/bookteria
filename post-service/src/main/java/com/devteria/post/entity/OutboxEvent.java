package com.devteria.post.entity;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Thực thể Transactional Outbox lưu trong collection "outbox_event" của MongoDB.
 * Khi một bài viết (Post) được tạo/sửa/xóa, một bản ghi OutboxEvent tương ứng sẽ được ghi
 * trong CÙNG MỘT TRANSACTION với bài viết đó để giải quyết triệt để bài toán Dual-Write.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "outbox_event")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {
    @MongoId
    String id;

    /**
     * Loại thực thể gốc (ví dụ: "POST").
     */
    String aggregateType;

    /**
     * ID của thực thể gốc (ví dụ: "post-665a...").
     * Được dùng làm Kafka Message Key để đảm bảo mọi sự kiện của cùng 1 bài viết luôn vào cùng 1 Partition theo đúng thứ tự.
     */
    String aggregateId;

    /**
     * Loại hành động: "UPSERT" hoặc "DELETE".
     */
    String eventType;

    /**
     * Chuỗi JSON chứa toàn bộ thông tin SearchIndexEvent sẽ gửi sang Kafka.
     */
    String payload;

    /**
     * Trạng thái xử lý của sự kiện (đánh index để OutboxRelayService truy vấn nhanh các bản ghi PENDING).
     */
    @Indexed
    OutboxStatus status;

    /**
     * Số lần đã thử gửi lên Kafka nhưng gặp lỗi.
     */
    int retryCount;

    /**
     * Lỗi gần nhất nếu gửi Kafka thất bại.
     */
    String lastError;

    Instant createdAt;
    Instant processedAt;
}
