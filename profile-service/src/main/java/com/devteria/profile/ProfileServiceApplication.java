package com.devteria.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi chạy profile-service.
 * - Bật @EnableScheduling để chạy tiến trình nền OutboxRelayService quét các node outbox_event trong Neo4j.
 * - Loại trừ Neo4jReactiveDataAutoConfiguration (do reactor-core từ OpenFeign kéo vào) để tránh xung đột
 *   2 bean TransactionManager (transactionManager vs reactiveTransactionManager) khi dùng @Transactional.
 */
@SpringBootApplication(
        exclude = {Neo4jReactiveDataAutoConfiguration.class, Neo4jReactiveRepositoriesAutoConfiguration.class})
@EnableFeignClients
@EnableScheduling
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}
