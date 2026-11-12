package com.devteria.post.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Cấu hình Transaction Manager cho MongoDB (yêu cầu MongoDB chạy chế độ Replica Set rs0).
 * Đánh dấu @Primary để tránh xung đột nếu có nhiều TransactionManager trên classpath.
 */
@Configuration
public class MongoTransactionConfig {

	@Bean
	@Primary
	MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
		return new MongoTransactionManager(dbFactory);
	}
}
