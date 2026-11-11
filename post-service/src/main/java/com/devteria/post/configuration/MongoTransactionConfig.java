package com.devteria.post.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Cấu hình Transaction Manager cho MongoDB (yêu cầu MongoDB chạy chế độ Replica Set rs0).
 * Nhờ có MongoTransactionManager, annotation @Transactional trên PostService.createPost()
 * sẽ đảm bảo việc lưu document "post" và lưu document "outbox_event" diễn ra nguyên tử (Atomic):
 * - Hoặc cả hai cùng commit thành công vào MongoDB.
 * - Hoặc cả hai cùng rollback nếu có bất kỳ lỗi nào xảy ra.
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
