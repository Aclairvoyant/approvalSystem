# Admin Runtime Settings Requirements

## Scope
- Add admin-editable runtime settings for outbound email.
- Add admin-editable runtime settings for the voice model provider used by voice parsing.
- Keep existing `application.yml` values as fallback defaults when no database override exists.
- Persist settings so changes survive restart.

## Email Settings
- Admin can edit SMTP host, port, username/from address, SSL switch, and password.
- GET responses must not expose the raw SMTP password.
- Blank password on update means keep the existing password.
- Email sending must use the latest saved settings without restarting the backend.

## Voice Model Settings
- Admin can edit enabled state, provider, base URL, auth scheme, API key, ASR model, and chat model.
- Support MiMo's `api-key` style and DeepSeek/OpenAI-compatible `Authorization: Bearer` style.
- GET responses must not expose the raw API key.
- Blank API key on update means keep the existing API key.
- Voice parsing must use the latest saved settings without restarting the backend.

## Admin UI
- Extend the existing admin system page instead of adding a new route.
- Use editable sections for email and voice settings.
- Show masked secret status and save/loading/error states.

## Security
- New endpoints must keep the existing admin guard.
- Do not log raw secrets.
- Store secrets in the same plaintext posture as current YAML for this task; flag encryption/secret-store as a follow-up.

## Verification
- Backend tests cover fallback behavior, secret preservation, masking, and auth header selection.
- Frontend must pass lint/build checks if dependencies are available.
