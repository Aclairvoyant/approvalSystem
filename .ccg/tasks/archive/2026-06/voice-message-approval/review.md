# Review

## Verification

- `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`: PASS, 18 tests, 0 failures.
- `cd frontend && npm run build:check`: PASS, `vue-tsc --noEmit` and Vite build completed. Existing Sass legacy API and chunk-size warnings remain.
- `git diff --check`: PASS.

## Dual-Model Review

### Claude reviewer

- Critical: none.
- Warning: OSS attachment URLs are permanent public URLs; this is existing attachment architecture risk, but voice content raises sensitivity.
- Warning: concurrent transcript requests can call MiMo more than once before cache is written.
- Warning: detail endpoint performs a duplicate lookup.
- Info: MiMo API key is not exposed; migration/entity/database SQL are aligned; attachment permission checks are materially improved.

### Codex reviewer

- Critical: none.
- Warning: transcription download should not trust arbitrary `fileUrl` values from the database.
- Warning: voice creation should explicitly reject non-WAV or oversized files before creating the application.
- Warning: OSS cleanup is not transactional if upload succeeds but DB write fails; this is consistent with existing attachment behavior.
- Warning: transcription currently performs external download/MiMo call inside a transaction.
- Info: permissions, MiMo key handling, DB migration, and ordinary application default type are generally sound.

## Fixes After Review

- Added backend validation so voice applications accept only WAV audio and reject oversized files when `aliyun.oss.max-file-size` is configured.
- Added trusted bucket URL validation before downloading stored voice audio for transcription, reducing SSRF risk from corrupted attachment URLs.
- Added service tests for non-WAV rejection and untrusted voice URL rejection.
- Re-ran backend tests, frontend build check, and diff check after fixes.

## Accepted Follow-Ups

- Public/permanent OSS attachment URLs should be replaced by short-lived signed URLs for sensitive attachments, especially voice content.
- Concurrent transcript requests may duplicate MiMo calls; a later optimization can use row locking or a compare-and-set update.
- Existing attachment upload cleanup is not fully transactional with OSS and can be improved separately.
