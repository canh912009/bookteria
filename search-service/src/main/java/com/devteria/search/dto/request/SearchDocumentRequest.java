package com.devteria.search.dto.request;

import java.time.Instant;
import java.util.Set;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchDocumentRequest {
    @NotBlank(message = "ID_REQUIRED")
    String id;

    @NotBlank(message = "TYPE_REQUIRED")
    String type;

    @NotBlank(message = "TITLE_REQUIRED")
    String title;

    String content;
    String authorId;
    String authorName;
    Set<String> tags;
    String thumbnailUrl;
    Instant createdAt;
    Instant updatedAt;
}
