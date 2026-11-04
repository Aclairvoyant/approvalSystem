# Admin Mimo Config Responsive Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a safer, more polished, mobile-responsive admin UI with a masked MiMo configuration page.

**Architecture:** Backend adds admin role enforcement and a safe MiMo config DTO endpoint. Frontend adds a responsive admin shell, a new system/MiMo page, and mobile-friendly list presentations for admin table pages. Work is split into backend and frontend file groups to avoid write conflicts.

**Tech Stack:** Spring Boot 3, MyBatis Plus, JUnit/MockMvc, Vue 3, TypeScript, Arco Design.

---

## File Structure

Backend task owns:

- Create: `backend/src/main/java/com/approval/system/dto/MimoConfigResponse.java`
- Create: `backend/src/test/java/com/approval/system/controller/AdminControllerTest.java`
- Modify: `backend/src/main/java/com/approval/system/controller/AdminController.java`

Frontend task owns:

- Create: `frontend/src/pages/admin/System.vue`
- Create: `frontend/src/styles/admin.css`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/layouts/AdminLayout.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/pages/admin/Dashboard.vue`
- Modify: `frontend/src/pages/admin/Applications.vue`
- Modify: `frontend/src/pages/admin/Users.vue`
- Modify: `frontend/src/pages/admin/Notifications.vue`

## Task 1: Backend Admin Guard And MiMo Config Endpoint

**Files:**

- Create: `backend/src/main/java/com/approval/system/dto/MimoConfigResponse.java`
- Create: `backend/src/test/java/com/approval/system/controller/AdminControllerTest.java`
- Modify: `backend/src/main/java/com/approval/system/controller/AdminController.java`

- [ ] **Step 1: Write failing controller tests**

Create `backend/src/test/java/com/approval/system/controller/AdminControllerTest.java` with tests for:

- non-admin user calling `GET /api/admin/mimo/config` receives JSON code `403`
- admin user calling `GET /api/admin/mimo/config` receives JSON code `200`
- successful response contains `apiKeyConfigured: true`
- successful response contains a masked key but not the full raw key
- placeholder key is treated as unconfigured

Use `MockMvcBuilders.standaloneSetup(controller)` and mocked `IUserService`, `IApplicationService`, `IUserRelationService`, `INotificationService`, and `MiMoConfig`. Set `SecurityContextHolder` to a principal `Long` for each test and clear it after each test.

- [ ] **Step 2: Verify backend tests fail before implementation**

Run:

```bash
cd backend
mvn -Dtest=AdminControllerTest test
```

Expected: FAIL because `MimoConfigResponse` and `/api/admin/mimo/config` do not exist yet.

- [ ] **Step 3: Add safe response DTO**

Create `MimoConfigResponse` with Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` fields:

```java
private Boolean enabled;
private String baseUrl;
private String asrModel;
private String chatModel;
private Boolean apiKeyConfigured;
private String apiKeyMasked;
```

- [ ] **Step 4: Add admin role guard**

In `AdminController`, inject `MiMoConfig`, then add helper methods:

- `private boolean isCurrentUserAdmin()`
- `private boolean isConfiguredApiKey(String apiKey)`
- `private String maskApiKey(String apiKey)`
- update `getCurrentUserId()` to safely handle missing authentication

Add `if (!isCurrentUserAdmin()) return ApiResponse.fail(403, "没有管理员权限");` at the start of every public admin endpoint in `AdminController`.

- [ ] **Step 5: Add MiMo config endpoint**

Add:

```java
@GetMapping("/mimo/config")
public ApiResponse<MimoConfigResponse> getMimoConfig()
```

The method must return only whitelisted fields and must never return the full `apiKey`.

- [ ] **Step 6: Verify backend tests pass**

Run:

```bash
cd backend
mvn -Dtest=AdminControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Run backend compile/test scope**

Run:

```bash
cd backend
mvn test
```

Expected: PASS or report external/environment failures separately if unrelated to these changes.

## Task 2: Responsive Admin Frontend And System Page

**Files:**

- Create: `frontend/src/pages/admin/System.vue`
- Create: `frontend/src/styles/admin.css`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/layouts/AdminLayout.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/services/api.ts`
- Modify: `frontend/src/pages/admin/Dashboard.vue`
- Modify: `frontend/src/pages/admin/Applications.vue`
- Modify: `frontend/src/pages/admin/Users.vue`
- Modify: `frontend/src/pages/admin/Notifications.vue`

- [ ] **Step 1: Add frontend API contract**

In `frontend/src/services/api.ts`, add:

```ts
export interface MimoConfigResponse {
  enabled?: boolean
  baseUrl?: string
  asrModel?: string
  chatModel?: string
  apiKeyConfigured?: boolean
  apiKeyMasked?: string
}
```

Add `adminAPI.getMimoConfig()` calling `GET /admin/mimo/config`.

- [ ] **Step 2: Add shared admin stylesheet**

Create `frontend/src/styles/admin.css` with shared page, toolbar, panel, metric, mobile-list, and responsive table utility classes. Import it from `frontend/src/main.ts` after Arco/Vant CSS.

- [ ] **Step 3: Refactor admin layout navigation**

In `AdminLayout.vue`:

- replace hard-coded nav title mapping with a menu config array
- add system route item
- add a mobile menu button and drawer
- keep desktop sidebar behavior
- add responsive CSS for `max-width: 768px`

- [ ] **Step 4: Add system/MiMo page**

Create `System.vue` with loading, error, configured, unconfigured, enabled, and disabled states. Use cards/descriptions/tags. Use one-column mobile layout and two-column desktop layout.

- [ ] **Step 5: Register route**

Add `/admin/system` child route to `frontend/src/router/index.ts`.

- [ ] **Step 6: Polish dashboard**

Use shared admin classes, make stat cards responsive, bind loading state correctly, and make refresh await `fetchStats()` before showing success.

- [ ] **Step 7: Add mobile list presentation for table pages**

For `Applications.vue`, `Users.vue`, and `Notifications.vue`:

- keep desktop Arco tables
- add mobile card/list markup for the main records
- hide tables on phone-sized screens and hide mobile lists on desktop
- keep current actions reachable on touch devices
- keep existing data fetching logic intact

- [ ] **Step 8: Run frontend checks**

Run:

```bash
cd frontend
npm run build:check
```

Expected: PASS.

## Task 3: Integration Verification

**Files:**

- No planned source edits. Use this task for review and fixes only if verification finds issues.

- [ ] **Step 1: Run full verification commands**

Run:

```bash
cd backend
mvn test
```

Run:

```bash
cd frontend
npm run build:check
```

- [ ] **Step 2: Browser visual verification**

Start the frontend dev server and verify:

- `/admin/dashboard`
- `/admin/applications`
- `/admin/users`
- `/admin/notifications`
- `/admin/system`

Check desktop and phone-sized viewports. Confirm no incoherent overlap and no uncontrolled horizontal overflow.

- [ ] **Step 3: CCG dual-model review**

Run both Codex and Claude reviewer backends on `git diff`. Save findings in `.ccg/tasks/refactor-admin-mimo-config/review.md`, fix Critical issues, and re-review if needed.
