package com.devteria.search.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.search.dto.PageResponse;
import com.devteria.search.dto.request.SearchDocumentRequest;
import com.devteria.search.dto.response.SearchDocumentResponse;
import com.devteria.search.entity.SearchDocument;
import com.devteria.search.exception.AppException;
import com.devteria.search.exception.ErrorCode;
import com.devteria.search.repository.SearchDocumentRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Tầng xử lý nghiệp vụ tìm kiếm (Business Logic Layer) của search-service.
 * Sử dụng kết hợp:
 * - SearchDocumentRepository: cho các thao tác CRUD theo ID đơn giản và đồng bộ Idempotent từ Kafka Outbox Event.
 * - ElasticsearchOperations + NativeQuery: cho truy vấn tìm kiếm nâng cao (multi-match, fuzzy search, filter theo type, phân trang).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SearchService {
    SearchDocumentRepository searchDocumentRepository;
    ElasticsearchOperations elasticsearchOperations;

    /**
     * Đánh chỉ mục (Index) mới hoặc cập nhật (Upsert) một tài liệu vào Elasticsearch qua REST API.
     */
    public SearchDocumentResponse index(SearchDocumentRequest request) {
        Instant existingCreatedAt = searchDocumentRepository
                .findById(request.getId())
                .map(SearchDocument::getCreatedAt)
                .orElse(null);

        SearchDocument document = toDocument(request, existingCreatedAt);
        SearchDocument savedDocument = searchDocumentRepository.save(document);
        log.info("Indexed search document id={} with type={}", savedDocument.getId(), savedDocument.getType());

        return toResponse(savedDocument);
    }

    /**
     * Xử lý sự kiện đồng bộ từ Transactional Outbox gửi qua Kafka.
     *
     * Đảm bảo tính Idempotent ( nhận event dù trùng nhiều lần thì vẫn chỉ xử lý một lần.) & Chống ghi đè ngược (Out-of-order protection):
     * - Cơ chế Outbox + Kafka đảm bảo At-Least-Once Delivery (một sự kiện có thể được gửi lại nếu mạng chập chờn).
     * - Hàm này kiểm tra trường `sourceVersion` hiện có trong Elasticsearch:
     *   + Nếu bản ghi trong ES đã có `sourceVersion > event.version` -> bỏ qua vì đây là bản tin cũ đến muộn.
     *   + Nếu `action == "DELETE"` -> xóa tài liệu theo `documentId` (xóa nhiều lần cùng 1 ID vẫn an toàn).
     *   + Nếu `action == "UPSERT"` -> ghi đè tài liệu theo `documentId` và lưu `sourceVersion = event.version`.
     */
    public void consumeSearchIndexEvent(SearchIndexEvent event) {
        if (event == null || !StringUtils.hasText(event.getDocumentId())) {
            log.warn("Ignored invalid SearchIndexEvent with empty documentId: {}", event);
            return;
        }

        String docId = event.getDocumentId().trim();
        String action = StringUtils.hasText(event.getAction())
                ? event.getAction().trim().toUpperCase(Locale.ROOT)
                : "UPSERT";

        if ("DELETE".equals(action)) {
            searchDocumentRepository.deleteById(docId);
            log.info("Processed Outbox DELETE eventId={} for documentId={}", event.getEventId(), docId);
            return;
        }

        SearchDocument existingDoc = searchDocumentRepository.findById(docId).orElse(null);
        if (existingDoc != null
                && existingDoc.getSourceVersion() != null
                && event.getVersion() != null
                && existingDoc.getSourceVersion() > event.getVersion()) {
            log.info(
                    "Skipped stale Outbox eventId={} for documentId={} (existingVersion={} > eventVersion={})",
                    event.getEventId(),
                    docId,
                    existingDoc.getSourceVersion(),
                    event.getVersion());
            return;
        }

        Instant now = Instant.now();
        Instant createdAt = event.getCreatedAt() != null
                ? event.getCreatedAt()
                : (existingDoc != null && existingDoc.getCreatedAt() != null ? existingDoc.getCreatedAt() : now);

        SearchDocument document = SearchDocument.builder()
                .id(docId)
                .type(
                        StringUtils.hasText(event.getType())
                                ? event.getType().trim().toLowerCase(Locale.ROOT)
                                : "other")
                .title(StringUtils.hasText(event.getTitle()) ? event.getTitle().trim() : docId)
                .content(event.getContent())
                .authorId(event.getAuthorId())
                .authorName(event.getAuthorName())
                .tags(event.getTags())
                .thumbnailUrl(event.getThumbnailUrl())
                .createdAt(createdAt)
                .updatedAt(event.getUpdatedAt() != null ? event.getUpdatedAt() : now)
                .sourceVersion(event.getVersion() != null ? event.getVersion() : now.toEpochMilli())
                .build();

        searchDocumentRepository.save(document);
        log.info(
                "Processed Outbox UPSERT eventId={} -> indexed documentId={} (version={})",
                event.getEventId(),
                docId,
                document.getSourceVersion());
    }

    /**
     * Lấy chi tiết một tài liệu tìm kiếm theo ID.
     * Ném lỗi DOCUMENT_NOT_FOUND (HTTP 404) nếu không tìm thấy trong Elasticsearch.
     */
    public SearchDocumentResponse getById(String id) {
        return searchDocumentRepository
                .findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /**
     * Xóa một tài liệu khỏi chỉ mục Elasticsearch theo ID.
     */
    public void deleteById(String id) {
        searchDocumentRepository.deleteById(id);
        log.info("Deleted search document id={}", id);
    }

    /**
     * Tìm kiếm toàn văn (Full-Text Search) kết hợp lọc theo phân loại (type) và phân trang.
     */
    public PageResponse<SearchDocumentResponse> search(String keyword, String type, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        String normalizedType = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";

        Pageable pageable = StringUtils.hasText(normalizedKeyword)
                ? PageRequest.of(currentPage - 1, pageSize)
                : PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> {
                    if (StringUtils.hasText(normalizedKeyword)) {
                        bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                                .query(normalizedKeyword)
                                .fields("title^3", "content", "authorName", "tags")
                                .operator(Operator.Or)
                                .fuzziness("AUTO")));
                    } else {
                        bool.must(must -> must.matchAll(matchAll -> matchAll));
                    }

                    if (StringUtils.hasText(normalizedType)) {
                        bool.filter(
                                filter -> filter.term(term -> term.field("type").value(normalizedType)));
                    }

                    return bool;
                }))
                .withPageable(pageable);

        SearchHits<SearchDocument> searchHits =
                elasticsearchOperations.search(queryBuilder.build(), SearchDocument.class);
        List<SearchDocumentResponse> data = searchHits.stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();

        long totalElements = searchHits.getTotalHits();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        return PageResponse.<SearchDocumentResponse>builder()
                .currentPage(currentPage)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .data(data)
                .build();
    }

    private SearchDocument toDocument(SearchDocumentRequest request, Instant existingCreatedAt) {
        Instant now = Instant.now();
        Instant createdAt = request.getCreatedAt() != null
                ? request.getCreatedAt()
                : (existingCreatedAt != null ? existingCreatedAt : now);

        return SearchDocument.builder()
                .id(request.getId().trim())
                .type(request.getType().trim().toLowerCase(Locale.ROOT))
                .title(request.getTitle().trim())
                .content(request.getContent())
                .authorId(request.getAuthorId())
                .authorName(request.getAuthorName())
                .tags(request.getTags())
                .thumbnailUrl(request.getThumbnailUrl())
                .createdAt(createdAt)
                .updatedAt(request.getUpdatedAt() != null ? request.getUpdatedAt() : now)
                .sourceVersion(now.toEpochMilli())
                .build();
    }

    private SearchDocumentResponse toResponse(SearchDocument document) {
        return SearchDocumentResponse.builder()
                .id(document.getId())
                .type(document.getType())
                .title(document.getTitle())
                .content(document.getContent())
                .authorId(document.getAuthorId())
                .authorName(document.getAuthorName())
                .tags(document.getTags())
                .thumbnailUrl(document.getThumbnailUrl())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
