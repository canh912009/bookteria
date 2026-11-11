package com.devteria.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi chạy chính (Main Application) của microservice search-service.
 * Dịch vụ này chạy trên cổng 8086 với context-path "/search" và kết nối tới Elasticsearch.
 */
@SpringBootApplication
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
