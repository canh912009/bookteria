package com.devteria.post.entity;

/**
 * Trạng thái của sự kiện trong bảng Outbox:
 * - PENDING: Mới được ghi cùng transaction với dữ liệu chính, đang chờ gửi lên Kafka.
 * - SENT: Đã được gửi lên Kafka và nhận ACK thành công từ Broker.
 * - FAILED: Đã thử gửi lại quá số lần tối đa cho phép.
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
