package com.devteria.search.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import com.devteria.search.entity.SearchDocument;

/**
 * Repository giao tiếp trực tiếp với Elasticsearch cho thực thể SearchDocument.
 * Kế thừa ElasticsearchRepository giúp có sẵn các hàm CRUD cơ bản: save, findById, deleteById, findAll...
 */
@Repository
public interface SearchDocumentRepository extends ElasticsearchRepository<SearchDocument, String> {}
