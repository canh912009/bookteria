package com.devteria.profile.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.profile.entity.OutboxEvent;
import com.devteria.profile.entity.OutboxStatus;
import com.devteria.profile.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Tiến trình nền (Outbox Polling Relay) của profile-service.
 * Định kỳ mỗi 2 giây quét các node "outbox_event" có status = PENDING trong Neo4j,
 * gửi lên Kafka topic "search-index-events" và chờ ACK trước khi đánh dấu SENT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxRelayService {
    static final String SEARCH_INDEX_TOPIC = "search-index-events";
    static final int MAX_RETRY_COUNT = 10;

    OutboxEventRepository outboxEventRepository;
    KafkaTemplate<String, Object> kafkaTemplate;
    ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000)
    public void publishPendingOutboxEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                SearchIndexEvent eventPayload =
                        objectMapper.readValue(outboxEvent.getPayload(), SearchIndexEvent.class);
                eventPayload.setEventId(outboxEvent.getId());

                // Gửi lên Kafka với key = aggregateId và đợi Broker xác nhận
                kafkaTemplate
                        .send(SEARCH_INDEX_TOPIC, outboxEvent.getAggregateId(), eventPayload)
                        .get(5, TimeUnit.SECONDS);

                outboxEvent.setStatus(OutboxStatus.SENT);
                outboxEvent.setProcessedAt(Instant.now());
                outboxEvent.setLastError(null);
                outboxEventRepository.save(outboxEvent);

                log.info(
                        "Outbox Relay [profile-service] -> Published eventId={} for documentId={} to Kafka",
                        outboxEvent.getId(),
                        outboxEvent.getAggregateId());
            } catch (Exception e) {
                int nextRetry = outboxEvent.getRetryCount() + 1;
                outboxEvent.setRetryCount(nextRetry);
                outboxEvent.setLastError(e.getMessage());

                if (nextRetry >= MAX_RETRY_COUNT) {
                    outboxEvent.setStatus(OutboxStatus.FAILED);
                    log.error(
                            "Outbox eventId={} in profile-service reached max retries ({}), marked FAILED",
                            outboxEvent.getId(),
                            MAX_RETRY_COUNT,
                            e);
                } else {
                    log.warn(
                            "Outbox Relay [profile-service] failed to publish eventId={} (retry {}/{}): {}",
                            outboxEvent.getId(),
                            nextRetry,
                            MAX_RETRY_COUNT,
                            e.getMessage());
                }
                outboxEventRepository.save(outboxEvent);
            }
        }
    }
}
