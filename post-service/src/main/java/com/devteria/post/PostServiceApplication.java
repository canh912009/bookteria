package com.devteria.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi chạy post-service.
 * Bật @EnableScheduling để chạy tiến trình nền OutboxRelayService quét bảng outbox_event định kỳ.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class PostServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostServiceApplication.class, args);
    }
}
