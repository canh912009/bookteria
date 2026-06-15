package com.devteria.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.devteria.search.dto.PageResponse;
import com.devteria.search.dto.request.SearchDocumentRequest;
import com.devteria.search.dto.response.SearchDocumentResponse;
import com.devteria.search.entity.SearchDocument;
import com.devteria.search.repository.SearchDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchService {
    SearchDocumentRepository searchDocumentRepository;
    ElasticsearchOperations elasticsearchOperations;

    public SearchDocumentResponse index(SearchDocumentRequest request) {
        SearchDocument document = toDocument(request);
        return toResponse(searchDocumentRepository.save(document));
    }

    public SearchDocumentResponse getById(String id) {
        return searchDocumentRepository.findById(id).map(this::toResponse).orElse(null);
    }

    public void deleteById(String id) {
        searchDocumentRepository.deleteById(id);
    }

    public PageResponse<SearchDocumentResponse> search(String keyword, String type, int page, int size) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        String normalizedType = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";

        NativeQuery query = NativeQuery.builder()
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
                        bool.filter(filter -> filter.term(term -> term.field("type").value(normalizedType)));
                    }

                    return bool;
                }))
                .withPageable(pageable)
                .build();

        SearchHits<SearchDocument> searchHits = elasticsearchOperations.search(query, SearchDocument.class);
        List<SearchDocumentResponse> data = searchHits.stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();

        long totalElements = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return PageResponse.<SearchDocumentResponse>builder()
                .currentPage(currentPage)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .data(data)
                .build();
    }

    private SearchDocument toDocument(SearchDocumentRequest request) {
        Instant now = Instant.now();

        return SearchDocument.builder()
                .id(request.getId())
                .type(request.getType().toLowerCase(Locale.ROOT))
                .title(request.getTitle())
                .content(request.getContent())
                .authorId(request.getAuthorId())
                .authorName(request.getAuthorName())
                .tags(request.getTags())
                .thumbnailUrl(request.getThumbnailUrl())
                .createdAt(request.getCreatedAt() != null ? request.getCreatedAt() : now)
                .updatedAt(request.getUpdatedAt() != null ? request.getUpdatedAt() : now)
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
