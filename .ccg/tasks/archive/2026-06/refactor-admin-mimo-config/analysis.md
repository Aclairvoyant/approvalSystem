# Analysis

## Local Context Summary

- Frontend admin is Vue 3 + Arco Design.
- Existing admin pages are `Dashboard.vue`, `Applications.vue`, `Users.vue`, and `Notifications.vue` under `frontend/src/pages/admin`.
- `AdminLayout.vue` hard-codes menu links and title mapping.
- Admin pages duplicate page header, table container, status mapping, date formatting, and refresh/pagination patterns.
- Backend admin API lives in `AdminController`.
- MiMo support currently appears in uncommitted user work: `MiMoConfig`, `VoiceApplicationServiceImpl`, and voice parsing API wiring.
- No project tests were found under `backend/src/test` or frontend spec files.

## Dual-Model Analysis Consensus

### Critical

- `/api/admin/**` is authenticated but not role-gated in backend security. Any logged-in user can potentially call admin endpoints directly.
- `application.yml` currently contains real secrets. Any configuration-view feature must use a whitelist DTO and must never serialize raw config objects or raw keys.
- Existing admin approve/reject endpoints call ordinary approval service methods, which may fail when the admin is not the original approver. This is adjacent risk, not necessarily required for the MiMo config MVP.

### Warning

- No MiMo usage table or counter exists. `VoiceApplicationServiceImpl` currently extracts only message content and discards possible provider `usage` fields.
- MiMo provider quota/billing endpoint availability is unknown and should not be assumed.
- `RestTemplate` has no explicit timeout config, which may affect MiMo health checks if those are added.
- Dashboard `refreshStats` reports success even if `fetchStats` fails.
- Application status mapping in admin UI does not cover all backend status values.

### Recommendation

Implement a safe MVP:

- First add backend admin role enforcement before exposing any new config endpoint.
- Add a masked MiMo overview/config endpoint returning only safe fields:
  - enabled/configured state
  - base URL host or safe base URL
  - ASR/chat model names
  - API key configured boolean or a strictly masked suffix
  - usage summary only if locally tracked data exists
- For “usage,” default to config/health visibility unless the user confirms local usage tracking should be added.
- Keep UI refactor focused:
  - data-driven admin menu
  - shared admin page shell/header patterns
  - cleaner dashboard cards/statistics
  - new system/MiMo page
  - responsive admin layout for phone-sized screens
  - avoid disruptive rewrites of mobile or voice logic

## User Decision

The user chose the safe MVP:

- Show MiMo enabled state, baseUrl, model names, API Key configured state, and a masked API Key prefix/tail.
- Do not implement real usage tracking in this task.
- Do not depend on an undocumented official quota/balance API.

## Provider Documentation Findings

- Official pricing docs say Pay-As-You-Go consumes account balance based on actual token usage.
- Token Plan docs say quota and usage are visible in Subscription Management.
- Official OpenAI-compatible chat response examples include a `usage` object for per-call token accounting.
- Search and public documentation did not reveal a stable official quota/balance REST endpoint.
- Token Plan docs state the package is for programming tools and restrict custom backend/script usage; the current project uses a `tp-` key in backend config, so the admin page should surface this as a risk/state.

## Test Expectations

- Backend admin endpoints reject non-admin users with 403.
- MiMo overview response never contains full API keys, passwords, access keys, or secrets.
- MiMo overview handles disabled, missing-key, placeholder-key, and configured states.
- Frontend type/build checks pass.
- Browser verification covers desktop and phone-sized viewports for the admin pages.
