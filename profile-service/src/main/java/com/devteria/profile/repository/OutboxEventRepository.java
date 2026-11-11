package com.devteria.profile.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.devteria.profile.entity.OutboxEvent;
import com.devteria.profile.entity.OutboxStatus;

/**
 * Repository truy vấn các node "outbox_event" trong Neo4j.
 */
@Repository
public interface OutboxEventRepository extends Neo4jRepository<OutboxEvent, String> {
    /**
     * Lấy tối đa 50 sự kiện đang ở trạng thái PENDING theo thứ tự thời gian tạo tăng dần (FIFO).
     */
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
