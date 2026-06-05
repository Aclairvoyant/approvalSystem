# Admin Mimo Config Responsive Redesign

## Summary

Refactor and polish the admin management experience while adding a safe MiMo configuration view. The admin UI must work on desktop and phone-sized screens. The MiMo feature is a safety MVP: show configuration status only, not real provider usage or local consumption statistics.

## Goals

- Make `/admin/*` visually cleaner, more consistent, and usable on desktop and mobile.
- Add a new admin system configuration page for MiMo.
- Show MiMo enabled state, base URL, ASR model, chat model, API key configured state, and masked API key.
- Enforce backend admin role checks before exposing new admin config data.
- Avoid exposing raw API keys, passwords, access keys, or secret values.

## Non-Goals

- Do not implement real MiMo usage tracking.
- Do not call undocumented MiMo console, quota, billing, or private endpoints.
- Do not rewrite the existing mobile user-facing app.
- Do not migrate existing secrets out of `application.yml` in this task, though the UI should not expose them.
- Do not change voice parsing behavior beyond what is needed to read existing MiMo config safely.

## Current Context

The existing admin UI has four pages: dashboard, applications, users, and notifications. Each page repeats header, table, status, date, and refresh styling. `AdminLayout.vue` hard-codes navigation items and current-page title mapping. The backend admin controller currently checks login state but does not enforce admin role in the controller or security configuration. The existing MiMo configuration object contains an API key and must not be serialized directly.

## Architecture

The feature has three parts:

1. Backend safety and data contract.
2. Responsive admin shell and shared visual patterns.
3. New MiMo configuration page.

The backend owns all secret masking. The frontend receives only a safe DTO and never needs to know the raw key. The frontend redesign stays within Vue 3 and Arco Design patterns already used by the project.

## Backend Design

Add a read-only admin endpoint:

```http
GET /api/admin/mimo/config
```

The response should use a dedicated DTO:

```json
{
  "enabled": true,
  "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
  "asrModel": "mimo-v2.5-asr",
  "chatModel": "mimo-v2.5",
  "apiKeyConfigured": true,
  "apiKeyMasked": "tp-c...wbh"
}
```

The endpoint must not return `MiMoConfig` directly. It should construct the DTO from whitelisted fields.

Add admin role enforcement for backend admin endpoints before adding the MiMo config endpoint. The minimal implementation can use the current authenticated user ID, load the user with `IUserService`, and reject users whose role is not `1` with a 403 response. This is less invasive than changing JWT claims/authorities and keeps the task scoped.

API key masking rules:

- Null, blank, and placeholder keys are treated as unconfigured.
- Unconfigured keys return `apiKeyConfigured: false` and an empty or neutral masked value.
- Short keys should not expose the full value.
- Normal keys expose only a small prefix and suffix, never the middle.

## Frontend Design

### Admin Shell

Refactor `AdminLayout.vue` to use a menu configuration array for navigation, labels, and icons. Derive current page title from that same configuration so adding pages does not require duplicate title mapping.

Desktop behavior:

- Persistent sidebar.
- Header with breadcrumb/current page and user menu.
- Main area with consistent padding and max-width rules.

Phone behavior:

- Collapse sidebar into a drawer or compact top menu trigger.
- Stack breadcrumb/title and user controls.
- Keep touch targets large enough for row actions and menu items.
- Avoid uncontrolled horizontal overflow.

### Shared Admin Patterns

Use shared CSS classes or small shared components for:

- Page header with title, subtitle, and actions.
- Metric/stat cards.
- Table/card containers.
- Status tags.
- Detail field layouts.

This should reduce duplicate styling across dashboard, applications, users, and notifications without rewriting business logic.

### Table-Heavy Pages

Desktop pages can keep Arco tables.

Phone pages should avoid forcing the full table into the viewport. Use a responsive card/list presentation for each record, showing the highest-value fields first and moving details/actions into a compact action area. At minimum, table containers must not break the layout on narrow screens.

### MiMo Config Page

Add a new route, for example:

```text
/admin/system
```

The page should show:

- MiMo status card: enabled/disabled and configured/unconfigured.
- Model card: ASR model and chat model.
- Endpoint card: base URL.
- Secret card: API key configured state and masked key.
- Security note: only masked values are shown.

Desktop can use a two-column card grid. Phone should use a single-column layout.

## Data Flow

1. Admin enters `/admin/system`.
2. Frontend route guard verifies login locally for UX.
3. Backend enforces admin role on `/api/admin/mimo/config`.
4. Frontend calls `adminAPI.getMimoConfig()`.
5. Backend reads `MiMoConfig`, masks the key, and returns a safe DTO.
6. Frontend renders loading, success, disabled, unconfigured, and error states.

## Error Handling

- Non-admin backend access returns 403.
- Missing or placeholder API key renders as unconfigured, not as a server error.
- Missing optional config fields render as `-` or an explicit unconfigured state.
- Frontend shows a concise error message if the config endpoint fails.
- The UI must not log or display raw secrets.

## Testing And Verification

Backend:

- Test non-admin access to admin endpoints returns 403.
- Test admin access can read MiMo config.
- Test MiMo config response does not contain the full API key.
- Test mask behavior for blank, placeholder, short, and normal keys.

Frontend:

- Run TypeScript/build checks.
- Verify desktop admin layout.
- Verify phone-sized admin layout for dashboard, list pages, and MiMo config page.
- Confirm no page has incoherent overlap or uncontrolled horizontal overflow.

## Scope Boundaries

This task may touch:

- `backend/src/main/java/com/approval/system/controller/AdminController.java`
- new backend DTO/helper files if useful
- frontend admin layout and admin pages
- `frontend/src/router/index.ts`
- `frontend/src/services/api.ts`
- new admin shared components/styles
- CCG task files and this spec

This task should not rewrite user-facing mobile pages, voice parsing internals, database schema, or external MiMo billing integrations.
