# Bookteria Agent Guide

## Project Overview

Bookteria is a book social network built as a microservice monorepo.

## Communication Style

- When replying to the user in Vietnamese, use `Em` as the agent's self-pronoun.
- Keep explanations direct, practical, and concise.
- Prefer concrete references to files, commands, and behavior over generic advice.

Main components:
- `api-gateway`: Spring Cloud Gateway, routes `/api/v1/**`
- `identity-service`: authentication and identity, MySQL, Kafka producer
- `profile-service`: user profile service
- `post-service`: post service, MongoDB
- `chat-service`: chat service, MongoDB, Socket.IO
- `notification-service`: notification and email service
- `file-service`: file upload/storage service
- `web-app`: React 18, CRA, MUI, axios, socket.io-client
- `mobile-app`, `book-service`, `search-service`, `iac`: present but less documented

## Tech Stack

Backend:
- Java 21
- Spring Boot / Spring Cloud
- Maven
- Lombok
- MapStruct
- Spotless / Palantir Java Format
- JaCoCo where configured

Frontend:
- React 18
- react-scripts
- MUI
- React Router
- Axios
- socket.io-client

Infrastructure:
- Docker Compose
- Kafka on `localhost:9094`
- MySQL on `localhost:3306`
- MongoDB on `localhost:27017`
- Neo4j on `localhost:7474` and `localhost:7687`

## Commands

Start shared infrastructure:

```sh
docker compose up -d
```

Run a backend service:

```sh
cd <service-name>
mvn spring-boot:run
```

Test a backend service:

```sh
cd <service-name>
mvn test
```

Build a backend service:

```sh
cd <service-name>
mvn package
```

Run web app:

```sh
cd web-app
npm install
npm start
```

Build web app:

```sh
cd web-app
npm run build
```

Test web app:

```sh
cd web-app
npm test
```

## Backend Conventions

- Keep changes scoped to the affected service unless the API contract crosses service boundaries.
- Follow existing package layout:
  - `controller`
  - `service`
  - `repository`
  - `repository/httpclient`
  - `dto/request`
  - `dto/response`
  - `mapper`
  - `entity`
  - `configuration`
  - `exception`
- Use Lombok and MapStruct consistently with existing code.
- Use `ApiResponse<T>` style where already used.
- Add validation annotations to request DTOs where input rules exist.
- Keep controller logic thin; business logic belongs in service classes.
- For inter-service calls, prefer existing OpenFeign client patterns.
- For JWT/security changes, check all services that define `SecurityConfig`, `CustomJwtDecoder`, and `JwtAuthenticationEntryPoint`.

## Frontend Conventions

- Keep API calls inside `src/services`.
- Keep shared axios/base config inside `src/configurations`.
- Use MUI components where suitable.
- Keep pages in `src/pages`, reusable UI in `src/components`.
- Do not hardcode backend URLs in components; use configuration/service modules.

## API Gateway Rules

- Public route prefix is `/api/v1`.
- Gateway strips first two path segments before forwarding.
- When adding a new backend route group, update gateway config if it must be reachable from frontend/mobile clients.

## Verification Expectations

For backend changes:
- Run `mvn test` in the touched service when practical.
- For formatting-sensitive Java changes, run Maven compile/package because Spotless may be wired into the build.

For frontend changes:
- Run `npm run build` for UI code changes when practical.
- Run focused tests if test files are affected.

## Safety Notes

- Do not commit secrets or real credentials.
- Existing local dev credentials in YAML are for development only.
- Do not rewrite unrelated service structure during focused changes.
- Do not modify generated/build output such as `target`, `build`, or `node_modules`.
