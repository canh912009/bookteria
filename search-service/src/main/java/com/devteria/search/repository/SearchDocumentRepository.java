package com.devteria.search.repository;

import com.devteria.search.entity.SearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchDocumentRepository extends ElasticsearchRepository<SearchDocument, String> {}
