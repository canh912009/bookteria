# Local Dev Stack Skill

Use this when the task involves running Bookteria locally.

## Infrastructure

Start dependencies from repo root:

```sh
docker compose up -d
```

Services exposed locally:
- Kafka: `localhost:9094`
- MongoDB: `localhost:27017`
- MySQL: `localhost:3306`
- Neo4j browser: `localhost:7474`
- Neo4j bolt: `localhost:7687`

## Backend Ports

Known service ports:
- api-gateway: `8888`
- identity-service: `8080`
- profile-service: `8081`
- notification-service: `8082`
- post-service: `8083`
- file-service: `8084`
- chat-service: `8085`

Gateway prefix:
- external API path starts with `/api/v1`

## Workflow

1. Start Docker Compose.
2. Start required backend services with `mvn spring-boot:run`.
3. Start `web-app` with `npm start`.
4. Prefer testing through `api-gateway` unless debugging one service directly.

## Notes

- Some databases may need schemas or seed data created manually depending on the service.
- Existing credentials in local YAML files are development-only values.
