# Voice Message Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a WeChat/QQ-style voice approval request flow where the applicant records one audio clip, the approver can play it, and either participant can request MiMo ASR transcription.

**Architecture:** Keep ordinary approval requests intact. Add `app_type` and `voice_transcript` to `applications`, store the audio clip as an `application_attachments` OSS object, create voice approvals through a single multipart endpoint, and expose a cached ASR endpoint for the transcript. The mobile UI gets a separate "voice request" path that reuses the existing WAV recorder without forcing title/description extraction.

**Tech Stack:** Spring Boot 3, MyBatis Plus, MockMvc/JUnit 5/Mockito, Vue 3 `<script setup>`, Vant, existing `wavRecorder.ts`, existing MiMo OpenAI-compatible ASR call.

---

## File Structure

- Modify `backend/src/main/java/com/approval/system/entity/Application.java`: add `appType` and `voiceTranscript`.
- Modify `backend/src/main/java/com/approval/system/service/IApplicationService.java`: add voice create/transcribe and participant guard contracts.
- Modify `backend/src/main/java/com/approval/system/service/impl/ApplicationServiceImpl.java`: create voice request, cache transcript, and participant/applicant/approver guards.
- Modify `backend/src/main/java/com/approval/system/service/IVoiceApplicationService.java`: expose raw ASR transcription without field extraction.
- Modify `backend/src/main/java/com/approval/system/service/impl/VoiceApplicationServiceImpl.java`: split ASR from field extraction so voice approvals use ASR only.
- Modify `backend/src/main/java/com/approval/system/controller/ApplicationController.java`: add multipart voice creation and transcript endpoints; secure detail access.
- Modify `backend/src/main/java/com/approval/system/controller/AttachmentController.java`: enforce participant access for attachment list and applicant/approver access for uploads.
- Modify `backend/src/main/resources/application.yml`: allow `audio/wav` uploads.
- Create `backend/src/main/resources/db/migration/V8__add_voice_application_fields.sql`: add `app_type` and `voice_transcript`.
- Modify `database.sql`: keep fresh installs aligned with migration fields and audio upload config assumptions.
- Create `backend/src/test/java/com/approval/system/controller/ApplicationControllerVoiceTest.java`: controller tests for voice create/transcribe contract.
- Create `backend/src/test/java/com/approval/system/service/impl/ApplicationServiceVoiceTest.java`: service tests for voice creation, permission checks, and transcript caching.
- Modify `frontend/src/services/api.ts`: add app type fields, voice creation API, and voice transcription API.
- Modify `frontend/src/pages/mobile/Applications.vue`: add normal/voice create mode, record preview, and submit voice request.
- Modify `frontend/src/pages/mobile/ApplicationDetail.vue`: render audio player, cached transcript, and transcribe button.
- Modify `frontend/src/pages/mobile/Approvals.vue`: mark voice requests in the pending list.

---

## Task 1: Backend Voice Request Contract And Data Model

**Files:**
- Create: `backend/src/test/java/com/approval/system/controller/ApplicationControllerVoiceTest.java`
- Create: `backend/src/test/java/com/approval/system/service/impl/ApplicationServiceVoiceTest.java`
- Create: `backend/src/main/resources/db/migration/V8__add_voice_application_fields.sql`
- Modify: `backend/src/main/java/com/approval/system/entity/Application.java`
- Modify: `backend/src/main/java/com/approval/system/service/IApplicationService.java`
- Modify: `backend/src/main/java/com/approval/system/service/impl/ApplicationServiceImpl.java`
- Modify: `backend/src/main/java/com/approval/system/controller/ApplicationController.java`
- Modify: `database.sql`

- [ ] **Step 1: Write failing controller tests**

Add MockMvc tests for:

