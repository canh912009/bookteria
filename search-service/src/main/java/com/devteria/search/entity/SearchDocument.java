package com.devteria.search.entity;

import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "bookteria-search")
public class SearchDocument {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String type;

    @Field(type = FieldType.Text)
    String title;

    @Field(type = FieldType.Text)
    String content;

    @Field(type = FieldType.Keyword)
    String authorId;

    @Field(type = FieldType.Text)
    String authorName;

    @Field(type = FieldType.Keyword)
    Set<String> tags;

    @Field(type = FieldType.Keyword, index = false)
    String thumbnailUrl;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    Instant updatedAt;
}
