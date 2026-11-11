package com.devteria.search.dto.request;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * DTO nhận dữ liệu đầu vào khi đánh chỉ mục (index) hoặc cập nhật một tài liệu tìm kiếm.
 * Các trường bắt buộc (id, type, title) được kiểm tra tự động bằng @NotBlank.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchDocumentRequest {
    // ID duy nhất của tài liệu (bắt buộc) - khớp với ErrorCode.ID_REQUIRED nếu bỏ trống
    @NotBlank(message = "ID_REQUIRED")
    String id;

    // Phân loại tài liệu: "book", "post", "user"... (bắt buộc)
    @NotBlank(message = "TYPE_REQUIRED")
    String type;

    // Tiêu đề hiển thị chính của tài liệu (bắt buộc)
    @NotBlank(message = "TITLE_REQUIRED")
    String title;

    // Nội dung chi tiết để tìm kiếm toàn văn
    String content;

    // ID và tên của tác giả/người tạo
    String authorId;
    String authorName;

    // Tập hợp các từ khóa / thẻ phân loại
    Set<String> tags;

    // Ảnh thu nhỏ hoặc avatar hiển thị trên giao diện kết quả tìm kiếm
    String thumbnailUrl;

    // Thời gian tạo và cập nhật (nếu không truyền, hệ thống sẽ tự gán thời gian hiện tại)
    Instant createdAt;
    Instant updatedAt;
}