```java
@Test
void createVoiceApplicationAcceptsMultipartAudioWithoutTitle() throws Exception {
    setCurrentUser(10L);
    MockMultipartFile audio = new MockMultipartFile(
            "file", "voice.wav", "audio/wav", new byte[] { 1, 2, 3, 4 }
    );
    Application saved = Application.builder()
            .id(99L)
            .applicantId(10L)
            .approverId(20L)
            .title("语音申请")
            .description("语音申请，请播放语音内容")
            .appType(2)
            .status(1)
            .build();

    when(userRelationService.isRelated(10L, 20L)).thenReturn(true);
    when(applicationService.createVoiceApplication(eq(10L), eq(20L), any(MultipartFile.class)))
            .thenReturn(saved);

    mockMvc.perform(multipart("/api/applications/voice")
                    .file(audio)
                    .param("approverId", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(99))
            .andExpect(jsonPath("$.data.appType").value(2))
            .andExpect(jsonPath("$.data.title").value("语音申请"));
}

@Test
void createVoiceApplicationRejectsUnrelatedApprover() throws Exception {
    setCurrentUser(10L);
    MockMultipartFile audio = new MockMultipartFile(
            "file", "voice.wav", "audio/wav", new byte[] { 1, 2, 3, 4 }
    );
    when(userRelationService.isRelated(10L, 20L)).thenReturn(false);

    mockMvc.perform(multipart("/api/applications/voice")
                    .file(audio)
                    .param("approverId", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("只有互为对象的用户才能创建申请"));

    verify(applicationService, never()).createVoiceApplication(anyLong(), anyLong(), any());
}
```

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Dtest=ApplicationControllerVoiceTest test
```

Expected: compilation failure or test failure because `/api/applications/voice`, `appType`, and `createVoiceApplication` do not exist yet.

- [ ] **Step 3: Write failing service tests**

Add service tests for:

```java
@Test
void createVoiceApplicationGeneratesDisplayFieldsAndUploadsAudio() {
    MockMultipartFile audio = new MockMultipartFile(
            "file", "voice.wav", "audio/wav", new byte[] { 1, 2, 3, 4 }
    );
    when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
        Application app = invocation.getArgument(0);
        app.setId(99L);
        return 1;
    });
    when(applicationAttachmentService.uploadApplicationAttachment(99L, audio))
            .thenReturn(ApplicationAttachment.builder().id(7L).applicationId(99L).fileType("audio/wav").build());

    Application result = service.createVoiceApplication(10L, 20L, audio);

    assertThat(result.getAppType()).isEqualTo(2);
    assertThat(result.getTitle()).isEqualTo("语音申请");
    assertThat(result.getDescription()).isEqualTo("语音申请，请播放语音内容");
    verify(applicationAttachmentService).uploadApplicationAttachment(99L, audio);
}

@Test
void assertApplicationParticipantRejectsUnrelatedUser() {
    when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
            .id(99L)
            .applicantId(10L)
            .approverId(20L)
            .build());

    assertThatThrownBy(() -> service.assertApplicationParticipant(99L, 30L))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("无权访问此申请");
}
```

- [ ] **Step 4: Run service tests and verify RED**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Dtest=ApplicationServiceVoiceTest test
```

Expected: compilation failure or test failure because voice service methods and entity fields do not exist yet.

- [ ] **Step 5: Implement data model and voice create path**

Implementation requirements:

```java
@TableField("app_type")
private Integer appType;

@TableField("voice_transcript")
private String voiceTranscript;
```

`createVoiceApplication` must:

- reject null or empty audio with `语音内容为空`
- create an application with `title="语音申请"`, `description="语音申请，请播放语音内容"`, `appType=2`, `status=PENDING`
- save the application before uploading the application attachment
- upload the WAV through `IApplicationAttachmentService.uploadApplicationAttachment`
- write the existing CREATE operation log text as `创建语音申请`

Add `V8__add_voice_application_fields.sql`:

```sql
ALTER TABLE `applications`
  ADD COLUMN `app_type` TINYINT NOT NULL DEFAULT 1 COMMENT '申请类型：1=普通申请，2=语音申请' AFTER `approver_id`,
  ADD COLUMN `voice_transcript` LONGTEXT NULL COMMENT '语音申请ASR转写文本' AFTER `remark`;
```

Update `database.sql` `applications` table with matching columns.

- [ ] **Step 6: Implement controller endpoint and secure detail**

Add:

```java
@PostMapping(value = "/voice", consumes = "multipart/form-data")
public ApiResponse<Application> createVoiceApplication(
        @RequestParam("approverId") Long approverId,
        @RequestParam("file") MultipartFile file)
```

The controller must read current user, verify `userRelationService.isRelated(userId, approverId)`, then call `applicationService.createVoiceApplication`.

