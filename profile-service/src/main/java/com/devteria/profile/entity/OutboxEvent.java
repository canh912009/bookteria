package com.devteria.profile.entity;

import java.time.Instant;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Thực thể Transactional Outbox lưu dưới dạng Node ("outbox_event") trong cơ sở dữ liệu đồ thị Neo4j.
 * Neo4j hỗ trợ sẵn ACID Transaction, nên khi ghi UserProfile và OutboxEvent trong cùng một hàm @Transactional,
 * cả hai node sẽ cùng được commit hoặc cùng rollback.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Node("outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    String id;

    /**
     * Loại thực thể gốc ("USER_PROFILE").
     */
    String aggregateType;

    /**
     * ID tài liệu đích trên Elasticsearch (ví dụ: "user-98b1..."), đồng thời là Kafka Message Key.
     */
    String aggregateId;

    /**
     * Hành động: "UPSERT" hoặc "DELETE".
     */
    String eventType;

    /**
     * Chuỗi JSON chứa toàn bộ thông tin SearchIndexEvent.
     */
    String payload;

    /**
     * Trạng thái xử lý: PENDING, SENT, FAILED.
     */
    OutboxStatus status;

    int retryCount;
    String lastError;
    Instant createdAt;
    Instant processedAt;
}
