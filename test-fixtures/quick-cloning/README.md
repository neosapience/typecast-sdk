# quick-cloning fixtures

Shared fixtures for the `POST /v1/voices/clone` and `DELETE /v1/voices/{voice_id}` endpoints, used by all 11 SDKs to keep multipart body shape and response parsing consistent.

## Files

- `sample.wav` — Dummy 1-second silent WAV (mono, 16-bit PCM, 16kHz). Used as upload payload in unit tests across all SDKs. Content does not need to be real speech.
- `success_v21.json` — Captured response from a real `POST /v1/voices/clone` call against `api.icepeak.in` with `model=ssfm-v21`. Captured 2026-05-07.
- `success_v30.json` — Same with `model=ssfm-v30`.
- `error_file_too_large.json` — Hand-crafted error response shape for `CLONING_FILE_TOO_LARGE`. Used by mocks; we do not actually upload >25 MB to capture this.

## Server-side limits (must match SDK pre-validation)

- File size: max 25 MB (typecast-api `cloning_max_file_size` env, default `25 * 1024 * 1024`).
- Name: 1–30 characters (typecast-api `Form(..., min_length=1, max_length=30)`).
- Model: must be `ssfm-v21` or `ssfm-v30`.