Change detail endpoint to call `applicationService.assertApplicationParticipant(id, userId)` before returning data.

- [ ] **Step 7: Run backend task tests and verify GREEN**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Dtest=ApplicationControllerVoiceTest,ApplicationServiceVoiceTest test
```

Expected: both test classes pass.

---

## Task 2: Backend Voice Transcription And Attachment Access

**Files:**
- Modify: `backend/src/test/java/com/approval/system/controller/ApplicationControllerVoiceTest.java`
- Modify: `backend/src/test/java/com/approval/system/service/impl/ApplicationServiceVoiceTest.java`
- Modify: `backend/src/main/java/com/approval/system/service/IVoiceApplicationService.java`
- Modify: `backend/src/main/java/com/approval/system/service/impl/VoiceApplicationServiceImpl.java`
- Modify: `backend/src/main/java/com/approval/system/service/IApplicationService.java`
- Modify: `backend/src/main/java/com/approval/system/service/impl/ApplicationServiceImpl.java`
- Modify: `backend/src/main/java/com/approval/system/controller/ApplicationController.java`
- Modify: `backend/src/main/java/com/approval/system/controller/AttachmentController.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write failing transcript and access tests**

Add controller test:

```java
@Test
void transcribeVoiceApplicationReturnsCachedTranscriptForParticipant() throws Exception {
    setCurrentUser(20L);
    when(applicationService.transcribeVoiceApplication(99L, 20L, "zh"))
            .thenReturn("明天想请假一天");

    mockMvc.perform(post("/api/applications/99/transcribe-voice")
                    .param("language", "zh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value("明天想请假一天"));
}
```

Add service test:

```java
@Test
void transcribeVoiceApplicationUsesCachedTranscriptWithoutCallingMimo() {
    when(applicationMapper.selectById(99L)).thenReturn(Application.builder()
            .id(99L)
            .applicantId(10L)
            .approverId(20L)
            .appType(2)
            .voiceTranscript("已经转写")
            .build());

    String result = service.transcribeVoiceApplication(99L, 20L, "zh");

    assertThat(result).isEqualTo("已经转写");
    verifyNoInteractions(voiceApplicationService);
}
```

- [ ] **Step 2: Run transcript tests and verify RED**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Dtest=ApplicationControllerVoiceTest,ApplicationServiceVoiceTest test
```

Expected: failure because transcript endpoint and service method do not exist.

- [ ] **Step 3: Expose ASR-only service method**

Add to `IVoiceApplicationService`:

```java
String transcribe(byte[] audioBytes, String contentType, String filename, String language);
```

Refactor `VoiceApplicationServiceImpl.parseVoice` so it calls `transcribe(...)` first, then `extractFields(...)`. Keep existing `/voice-parse` behavior unchanged.

- [ ] **Step 4: Implement cached transcription**

`ApplicationServiceImpl.transcribeVoiceApplication` must:

- load application by id
- call `assertApplicationParticipant`
- reject non-voice applications with `不是语音申请`
- return `voiceTranscript` immediately if it has text
- find the newest `application_attachments` row where `file_type` starts with `audio/`
- download `fileUrl` as bytes through existing `RestTemplate`
- call `voiceApplicationService.transcribe(bytes, fileType, fileName, language)`
- save transcript to `applications.voice_transcript`
- return transcript

- [ ] **Step 5: Secure attachment endpoints**

`AttachmentController` must:

- read current user for every application/approval attachment endpoint
- call `applicationService.assertApplicationParticipant(applicationId, userId)` before listing attachments
- call `applicationService.assertApplicationApplicant(applicationId, userId)` before uploading application attachments
- call `applicationService.assertApplicationApprover(applicationId, userId)` before uploading approval attachments
- for delete by attachment id, load the attachment first and verify participant access to its `applicationId`

- [ ] **Step 6: Allow WAV uploads**

Change:

```yaml
allowed-file-types: image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,audio/wav
```

- [ ] **Step 7: Run backend tests and verify GREEN**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -Dtest=ApplicationControllerVoiceTest,ApplicationServiceVoiceTest,AdminControllerTest test
```

Expected: all selected tests pass.

---

## Task 3: Mobile Voice Request UI

