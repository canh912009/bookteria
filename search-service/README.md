# Search Service

Search service owns the Elasticsearch index used for full-text search across Bookteria resources.

## Local dependencies

- Elasticsearch: `http://localhost:9200`
- Service port: `8086`
- Context path: `/search`

Start shared infrastructure from the repository root:

```sh
docker compose up -d elasticsearch
```

Run the service:

```sh
./mvnw spring-boot:run
```

On Windows:

```sh
mvnw.cmd spring-boot:run
```

## Gateway routes

Gateway exposes the service through:

```text
/api/v1/search/**
```

## API

Index or update a searchable document:

```http
POST /search/documents
```

Search documents:

```http
GET /search?q=dune&type=book&page=1&size=10
```

Delete a document from the search index:

```http
DELETE /search/documents/{id}
```

Elasticsearch stores a denormalized search document only. The source services remain the source of truth.
