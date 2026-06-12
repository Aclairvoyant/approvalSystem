# Review: Admin Runtime Settings

## Dual-Model Review Summary

### Initial review
- Codex and Claude reviewed the backend/frontend runtime settings changes.
- Critical issues found and fixed:
  - Existing databases were missing a root initialization table for `system_settings`.
  - Voice ASR and chat settings were originally coupled too closely for DeepSeek-style chat providers.
  - Sparse provider updates could keep stale chat URL/auth values.
  - Runtime outbound calls lacked a bounded `RestTemplate` timeout.

### Follow-up review
- Codex and Claude re-reviewed the provider switching behavior.
- No Critical findings remained.
- Warnings addressed:
  - `provider=deepseek` with sparse input now defaults chat URL/auth/header/model to DeepSeek values.
  - Provider changes without a new chat key now clear stale provider-specific chat keys.
  - Switching back to MiMo restores MiMo chat URL/model/auth defaults and reads the MiMo legacy key fallback.
  - Missing `voice.chatModel` now uses provider-aware defaults on read, so DeepSeek resolves to `deepseek-chat`.

### Remaining follow-ups
- Provider names are normalized but not validated against an allow-list; typo handling can be improved later.
- Legacy `apiKey` still writes the same key to ASR and chat for backward compatibility.
- The repository has migration SQL under `db/migration`, but production must ensure the V10 migration is actually applied before startup.

## Verification

- `mvnw -f backend/pom.xml -Dtest=SystemSettingServiceImplTest#providerChangeToDeepSeekDefaultsSparseChatSettingsToDeepSeek test`
  - First failed on the old chat URL, then passed after the fix.
- `mvnw -f backend/pom.xml -Dtest=SystemSettingServiceImplTest#deepSeekProviderUsesProviderAwareChatModelDefaultWhenDatabaseValueIsMissing test`
  - First failed on `mimo-chat`, then passed after the provider-aware fallback fix.
- `mvnw -f backend/pom.xml -Dtest=SystemSettingServiceImplTest test`
  - 8 tests passed.
- `mvnw -f backend/pom.xml -Dtest=SystemSettingServiceImplTest,AdminControllerSettingsTest,VoiceApplicationServiceImplTest,EmailServiceImplTest test`
  - 17 tests passed.
- `mvnw -f backend/pom.xml test`
  - 54 tests passed.
- `npm run lint`
  - Passed.
- `npm run build:check`
  - Passed with existing Sass legacy JS API and chunk-size warnings.
- `git diff --check`
  - Passed with CRLF warnings only.