**Files:**
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/pages/mobile/Applications.vue`
- Modify: `frontend/src/pages/mobile/ApplicationDetail.vue`
- Modify: `frontend/src/pages/mobile/Approvals.vue`

- [ ] **Step 1: Update frontend API contract**

Add fields:

```ts
export interface Application {
  appType?: number
  voiceTranscript?: string
}
```

Add API methods:

```ts
createVoiceApplication(approverId: number, audio: Blob) {
  const formData = new FormData()
  formData.append('approverId', String(approverId))
  formData.append('file', audio, 'voice.wav')
  return http.post<Application>('/applications/voice', formData)
}

transcribeVoiceApplication(id: number, language: 'auto' | 'zh' | 'en' = 'zh') {
  return http.post<string>(`/applications/${id}/transcribe-voice`, null, { params: { language } })
}
```

- [ ] **Step 2: Add voice mode to create sheet**

In `Applications.vue`:

- add `createMode = ref<'normal' | 'voice'>('normal')`
- keep the existing normal form as-is under `v-if="createMode === 'normal'"`
- add a voice form under `v-else` with approver picker, record/re-record button, `<audio controls>`, and submit button
- reuse `startRecording()` and the existing bottom recorder popup with a `voiceRecordingMode` value of `'fill' | 'application'`
- for voice request mode, `finishRecording` stores the Blob locally instead of calling `parseVoice`
- `submitVoiceApplication` calls `applicationAPI.createVoiceApplication`, then resets form and refreshes list

- [ ] **Step 3: Render voice requests in detail**

In `ApplicationDetail.vue`:

- add `isAudio(fileType)` helper
- add `voiceAttachments`, `applicationImages`, and `applicationFiles` computed values so audio is not duplicated in ordinary file list
- show a voice message block before ordinary attachments when `application?.appType === 2 || voiceAttachments.length > 0`
- render `<audio :src="att.fileUrl" controls preload="none" />`
- initialize transcript from `application.voiceTranscript`
- add `handleTranscribeVoice()` with loading state and error toast

- [ ] **Step 4: Mark voice requests in pending list**

In `Approvals.vue`:

- extend local `Application` interface with `appType?: number`
- use a `volume-o` icon for `appType === 2`
- show description fallback `语音申请，点击详情播放` for voice applications

- [ ] **Step 5: Run frontend build check**

Run:

```bash
cd frontend && npm run build:check
```

Expected: `vue-tsc --noEmit` succeeds and Vite build exits 0. Existing Sass legacy API or chunk-size warnings are acceptable if the command exits 0.

---

## Task 4: Integration Verification And Review

**Files:**
- Modify: `.ccg/tasks/voice-message-approval/review.md`
- Modify: `.ccg/tasks/voice-message-approval/task.json`

- [ ] **Step 1: Run full backend verification**

Run:

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
```

Expected: Maven test exits 0.

- [ ] **Step 2: Run frontend verification**

Run:

```bash
cd frontend && npm run build:check
```

Expected: command exits 0.

- [ ] **Step 3: Run diff hygiene check**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; status contains only expected task files and implementation files.

- [ ] **Step 4: Dual-model CCG security review**

Run codex and Claude reviewers on `git diff`, with focus on:

- application participant authorization
- voice audio privacy
- MiMo API key exposure
- database migration compatibility
- ordinary application regression

Write merged findings to `.ccg/tasks/voice-message-approval/review.md`.

- [ ] **Step 5: Fix Critical and high-confidence Warning issues**

If reviewers report Critical issues, fix them and rerun the relevant tests and review. If reviewers report Warning issues that are in scope and low-risk to fix, fix them before delivery.

- [ ] **Step 6: Archive CCG task**

After verification and review are complete:

```bash
mkdir -p .ccg/tasks/archive/2026-06
mv .ccg/tasks/voice-message-approval .ccg/tasks/archive/2026-06/
```

If committing is requested or appropriate, stage only this task's implementation and archive files.

---

## Self-Review

- Spec coverage: voice recording, playback, ASR-only transcription, cached transcript, participant security, MiMo safety, backend and frontend verification are covered.
- Placeholder scan: no `TBD`, `TODO`, or unspecified edge handling remains.
- Type consistency: backend uses `appType`/`voiceTranscript`; frontend uses the same camelCase JSON fields; API paths are `/applications/voice` and `/applications/{id}/transcribe-voice`.
