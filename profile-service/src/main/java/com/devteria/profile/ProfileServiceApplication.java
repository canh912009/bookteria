package com.devteria.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi chạy profile-service.
 * Bật @EnableScheduling để chạy tiến trình nền OutboxRelayService quét các node outbox_event trong Neo4j.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}
