package com.devteria.search.entity;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Thực thể ánh xạ (Document Entity) lưu trữ trong chỉ mục (Index) của Elasticsearch.
 * Tên index là "bookteria-search", đóng vai trò lưu bản sao phi chuẩn hóa (denormalized)
 * của các tài nguyên trong hệ thống (bài viết post, người dùng user, sách book...) để tìm kiếm toàn văn (Full-Text Search).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "bookteria-search")
public class SearchDocument {
    /**
     * Định danh duy nhất của tài liệu trong Elasticsearch (ví dụ: "post-123", "user-456", "book-789").
     */
    @Id
    String id;

    /**
     * Phân loại tài nguyên (ví dụ: "book", "post", "user").
     * Dùng FieldType.Keyword để lọc chính xác (exact match filter) theo phân loại.
     */
    @Field(type = FieldType.Keyword)
    String type;

    /**
     * Tiêu đề tài liệu (tên sách, tiêu đề bài viết, hoặc username).
     * Dùng FieldType.Text để Elasticsearch tách từ (tokenize) phục vụ tìm kiếm toàn văn.
     */
    @Field(type = FieldType.Text)
    String title;

    /**
     * Nội dung chi tiết cần tìm kiếm (nội dung bài viết, mô tả sách, thông tin giới thiệu user).
     */
    @Field(type = FieldType.Text)
    String content;

    /**
     * ID của tác giả hoặc chủ sở hữu tài liệu (lưu dạng Keyword để tra cứu chính xác).
     */
    @Field(type = FieldType.Keyword)
    String authorId;

    /**
     * Tên hiển thị của tác giả (dùng FieldType.Text để người dùng có thể tìm theo tên tác giả).
     */
    @Field(type = FieldType.Text)
    String authorName;

    /**
     * Danh sách các nhãn/thẻ (tags) gắn với tài liệu.
     */
    @Field(type = FieldType.Keyword)
    Set<String> tags;

    /**
     * Đường dẫn ảnh đại diện / ảnh thu nhỏ.
     * Đặt index = false vì không cần tìm kiếm text trên URL ảnh, giúp tiết kiệm bộ nhớ index.
     */
    @Field(type = FieldType.Keyword, index = false)
    String thumbnailUrl;

    /**
     * Thời điểm tạo tài liệu (định dạng chuẩn ISO-8601 date_time).
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    Instant createdAt;

    /**
     * Thời điểm cập nhật tài liệu gần nhất.
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    Instant updatedAt;

    /**
     * Phiên bản sự kiện gốc từ Outbox (epoch millis).
     * Giúp Consumer kiểm tra tính Idempotent: chỉ cập nhật khi event.version >= sourceVersion hiện tại.
     */
    @Field(type = FieldType.Long)
    Long sourceVersion;
}
