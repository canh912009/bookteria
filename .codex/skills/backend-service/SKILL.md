# Backend Service Skill

Use this when changing a Spring Boot service in Bookteria.

## Workflow

1. Identify the target service and read:
   - `pom.xml`
   - `src/main/resources/application.yaml`
   - related controller, service, repository, DTO, mapper, entity, and exception files

2. Preserve local architecture:
   - controller handles HTTP only
   - service owns business logic
   - repository owns persistence
   - mapper converts entity/DTO
   - exceptions use existing `AppException`, `ErrorCode`, and `GlobalExceptionHandler` pattern if present

3. For a new endpoint:
   - add request/response DTOs
   - add validation annotations
   - add controller method
   - add service method
   - add repository method if needed
   - update mapper if entity/DTO conversion is needed
   - update API gateway route only if adding a new route group

4. For cross-service calls:
   - use existing OpenFeign patterns under `repository/httpclient`
   - keep URLs in `application.yaml` under `app.services`

5. Verify:
   - run `mvn test`
   - if tests are not available or fail due local infrastructure, report that clearly

## Notes

- Keep changes scoped to one service unless the request explicitly crosses service boundaries.
- Do not introduce a new persistence or HTTP client style without a clear reason.
- Keep DTO contracts explicit and avoid leaking entity classes through controllers.
