package com.devteria.search.controller;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.search.service.SearchService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Consumer lắng nghe các sự kiện đồng bộ dữ liệu từ Transactional Outbox của:
 * - post-service (MongoDB Outbox)
 * - profile-service (Neo4j Outbox)
 *
 * Nếu quá trình ghi vào Elasticsearch gặp lỗi tạm thời, KafkaConsumerConfig sẽ tự động retry 3 lần
 * trước khi đẩy bản tin lỗi vào Dead Letter Topic ("search-index-events.DLT").
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchEventConsumer {
    SearchService searchService;

    /**
     * Lắng nghe topic chính "search-index-events" và cập nhật chỉ mục Elasticsearch.
     */
    @KafkaListener(topics = "search-index-events", groupId = "search-service-group")
    public void listenSearchIndexEvents(SearchIndexEvent event) {
        log.info(
                "Received SearchIndexEvent from Kafka: eventId={}, action={}, documentId={}",
                event.getEventId(),
                event.getAction(),
                event.getDocumentId());
        searchService.consumeSearchIndexEvent(event);
    }

    /**
     * Lắng nghe Dead Letter Topic "search-index-events.DLT" để ghi log cảnh báo khi có bản tin lỗi sau tối đa số lần retry.
     */
    @KafkaListener(topics = "search-index-events.DLT", groupId = "search-service-dlt-group")
    public void listenDeadLetterTopic(SearchIndexEvent failedEvent) {
        log.error(
                "ALERT [DLT]: SearchIndexEvent could not be indexed after retries: eventId={}, documentId={}",
                failedEvent != null ? failedEvent.getEventId() : "null",
                failedEvent != null ? failedEvent.getDocumentId() : "null");
    }
}
