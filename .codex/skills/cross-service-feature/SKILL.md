# Cross-Service Feature Skill

Use this when a feature affects multiple services or both backend and frontend.

## Checklist

1. Identify owner service:
   - auth/user account: `identity-service`
   - profile data: `profile-service`
   - posts: `post-service`
   - chat: `chat-service`
   - notification/email: `notification-service`
   - files/uploads: `file-service`

2. Check API gateway route impact.

3. Check frontend service modules in `web-app/src/services`.

4. Keep contracts explicit:
   - request DTO
   - response DTO
   - error codes
   - validation rules

5. If events are involved:
   - check Kafka producer/consumer DTOs
   - keep event payload backward compatible where possible

6. Verify each touched layer:
   - backend service tests/build
   - frontend build if UI changed

## Notes

- Avoid changing multiple service contracts at once unless the feature requires it.
- Prefer additive API changes over breaking changes.
- Update gateway routes when a new route group must be exposed to clients.
