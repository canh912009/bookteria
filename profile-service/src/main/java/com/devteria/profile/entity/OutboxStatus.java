package com.devteria.profile.entity;

/**
 * Trạng thái của sự kiện Outbox trong Neo4j:
 * - PENDING: Mới được ghi cùng transaction với UserProfile, đang chờ gửi lên Kafka.
 * - SENT: Đã được gửi lên Kafka và nhận ACK thành công.
 * - FAILED: Đã thử gửi lại quá số lần tối đa cho phép.
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
