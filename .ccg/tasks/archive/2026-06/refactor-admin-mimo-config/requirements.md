# Requirements

## User Request

重构并美化后台管理页面，同时需要支持查看 Mimo 用量等配置。

## Current Interpretation

- Improve the existing `/admin/*` experience built with Vue 3 + Arco Design.
- Keep the admin UI operational and dense enough for repeated management work.
- Make the admin UI usable on phone-sized screens.
- Add an admin-visible Mimo configuration/status area.
- Do not expose raw API keys or other sensitive config values in the browser.
- Preserve existing mobile pages and existing untracked voice/MiMo work unless integration is required.

## Relevant Existing Surface

- Admin layout and routes exist at `/admin/dashboard`, `/admin/applications`, `/admin/users`, and `/admin/notifications`.
- Current admin pages are independent table pages with duplicated header/table styling.
- Backend admin endpoints live in `AdminController`.
- MiMo config currently exists as `MiMoConfig` bound to `mimo.*` properties, and `VoiceApplicationServiceImpl` calls MiMo for ASR and field extraction.

## Confirmed Decision

The user selected the safe MVP:

- Show whether MiMo is enabled.
- Show baseUrl.
- Show ASR/chat model names.
- Show whether the API Key is configured.
- Show only a masked API Key tail/prefix, never the full key.
- Do not implement real usage tracking yet.
- Do not depend on an undocumented provider quota/balance endpoint.
- Use the same `/admin/*` routes for mobile-responsive admin pages unless a separate mobile admin flow becomes necessary.

## Provider Documentation Check

- Official MiMo docs show model response objects include per-call `usage` token fields.
- Token Plan docs say current plan quota and usage are viewed in Subscription Management.
- FAQ/docs describe balance, billing, and historical usage in console pages.
- Public API Reference pages found so far do not document a stable quota/balance REST endpoint.
- The configured key is a `tp-` Token Plan key, and official docs describe Token Plan usage restrictions for custom application backends. This should be surfaced as a configuration risk.

## Design Constraint

The admin page should be a safe configuration/health view. It must not hard-code undocumented console/private endpoints and must not expose raw secrets.

The admin layout must adapt to mobile screens: navigation should collapse, page headers should stack, table-heavy data should remain readable without uncontrolled horizontal overflow, and row actions/details must remain reachable on touch devices.
