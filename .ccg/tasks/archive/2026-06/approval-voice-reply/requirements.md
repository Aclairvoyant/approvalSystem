# Requirements

## User Request

Add support for sending a voice reply when an approver approves or rejects an application.
The approval or rejection must still trigger an email notification to the other party.

After confirming the first design, the user also asked to inspect the existing voice application upload flow and make voice application submission not wait for the audio upload to finish when the network is slow.

## Current Context

- Voice applications already exist via `POST /api/applications/voice`.
- Approval attachments already exist via `approval_attachments` and `/api/attachments/approval/{applicationId}`.
- Approval/rejection currently use JSON body `ApplicationApprovalRequest` with only `approvalDetail`.
- Approval/rejection email notifications are sent in `ApplicationServiceImpl.approveApplication` and `rejectApplication`.
- Mobile approval detail already displays approval attachments, but audio approval attachments are treated as generic files.
- Existing voice application creation is synchronous: frontend submits the WAV blob to `POST /api/applications/voice`, and backend creates the application then uploads the audio before returning.
- Existing `createVoiceApplication` does not currently share the normal application email-notification block.

## Constraints

- Reuse existing approval attachment storage where possible.
- Do not block approval/rejection if email sending fails; preserve current behavior.
- Keep applicant and approver authorization checks.
- Add tests before production code changes.
- For asynchronous voice application upload, avoid presenting an application as fully playable until the audio attachment exists, and avoid sending a misleading email before the voice upload outcome is known unless the UI explicitly communicates "audio uploading".
