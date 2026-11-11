package com.devteria.post.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.post.entity.OutboxEvent;
import com.devteria.post.entity.OutboxStatus;
import com.devteria.post.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Tiến trình nền (Outbox Polling Relay) của post-service.
 *
 * Cơ chế đảm bảo "Commit xuống DB là chắc chắn đồng bộ qua Elasticsearch":
 * 1. Định kỳ mỗi 2 giây (@Scheduled(fixedDelay = 2000)), tiến trình quét bảng "outbox_event" lấy các bản ghi PENDING.
 * 2. Với mỗi bản ghi, giải tuần tự (deserialize) payload thành SearchIndexEvent và gửi vào Kafka topic "search-index-events"
 *    với key = aggregateId (đảm bảo đúng thứ tự cho cùng 1 bài viết).
 * 3. Chờ Kafka Broker xác nhận đã ghi thành công (.get(5, TimeUnit.SECONDS)) rồi mới đổi trạng thái sang SENT.
 * 4. Nếu Kafka đang tắt hoặc mạng lỗi, bản ghi giữ nguyên trạng thái PENDING để lần quét tiếp theo tự động gửi lại!
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

    /**
     * Quét các sự kiện PENDING mỗi 2 giây và đẩy lên Kafka.
     */
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

                // Gửi lên Kafka với key = aggregateId và chờ ACK từ Broker trước khi đánh dấu SENT
                kafkaTemplate
                        .send(SEARCH_INDEX_TOPIC, outboxEvent.getAggregateId(), eventPayload)
                        .get(5, TimeUnit.SECONDS);

                outboxEvent.setStatus(OutboxStatus.SENT);
                outboxEvent.setProcessedAt(Instant.now());
                outboxEvent.setLastError(null);
                outboxEventRepository.save(outboxEvent);

                log.info(
                        "Outbox Relay [post-service] -> Published eventId={} for documentId={} to Kafka",
                        outboxEvent.getId(),
                        outboxEvent.getAggregateId());
            } catch (Exception e) {
                int nextRetry = outboxEvent.getRetryCount() + 1;
                outboxEvent.setRetryCount(nextRetry);
                outboxEvent.setLastError(e.getMessage());

                if (nextRetry >= MAX_RETRY_COUNT) {
                    outboxEvent.setStatus(OutboxStatus.FAILED);
                    log.error(
                            "Outbox eventId={} reached max retries ({}), marked FAILED",
                            outboxEvent.getId(),
                            MAX_RETRY_COUNT,
                            e);
                } else {
                    log.warn(
                            "Outbox Relay [post-service] failed to publish eventId={} (retry {}/{}): {}",
                            outboxEvent.getId(),
                            nextRetry,
                            MAX_RETRY_COUNT,
                            e.getMessage());
                }
                outboxEventRepository.save(outboxEvent);
            }
        }
    }

    /**
     * Dọn dẹp định kỳ mỗi giờ: xóa các bản ghi Outbox đã gửi thành công (SENT) quá 7 ngày.
     */
    @Scheduled(fixedDelay = 3600000)
    public void cleanupOldSentEvents() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        outboxEventRepository.deleteByStatusAndProcessedAtBefore(OutboxStatus.SENT, sevenDaysAgo);
    }
}
