package com.devteria.post.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.devteria.post.entity.OutboxEvent;
import com.devteria.post.entity.OutboxStatus;

/**
 * Repository truy vấn collection "outbox_event" trong MongoDB.
 */
@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {
    /**
     * Lấy tối đa 50 sự kiện đang ở trạng thái PENDING theo thứ tự thời gian tạo tăng dần (FIFO)
     * để đảm bảo sự kiện xảy ra trước được đẩy lên Kafka trước.
     */
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Xóa các sự kiện đã gửi thành công (SENT) cũ hơn một mốc thời gian để tránh phình to DB.
     */
    void deleteByStatusAndProcessedAtBefore(OutboxStatus status, Instant before);
}
