# Frontend Web Skill

Use this when changing `web-app`.

## Workflow

1. Read:
   - `web-app/package.json`
   - relevant files in `src/pages`, `src/components`, `src/services`, and `src/configurations`

2. Keep API logic in `src/services`.
3. Keep backend URL/config values in `src/configurations`.
4. Use existing MUI style and component patterns.
5. Avoid duplicating axios setup.
6. For auth-sensitive features, check `localStorageService` and `authenticationService`.

## Verification

Run:

```sh
npm run build
```

Use `npm test` when changing tested behavior.

## Notes

- Keep page-level workflow in `src/pages`.
- Keep reusable UI in `src/components`.
- Do not hardcode backend endpoint roots in components.
