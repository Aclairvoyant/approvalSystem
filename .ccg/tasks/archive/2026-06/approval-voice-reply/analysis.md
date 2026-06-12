# Analysis Summary

## Decision

Use a single approve/reject submission that optionally includes a voice reply file.

## Why

Both codex and Claude analysis rejected a separate "upload voice reply first, approve later" workflow because it can leave orphan approval attachments and does not prove the voice belongs to the final approve/reject operation. The existing `approval_attachments.operation_log_id` field should be used to bind the voice reply to the created approve/reject operation log.

## Recommended Shape

- Keep existing JSON approve/reject behavior for compatibility.
- Add multipart approve/reject handling with `approvalDetail` and optional `voiceFile`.
- Change operation-log creation helper to return the inserted `OperationLog`.
- Add an approval-attachment upload path that accepts `operationLogId`.
- Validate submitted voice replies as WAV, using the same voice constraints as voice applications.
- Upload and persist the voice reply before email notification; if voice upload fails, the approval/rejection should fail.
- Keep email notification non-fatal once the approval/rejection and optional voice attachment have succeeded.
- Update the mobile approval detail UI to record/preview/cancel a voice reply and display approval audio attachments.

## Risks To Address

- JSON-to-multipart compatibility for existing callers.
- OSS upload is not transactionally rolled back if a later DB operation fails.
- Existing approval attachment audio is currently filtered out of the detail display.
- Server-side MIME validation must not trust only browser-provided content type.
- Existing admin approve/reject endpoints should remain text-only unless explicitly extended.

## Async Voice Application Upload Analysis

The existing voice application flow blocks on both the browser-to-backend multipart upload and the backend-to-OSS upload. A backend-only `@Async` change can remove the OSS wait from the request path, but it cannot remove the time needed for the browser to send the audio bytes to the backend.

Recommended direction:

- Add explicit voice upload status to `applications`, for example `voice_status`: `0=not voice`, `1=uploading`, `2=ready`, `3=failed`.
- Keep voice applications non-approvable / clearly "uploading" until the audio attachment exists. The safer backend shape is to avoid notifying the approver until voice audio is successfully uploaded and attached.
- Minimal implementation option: keep `POST /applications/voice` multipart, validate and create the application quickly, copy the `MultipartFile` bytes in the request thread, return after scheduling an async backend-to-OSS upload, and update `voice_status` from the async worker.
- More complete two-phase option: create a voice application draft with JSON first, then upload audio via a second request and let the UI close immediately after draft creation while the upload continues in the background. This gives the best perceived UX but needs retry handling when the browser upload is interrupted.
- Long-term scalable option: direct-to-OSS presigned upload plus finalize endpoint. This is the only option that avoids the slow mobile upload going through the backend, but it is a larger security/CORS/API change.

Recommended for this task:

- Use the minimal safe A+B path: backend async OSS upload plus optimistic frontend state.
- Add `voice_status` and only send the approver email when the async upload succeeds.
- Surface `voice_status` in list/detail pages so applicants see uploading/failed/ready, and so approvers do not act on a voice application before audio is ready.
- If `voice_status=failed`, keep the application visible to the applicant with a retry affordance.
