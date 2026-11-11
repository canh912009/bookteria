package com.devteria.search.configuration;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

/**
 * Cấu hình cơ chế Retry và Dead Letter Topic (DLT) cho Kafka Consumer tại search-service.
 *
 * Nguyên lý hoạt động:
 * 1. Khi @KafkaListener xử lý 1 message bị lỗi (ví dụ Elasticsearch tạm thời quá tải hoặc mất kết nối),
 *    DefaultErrorHandler sẽ tự động thử lại (Retry) tối đa 3 lần, mỗi lần cách nhau 2 giây (2000ms).
 * 2. Nếu sau 3 lần thử lại vẫn thất bại, DeadLetterPublishingRecoverer sẽ tự động đẩy message đó
 *    sang topic "search-index-events.DLT" để lưu vết và xử lý sau, không làm kẹt hàng đợi chính.
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> {
            log.error(
                    "Message failed after retries, publishing to Dead Letter Topic: key={}, error={}",
                    record.key(),
                    ex.getMessage());
            return new TopicPartition(record.topic() + ".DLT", record.partition());
        });

        // Thử lại tối đa 3 lần, khoảng nghỉ giữa mỗi lần là 2000ms (2 giây)
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
