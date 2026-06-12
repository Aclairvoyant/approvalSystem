# Review Notes

## Scope

- Added approval/rejection voice replies that can be sent with the decision email notification.
- Added asynchronous upload handling for voice applications so the applicant does not need to wait for audio upload before the draft is created.
- Added upload status and retry support across backend and mobile frontend.

## Review Status

- Initial double-model review was completed.
- Critical findings from the initial review were fixed:
  - `OssUtils.uploadBytes` now tolerates a null `maxFileSize`.
  - Async voice upload completion re-checks application state before promoting the draft.
  - OSS MIME validation no longer uses substring matching.
- A second review pass was explicitly skipped per the latest instruction: "跳过审查".

## Verification

- Backend: `.\mvnw.cmd -f backend\pom.xml test`
  - Result: passed, 41 tests, 0 failures.
- Frontend lint: `npm run lint`
  - Result: passed.
- Frontend build/type check: `npm run build:check`
  - Result: passed.
  - Notes: existing Sass legacy API and chunk-size warnings were reported by the build.
