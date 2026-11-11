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
 * Sự kiện chuẩn hóa (Event Payload) được đóng gói trong Node Outbox của profile-service
 * và gửi qua Kafka topic "search-index-events" tới search-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchIndexEvent {
    String eventId;
    String action;
    String documentId;
    String type;
    String title;
    String content;
    String authorId;
    String authorName;
    Set<String> tags;
    String thumbnailUrl;
    Instant createdAt;
    Instant updatedAt;
    Long version;
}
