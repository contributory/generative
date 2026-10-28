# Speech Services: ElevenLabs, Fish Audio & Cartesia + Feature Tools: Music Generation

## Goal

Add **ElevenLabs**, **Fish Audio**, and **Cartesia** as first-class engines in **Speech Services**, and add an independently configurable **Music Generation** tool in **Feature Tools**. Keep the existing interface style, auto-save behavior for media profile editing, speech playback, and saved user configurations intact.

**Status: Source implementation added; verification pending.** This is a source-aware implementation checklist; check a task only after the corresponding behavior and tests exist. Keep Speech Services and Music Generation independent: selecting a TTS service must not select a music profile, and music generation must not depend on the chat model, a Provider model, or a speech voice.

## Non-negotiable Music Generation UX contract

1. Show **Music Generation** alongside **Image Generation** and **Video Generation** under Feature Tools, with its own enable/disable switch.
2. **No model picker anywhere in the Music Generation tool settings.** Neither a `DefaultModelSelector` nor provider/model-dependent validation may appear.
3. Show **only one Profile selector** with a separate icon button to open the **Music Profiles** list/library. The selected profile belongs to the Music Generation tool itself, not to `ModelConfiguration`.
4. **No API Type selector, no Default/Custom toggle, no built-in Default profile.** Every music profile is a **Custom request profile** by definition; the editor opens directly into its Custom request-chain fields. Do not even display a redundant “Custom” selector.
5. A music profile owns its endpoint, authentication, request steps, optional provider-specific model identifier **inside the request template**, async result handling, and audio extraction. The user never needs to configure or select a `ModelProvider`/`modelId` for music. An external service's required `model_id` is a profile request-body/header setting, **not a model picker**.
6. Selecting, creating, editing, duplicating, deleting, and testing profiles must have persistent effects. Editing auto-saves like the current media profile editor; no global Save button. A missing, deleted, or invalid active profile must not silently switch to a hidden Default mode or send a request.

## Current code / integration points (audited 2026-10-27)

### Speech Services

- `shared/model/SpeechService.kt`: serializable TTS configuration currently uses `provider = "system"` or an existing `ModelProvider.id`, with optional `modelId`, `voice`, `apiKey`, `responseFormat`, `languageCode`, `extraBody`, etc.
- `features/speech/ui/SpeechServicesScreen.kt` and `SpeechEditor.kt`: list, selection, reordering, test playback, provider/model selectors, and editor. The current editor only exposes System TTS and model-based Providers.
- `shared/media/SpeechSynthesisService.kt`: existing OpenAI-compatible/Google speech transport. `features/home/chat/logic/ChatSpeechCoordinator.kt` assumes every non-System TTS service has a `ModelProvider` and an available TTS model; its dispatch must be adjusted for standalone engines.
- `database/SpeechRepository.kt`: service data, protected API-key storage, `extraBody`, and import/export compatibility. Check any persisted fields, Room entity, and backup/restore plumbing before changing the schema.

### Music Generation

- `features/tools/ui/ToolsEditor.kt`: media-tool UI currently loops over `generate_image` / `generate_video` and always shows a model selector and mode-dependent profile picker. Music needs **its own profile-only branch**, not a third value in that unchanged loop.
- `shared/model/Tools.kt`: `ToolSettings.system`, `SystemToolConfig`, image/video libraries, `selectedMediaProfile`, and `systemMediaToolAvailable` currently require provider/model identity for Image/Video. Music requires independent `musicRequestProfiles` and `activeMusicRequestProfileId`, with a profile-only availability check.
- `features/tools/ui/MediaRequestChainSettings.kt`, `MediaRequestProfileEditor.kt`, `MediaRequestChainStepEditor.kt`: reusable library/editor, request-step editing, per-step testing, auto-save. The current API is Image/Video boolean-based and creates a built-in Default; generalize using an explicit media kind without changing existing Image/Video behavior.
- `shared/model/media/MediaRequestChain.kt`: request steps and JSON/Image/Video/Octet-stream final types, but no explicit `AUDIO` final response. Preserve existing serialized enum values.
- `integrations/media/MediaRequestChainExecutor.kt`: step execution, polling/retry, request templates, file extraction, and test-through-step. Currently tests require a non-empty model; music must support profile-only execution and audio outputs.
- `features/home/chat/logic/tool/ToolChatViewModel.kt`, `features/home/shared/logic/ToolMediaGenerationService.kt`, `integrations/media/SystemMediaTools.kt`, and `shared/helpers/media/MediaInputReference.kt`: tool discoverability, attached schemas, execution, file reporting, and generation progress currently distinguish only `generate_image` / `generate_video`.
- `shared/files/ToolFiles.kt` and shared media/audio previews already recognize some audio MIME types. Reuse the existing audio player/media preview and tool-file storage instead of inventing a second file system.

## Speech integration architecture — required for all three engines

### Persisted models and routing

- Keep `SpeechService.id` stable. Add a typed `engine` identifier (or an equivalent explicit discriminator) for `system`, `provider`, `elevenlabs`, `fish_audio`, `cartesia`. When loading old services, infer `system` from `provider == "system"`, otherwise treat the value as a legacy `ModelProvider.id`. Do **not** assume a third-party engine ID is an existing provider ID. Keep save/restore/export compatible with older JSON and Room records; use additive fields with safe defaults and an appropriate migration only if needed.
- `modelId` on ElevenLabs, Fish Audio, and Cartesia is an **engine-specific TTS model setting**, not a model belonging to a Generative `ModelProvider`. The existing Speech Editor's model picker must not search generic Provider models for these engines. Fish Audio's **TTS model** is transmitted in the `model` HTTP header, while Fish Audio's **voice** comes from a separate `reference_id` body field.
- Give each engine its own request mapper and validation (`ElevenLabsSpeechClient`, `FishAudioSpeechClient`, `CartesiaSpeechClient` or equivalent) behind a small shared interface. Do not force their different field names, audio formats, or auth schemes through `speechPayload()`'s OpenAI/Google branch. Branch by engine **before** `ChatSpeechCoordinator` tries to resolve a `ModelProvider`.
- Reuse encrypted `SpeechRepository` auth storage and existing playback/test/stop flows. Persist chosen voice **ID**, not the display name or preview URL. Keep provider-specific tuning in typed serializable settings (preferred) or validated `extraBody`; do not silently send unsupported parameters. Avoid copying or logging credentials in the model's tool messages, crash reports, or network diagnostics.

### Voice browsing: mandatory for ElevenLabs, Fish Audio, and Cartesia

Each engine has a documented voice-list endpoint. **Implement native Fetch/Choose Voice for all three**; manual Voice ID entry remains a fallback, not a substitute for implementing browsing.

- Add a trailing browse/list icon next to the **Voice** field in `SpeechEditor`. Open a searchable bottom sheet styled consistently with the app. Each item shows display name and optional description/language/category; selecting it saves the voice ID into the field and shows its friendly name without replacing the stored ID. Optionally show a preview/play button when a usable preview is returned; do not invent URLs or synthesize paid previews automatically while browsing.
- Shared representation suggestion: `SpeechVoice(id: String, name: String, description: String?, languages: List<String>, previewUrl: String?, meta: ...)`, `VoicePage(voices: List<SpeechVoice>, nextCursor: String?)`. Keep provider-specific page tokens and query parameters inside separate adapters. Include a loading state, empty state, Retry, search debounce, pagination/Load more, selection indicator, and clear error states (missing key, 401/403, 429, 5xx, offline).
- Fetch voices **on demand** when the sheet opens, not each time the editor recomposes. Optional in-memory cache must be keyed by engine and credential identity and invalidated when the API key/account changes; **never cache API keys** or persist a directory response as service configuration. Cancel outstanding requests on close or engine switch; cap fetched item counts and prevent stale responses from replacing the current engine's list.
- Implement search and paging in accordance with each provider's actual protocol (see exact endpoints below). Never fake cross-provider paging semantics (e.g., ElevenLabs uses a token, Fish uses page numbers, Cartesia uses a voice-ID cursor). Support manual entry even when listings are empty or temporarily fail; do not delete a previously saved voice ID just because an API query fails.
- Optional voice preview URLs are untrusted network data: require HTTPS, limit downloads, never attach the API credential to a third-party host or redirected URL, and use the existing audio player with stop/cancellation. Voice-list and TTS calls must not silently fall back to a different provider.

## Phase 1 — ElevenLabs in Speech Services

### Text-to-Speech REST contract

**Endpoint:** `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}`. Path variable is the selected **voice ID**, URL-encoded as a path segment (not voice display name). **Headers:** `xi-api-key: <key>`, `Content-Type: application/json`, `Accept: audio/mpeg` when asking for MP3. **Query:** `output_format=mp3_44100_128` is a concrete compatible example; other formats use the ElevenLabs codec/sample-rate/bitrate notation and may be plan-limited. `output_format` belongs in the query string, **not** a JSON field. Response on success is **raw audio bytes**, not JSON/base64. Source: https://elevenlabs.io/docs/api-reference/text-to-speech/convert

Request example (placeholders are illustrative, never embed a real key in source):

```http
POST /v1/text-to-speech/{voice_id}?output_format=mp3_44100_128 HTTP/1.1
Host: api.elevenlabs.io
xi-api-key: <ELEVENLABS_API_KEY>
Content-Type: application/json
Accept: audio/mpeg

{"text":"Hello from Generative.","model_id":"eleven_multilingual_v2"}
```

- Required `text`; engine `model_id` chooses the synthesis family; optional request-level `voice_settings` (e.g., `stability`, `similarity_boost`, `style`, `use_speaker_boost`, `speed`) and `language_code` **only if supported by the selected model**. Do not assume every model supports voice instructions, language enforcement, or all settings. Expose valid engine fields, with an optional advanced-JSON area for documented parameters, but give required fields explicit UI controls.
- Default to an explicitly selected model/format/voice; list installed/account-accessible voices via the endpoint below. If the requested `output_format` is MP3, give returned bytes to existing MP3 playback; if exposing PCM, wrap raw PCM with correct encoding/sample rate/channels or choose a supported container, do not label PCM as MP3.
- Send a bounded transcript. On non-2xx, parse a short sanitized JSON error (`detail` / message when available), show the actual HTTP status without API-key disclosure, and **do not** pass the error body to the audio player. Handle network failure, empty bytes, unsupported output format and cancellation.

### ElevenLabs List Voices API — implement it

**Endpoint:** `GET https://api.elevenlabs.io/v2/voices` (do not use deprecated, unpaged `GET /v1/voices`). Header `xi-api-key: <key>`. Query: `page_size=50` (range up to 100), optional `search=<typed text>`, `sort=name`, `sort_direction=asc`, and `next_page_token=<returned token>` for subsequent pages. The response contains:

```json
{
  "voices": [{"voice_id": "abc123", "name": "Sample Voice", "category": "personal", "description": "...", "preview_url": "https://..."}],
  "has_more": true,
  "next_page_token": "opaque-token"
}
```

- Map `voices[].voice_id` → stored ID, `name` → label, `description` / `category` / `labels` → optional subtitles, `preview_url` → optional preview. Continue fetching only when `has_more` and a nonempty `next_page_token` exist. Search on server, debounce typing, reset paging on query changes.
- Account-specific voices may require the API key even if a public example works unauthenticated. Keep manual entry; don't equate “no results” with a missing voice permission. Optionally use `GET /v1/voices/{voice_id}` to look up an existing manually entered ID, but voice details are not needed to save it.
- Source: https://elevenlabs.io/docs/api-reference/voices/search

### ElevenLabs implementation checklist

- [ ] Add ElevenLabs to the Speech Services engine picker with masked API key, explicit voice ID (browsable), model ID, compatible output-format choice, and optional supported voice settings.
- [ ] Implement the POST endpoint/auth/query/body/raw-audio contract above; separate validation and error parsing from OpenAI/Google TTS.
- [ ] Implement live searchable/paginated voices (`GET /v2/voices`), save the ID, show optional preview metadata, preserve manual-ID fallback.
- [ ] Wire Test Voice / Stop and chat read-aloud; verify cancellation, invalid/changed API key, absent voice, 401/403, 422, 429, 5xx, and unsupported format with mock HTTP tests.

## Phase 2 — Fish Audio in Speech Services

### Text-to-Speech REST contract (Fish Audio cloud, not Fish Speech self-hosted)

**Endpoint:** `POST https://api.fish.audio/v1/tts`. **Headers:** `Authorization: Bearer <key>`, `Content-Type: application/json`, **`model: <tts-model>`** (important: synthesis model goes in the HTTP header, not the `reference_id` field). Current documented examples include `s2.1-pro` and `s2.1-pro-free`; model availability and tiers may change. **Success:** raw audio bytes in the requested format. Source: https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech

```http
POST /v1/tts HTTP/1.1
Host: api.fish.audio
Authorization: Bearer <FISH_AUDIO_API_KEY>
Content-Type: application/json
model: s2.1-pro-free

{"text":"Hello from Generative.","reference_id":"<VOICE_MODEL_ID>","format":"mp3","sample_rate":44100,"mp3_bitrate":128,"prosody":{"speed":1},"normalize":true}
```

- Required `text`. `reference_id` is the **selected voice model ID** (Fish's naming, not the synthesis-model header). The API also supports `references` with provided reference audio, but reference-audio upload and multi-speaker dialogue are **out of scope** for this first integration; never create a fake reference ID. When no voice is chosen, omit `reference_id` only if the chosen API model explicitly supports its documented default; otherwise block Test/Save with a clear message.
- Documented `format`: `mp3`, `wav`, `pcm`, `opus`; `sample_rate` and MP3/Opus bitrates must follow provider constraints. Optional tuning: `temperature` and `top_p` each 0..1; `prosody` settings (including speed/volume); `chunk_length` 100..300; `latency` (`normal`, `balanced`, `low`); `language` affects text normalization/alignment, **not** voice/accent. Expose only needed fields; preserve `extraBody` for validated advanced settings.
- Decode response according to requested format and MIME; raw PCM requires correct sample rate and channel count. Do **not** decode successful MP3 responses as JSON, and do not treat `model` as a property of the JSON body. The HTTP endpoint is JSON + binary audio; the separate WebSocket `/v1/tts/live` uses MessagePack and is **not** needed here.

### Fish Audio voice search/list endpoint — implement it

**Endpoint:** `GET https://api.fish.audio/model`; header `Authorization: Bearer <key>`. These **voice models** are what users select as voices. Query: `page_size=20`, `page_number=1` (one-based), optional `title=<search>`, `self=true` (workspace-owned only), `language`, `sort_by=score|task_count|created_at`, and `licensed=true` if filtering licensed catalog voices. No ElevenLabs-style token cursor.

```json
{
  "total": 2,
  "items": [{"_id": "voice-id-1", "title": "Sample Fish Voice", "description": "...", "languages": ["en"], "visibility": "public"}],
  "has_more": false
}
```

- Map `items[]._id` → ID used in `reference_id`, `title` → label, `description` and `languages` → subtitles; `cover_image` is a picture, **not a playable voice preview URL**. Show/search both available and owned voices according to user filters; avoid blindly downloading the entire public catalog. Do not invent a preview-audio endpoint when the returned `samples` lack a documented playable link.
- Pagination increments `page_number` while `has_more == true` (or `total` and current displayed count safely imply more; the documented `has_more` can be nullable). Reset on search/filter change. `GET /model/{id}` may be used to resolve an entered ID. Unlike ElevenLabs/Cartesia, Fish calls voice choices **models**; the UI should still label them **Voices** and distinguish them from the `model` synthesis header.
- Source: https://docs.fish.audio/api-reference/endpoint/model/list-models

### Fish Audio implementation checklist

- [ ] Add Fish Audio engine + encrypted key, `model` header setting, Voice ID picker/manual fallback, format and supported tuning fields.
- [ ] Implement cloud `POST /v1/tts` JSON/raw-audio flow, including format-specific validation and optional absence of `reference_id` where explicitly supported.
- [ ] Implement searchable/paginated `GET /model`, map `_id`/`title`, support personal and catalog browsing, and maintain selected ID across refresh/failure.
- [ ] Test correct `model` header vs `reference_id` body, 401/402/422/429/5xx handling, output formats, playback/stop, and no accidental MessagePack/WebSocket usage.

## Phase 3 — Cartesia in Speech Services

### Text-to-Speech REST contract

**Endpoint:** `POST https://api.cartesia.ai/tts/bytes`. **Headers:** `Authorization: Bearer <CARTESIA_API_KEY>`, **`Cartesia-Version: 2026-08-14`**, `Content-Type: application/json` (and `Accept: audio/*` as appropriate). **Important correction:** the current official REST reference uses **`Authorization: Bearer`**, **not `X-API-Key`**. The documented version as of 2026-10-27 is `2026-08-14`; centralize it as a configurable/maintained constant and do not use it indefinitely without checking compatibility. **Success:** binary audio bytes. Source: https://docs.cartesia.ai/api-reference/tts/bytes

```http
POST /tts/bytes HTTP/1.1
Host: api.cartesia.ai
Authorization: Bearer <CARTESIA_API_KEY>
Cartesia-Version: 2026-08-14
Content-Type: application/json
Accept: audio/*

{"model_id":"sonic-3.6","transcript":"Hello from Generative.","voice":"<VOICE_ID>","output_format":{"container":"wav","encoding":"pcm_s16le","sample_rate":44100}}
```

- Required `model_id` (example `sonic-3.6`), `transcript`, `voice` (a **string voice ID** for this endpoint), and `output_format` object. `output_format` is **not** a string like `"mp3"`; the reference specifies WAV/MP3/RAW format variants. Default to playable WAV in the editor; allow only documented compatible combinations of container, encoding and sample rate. For raw PCM, wrap/convert correctly before playback, or disallow it in v1 to avoid corrupt audio.
- Optional `language` or `locale` (**send at most one**; both together return HTTP 400), `normalization`, optional accent for compatible voices, pronunciation dictionary, and `generation_config` such as `speed` and `volume` on supported models. Do not send legacy/unsupported fields. Route generic `SpeechService.speed` to `generation_config.speed` only when the model accepts it.
- Check HTTP status/content type before passing bytes to the player; error payloads may be JSON. Do not attempt to parse successful audio as a JSON body. Keep API version header identical for synthesis and voice list requests.

### Cartesia List Voices API — implement it

**Endpoint:** `GET https://api.cartesia.ai/voices`. **Headers:** `Authorization: Bearer <key>` and `Cartesia-Version: 2026-08-14`. Query examples: `limit=50` (range 1..100), `q=<voice name/description/ID>`, optional `language=en`, `is_owner=true`, `starting_after=<next_page ID>`. Optional `expand[]=preview_file_url` requests preview URLs when supported. Source: https://docs.cartesia.ai/api-reference/voices/list

```json
{
  "data": [{"id":"voice-id-1","name":"Sample Cartesia Voice","description":"...","language":"en","status":"active"}],
  "has_more": true,
  "next_page": "voice-id-cursor"
}
```

- Map `data[].id` → stored Voice ID, `name` → label, `language` / `description` / `accents` → subtitles. Continue only while `has_more` and `next_page` are present: pass `next_page` as `starting_after` on the next request (**not** a page number or opaque ElevenLabs token). `q` is the server-side search field, not `search` or `title`.
- Optionally enable preview when the API supplies `preview_file_url` (only with safe HTTPS media loading); keep manual-ID entry and selection stable when a listed voice is renamed or archived. The voice-list API and the synthesis API use the same Cartesia authentication/version scheme.

### Cartesia implementation checklist

- [ ] Add Cartesia engine with encrypted API key, Sonic model ID, browsable Voice ID, WAV-compatible output format, and optional supported voice settings.
- [ ] Implement `POST /tts/bytes` with **Bearer authorization**, version header, correct required JSON fields and audio decoding; forbid simultaneous `language` and `locale`.
- [ ] Implement searchable/paginated `GET /voices` with `q`, `starting_after`, `has_more`/`next_page`, optional previews, and manual-ID fallback.
- [ ] Test header/version/JSON correctness, binary WAV/raw PCM behavior, 400/401/403/422/429/5xx errors, save/restore, Test Voice / Stop, and cancellation.

## Phase 4 — Music Profiles (Custom only)

- [ ] Add a standalone, backwards-compatible collection and active profile ID for `generate_music` to persisted `SystemToolConfig` (or an equally clear dedicated serializable field). Default to **disabled**, with an empty profile library; no synthetic Default entry or mode field for music.
- [ ] Implement `Music Profiles` as a reusable profile-list UI matching Image/Video Profiles: Add in AppBar; list items open Edit; overflow menu for contextual actions; no global Save. Only manually created/imported **Custom** profiles are available.
- [ ] Generalize the request-chain/profile editors by explicit media kind (`IMAGE`, `VIDEO`, `MUSIC`) instead of relying solely on `video: Boolean`. Music skips mode selection and all default-API/model-specific options; save `mode = CUSTOM` for compatibility if reusing `MediaRequestProfile`, but never offer mode switching. Normalize/reject imported music profiles marked `DEFAULT` explicitly.
- [ ] Allow each music profile to configure endpoint, HTTP method, authentication/headers, JSON/form/raw request, request-body templates, multiple steps, polling/retry, and extraction. Offer a prompt placeholder and optional structured inputs (duration, style/genre, lyrics, instrumental flag, negative prompt, etc.) **only when the profile can use them**; do not hardcode a specific music vendor's schema.
- [ ] Supply a secure profile-owned credential/reference system. Reuse existing encrypted secret storage patterns; mask secrets, redact tests/logs/export previews, and never save or echo raw authorization headers in conversation/tool results. Do not force users to create a fake Provider or choose a dummy model just to provide credentials. Define secure backup/export semantics.
- [ ] Update per-step test to accept an empty model in the **Music** context only; test from the editor with a sample prompt and optional parameters, show safe response/diagnostic information, and never write or replace the user's saved music file merely because a step was tested.
- [ ] Give music a distinct `AUDIO` final-response type (`audio/*` from raw bytes, or JSON URL/base64 extraction), with correct MIME sniffing, safe extensions (`.mp3`, `.wav`, `.flac`, `.ogg`, `.m4a`, etc. where supported), download limits, and validation. Ensure asynchronous music APIs can poll and retrieve the finished audio without treating a job ID as a track.
- [ ] When deleting the selected music profile, clear/repair its selection and disable generation until another valid Custom profile is chosen. Handle import duplicate IDs, rename, edit mid-generation, and persisted settings reload without mutating Image/Video profile collections.

## Phase 5 — Music Generation tool, execution, and chat output

- [ ] Add the **Music Generation** card in Feature Tools beside Image/Video: title, concise description, enable switch, one enabled Profile dropdown, and a profile-library icon. **No provider/model dropdown and no API Type/Default/Custom selector** in this card or its music editor.
- [ ] Attach `generate_music` to the AI tool list only when the user enabled it **and** a valid Custom profile is selected. Add its name to built-in tool discovery (`search_tools`) and any quick Feature Tools surfaces; do not add an unrelated model selection step. Disabled/unconfigured tools must never trigger network calls.
- [ ] Define a clear model-facing `generate_music` schema with `prompt` and optional supported fields only. Resolve server endpoint, authentication, hidden constants, vendor model name, and any other provider-specific values **solely from the selected profile**; avoid publishing keys, raw templates, or sensitive data to the language model.
- [ ] Implement a dedicated music execution path through the request-chain executor. Do **not** coerce music into `IMAGE_GENERATION`/`VIDEO_GENERATION`, select a chat model, or invoke built-in image/video APIs. Keep tool calls cancellable and enforce timeout, polling/retry, response size, and storage limits.
- [ ] After success, persist the audio in the existing ToolFiles/Media Storage, associate the saved file with `ToolActivity.files`, and show an inline playable/downloadable **audio** card in chat plus the existing tool progress/error UI. A generated track must not appear as an image/video thumbnail or an unplayable generic JSON payload.
- [ ] Verify all generation completion/error/abort paths: exact attachment visibility, file MIME and metadata, error state, cancellation on chat switch, retry policy, failed file cleanup, and suppression of stale completions. Respect the current temporary-chat non-persistence and cancellation rules.
- [ ] Ensure the tool works when the selected **chat** model is any valid LLM that supports tools; the LLM's model identity is unrelated to the music service and must not be sent unless the music profile explicitly supplies it in its request template.

## Phase 6 — Verification, migration, localization

- [ ] Add focused tests for serialization/backward compatibility of SpeechService and ToolSettings, encrypted credentials, provider/engine routing, mandatory voice-list paging/search/selection for all three engines, and legacy System/OpenAI/Google TTS behaviors.
- [ ] Add profile editor/UI tests confirming Music has **zero model pickers**, **zero mode selectors**, no built-in Default profile, proper selected-profile persistence, auto-save, edit/test, fallback on deletion, and a working enable switch.
- [ ] Add mock-HTTP tests for binary audio, JSON URL/base64, redirect/auth rules, async polling, pending/error/timeout, 401/429/5xx, cancellation, corrupt/oversized responses, and secret redaction. Assert media is recorded as audio in ToolFiles and ToolActivity.
- [ ] Update English string resources and the localization plan `plans/full-app-localization.md` with all new labels/descriptions/errors. Keep technical terms such as `API`, `Profile`, and provider names in English where translation would mislead.
- [ ] Update `docs/structure.md` for new components/files. Run targeted static/format checks first. **Do not run Gradle unless explicitly approved**; existing unrelated changes/build jobs must remain untouched.
- [ ] Document manual smoke checks: add ElevenLabs, Fish Audio, and Cartesia services; fetch/search/page voices for all three, pick a voice and test/sample/play/stop; switch back to existing TTS; create/select/test a custom music profile; ask a tool-capable LLM to generate music; verify playback and Media Storage; kill/reopen the app and confirm settings persisted.

## Acceptance criteria

- ElevenLabs, Fish Audio, and Cartesia are selectable and functional TTS engines in Speech Services, without a fake `ModelProvider` or regression to existing TTS. All three provide a working searchable/paginated **Fetch Voices** picker backed by the documented API, with manual Voice ID fallback.
- Feature Tools shows Music Generation, and its **only selection is a Custom Music Profile**; neither a model picker nor Default/Custom API-type chooser exists. Music profile configuration/test/save works without a model or Provider.
- Enabled, valid music profiles can generate playable music files attached to chat and saved to Media Storage; disabled/missing/invalid profiles cannot make a request.
- API keys are protected; errors and cancellation are safe; existing Image/Video generation profiles and old persisted configurations continue to work.
- Tests, visual/device checks, and any external API limitations are accurately recorded before marking this plan complete.

## Official API references (verify again before implementation)

- ElevenLabs Text-to-Speech: https://elevenlabs.io/docs/api-reference/text-to-speech/convert
- ElevenLabs List Voices (v2): https://elevenlabs.io/docs/api-reference/voices/search
- Fish Audio cloud API and examples: https://fish.audio/developers/
- Fish Audio official API index: https://docs.fish.audio/api-reference/introduction
- Fish Audio List Voice Models: https://docs.fish.audio/api-reference/endpoint/model/list-models
- Fish Audio TTS (JSON request / binary response): https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech
- Cartesia Text-to-Speech (Bytes): https://docs.cartesia.ai/api-reference/tts/bytes
- Cartesia Voice List (paginated): https://docs.cartesia.ai/api-reference/voices/list
- Fish Speech **self-hosted**, separate protocol: https://speech.fish.audio/inference/
- Optional example of a music API usable by a **custom profile** (not a built-in Music mode or required provider): https://elevenlabs.io/docs/api-reference/music/compose

## Implementation record (2026-10-27)

Implemented standalone engine discrimination with legacy JSON/Room fallback, encrypted speech keys, separate REST adapters for all three engines, on-demand searchable/paginated voice browsing with manual-ID fallback, and dispatch before Provider resolution. Optional preview downloads are intentionally not exposed. Supported initial output selections are ElevenLabs MP3, Fish MP3/WAV/Opus, and Cartesia WAV; raw PCM is not offered for standalone engines. Optional tuning is entered through validated advanced JSON; unsupported keys are rejected. The speed slider is shared by legacy and standalone engines. Standalone engines apply SpeechService.speed locally through the audio player, rather than relying on provider-specific synthesis parameters; existing API-speed behavior for OpenAI-compatible providers is unchanged.

Implemented a separate Custom-only Music profile library/active ID, profile-only tool controls, auto-save and duplication/deletion, protected profile-owned credentials, per-step tests without files or Provider/model identity, binary audio/JSON URL/base64 extraction, async polling, tool discovery/execution, and existing inline audio/Media Storage output. Music's model-facing schema currently offers only `prompt`; vendor constants and any duration/style/lyrics values belong in the profile request template or in the natural-language prompt. Imported Default music profiles are excluded and cannot be selected/executed. Library map keys are authoritative for imported profile IDs.

Music credentials are Android Keystore envelopes in persisted settings and exports. An envelope restored on another installation is retained but cannot be decrypted; the user must re-enter the Profile API key. Authentication headers must reference `{{api_token}}`; literal sensitive header values block auto-save and execution. No automatic Bearer header is added for Music. Requests and download redirects never forward a Provider credential. Request bodies/headers are omitted from music debug logging and tool errors.

Added tests: `StandaloneSpeechTest`, `MusicGenerationTest`, `MusicCredentialTest`, `MusicProfilesUiTest`, `SpeechVoiceSheetTest`, plus standalone engine/key round trips in `SpeechServiceStorageTest`. These are **not yet executed**. Existing System/OpenAI/Google tests remain in place. Checklist verification items remain open until their tests/device checks pass.

### Verification and smoke checklist

- Static checks passed: Kotlin PSI syntax parsing, string-resource references, XML parsing, checked dependency direction and `git diff --check`. These checks do not establish Android/Compose type correctness. The localization checker reports missing translations for the new English keys across all 13 non-English locales; translation is deferred as permitted by AGENTS.md.
- Gradle lint/unit/instrumentation tests and app build: not run; this plan requires explicit authorization. After authorization, run lint and tests together, e.g. `./gradlew --offline lint test`, and then the targeted device tests on a provisioned emulator.
- No live paid synthesis or music API requests were made. Official request/list documentation was rechecked before implementation, including Cartesia's 2026-08-14 version and Bearer authentication.
- On a device, add each standalone speech engine, enter its key/model, open voices, search, Load more, choose a voice, test/play/stop, retry offline/invalid-key cases, then switch back to System/OpenAI/Google.
- Create a Music profile, set a protected credential and request-chain mapping, test each step, select it and enable Music Generation. Ask a tool-capable chat model for music; verify inline playback/download, Media Storage, and restart persistence.
- Repeat with async polling, failed jobs, cancellation/chat switching and a temporary chat. Verify no failed/cancelled output remains, deleted profiles disable generation, and invalid/unconfigured tools make no request.
- Restore settings on the same installation and another installation. Confirm keys remain masked and the latter requires re-entering Music credentials. Complete the coordinated translation follow-up listed in `full-app-localization.md`.

### Debug build and editor review (user-authorized)

Ran `./gradlew --offline :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --continue --max-workers=2 --console=plain` after the user requested a debug build.

- `:app:assembleDebug` passed and produced `app/build/outputs/apk/debug/app-debug.apk`. Main Kotlin/Java sources compiled, including the new TTS/Music implementation.
- The combined command exited with failures in lint/unit-test tasks. Offline cache lacks `lint-gradle:32.3.0` and several Robolectric/Espresso runtime dependencies. Unit-test compilation also reports errors in existing unrelated tests, including AppLanguageSelectionTest, ChatTextSelectionDismissalTest, DrawerRemoteMachineContextMenuTest, EdgeToEdgeContentTest, FullBackupRestoreResilienceTest and terminal/UI tests. Unit tests did not execute.
- Two nullable Java interop warnings remain in ToolFiles at the existing file-parent checks.
- Reviewed the standalone speech editors: API key precedes synthesis model/voice, engine-specific Voice labels omit the OpenAI alloy example, advanced JSON is collapsed for empty configuration, and redundant standalone help text is removed. The voice sheet uses the shared list container, theme colors and Fluent selection icon, with no empty subtitle row.
- No connected Android device/emulator was available; runtime visual/playback checks remain pending.

### Feature Tools ordering and coordinated localization (2026-10-28)

Moved Music Generation below Video Generation. Removed the inline red missing-model/profile setup warnings from the three tool cards while preserving execution validation. Added all 21 new speech/music resource keys across all 13 supported non-English locales. Localization coverage is now 100% in every locale, with no checker errors; the earlier translation follow-up is resolved.

### Automatic synthesis model catalogs (2026-10-28)

Speech Services now loads model choices after an API key is entered, with debounce, cancellation on engine/key changes, refresh, and manual ID fallback. After a successful catalog response, the first returned synthesis model is automatically selected **only if the model field is blank**. An existing saved choice or a manually entered ID is never overwritten by a catalog response. Changing the engine or API key clears the previous model selection so the first model from the new catalog can be chosen. If the catalog fails or returns no models, the model remains unset and manual ID entry stays available. Catalog results are not stored with credentials.

- ElevenLabs: authenticated `GET https://api.elevenlabs.io/v1/models`; parse the top-level array and include only `can_do_text_to_speech == true`. Source: https://elevenlabs.io/docs/api-reference/models/list
- Cartesia: authenticated/versioned `GET https://api.cartesia.ai/models?capability=tts`; include `capability == tts`. `modes` applies to STT and must not filter TTS entries. Source: https://docs.cartesia.ai/api-reference/models/list
- Fish Audio: the documented `/model` endpoint lists **voices**, not synthesis engine models. The public OpenAPI schema declares the allowed `/v1/tts` `model` header enum; fetch that enum from https://docs.fish.audio/api-reference/openapi.json without attaching an API key. This is the published supported model catalog, not an account entitlement check. Keep manual IDs available if the schema changes. Sources: https://docs.fish.audio/llms.txt and https://docs.fish.audio/api-reference/endpoint/model/list-models

Added `SpeechModelCatalogTest` for shape/filtering, auth/version/query correctness, public-schema credential isolation, and sanitized HTTP errors. Tests have not been executed; the previously identified Gradle test dependency/compilation blockers remain.

### Spoken text cleanup and bounded speech requests (2026-10-28)

TTS now removes decorative symbols and colons from parsed visible text, while keeping letters/accents, numbers and sentence punctuation. Symbol-only messages are ignored. External engines and Provider-based TTS receive balanced chunks of 3–5 sentences; messages shorter than three sentences remain a single request. A 4,000-character cap can split unusually long groups/sentences further without breaking surrogate pairs. Each clip finishes playback before the next request is sent. Stop cancels the sequence and active request; playback errors stop subsequent chunks. Android System TTS keeps its own utterance queue.

Executed `SpeechChunksTest` independently with the cached Kotlin/CommonMark/JUnit dependencies: all 3 tests passed (symbol/colon cleanup, 3–5-sentence grouping without lost text, length/Unicode bounds). This did not build the Android app or execute Android playback tests. Kotlin syntax parsing and `git diff --check` also passed.

### Configurable speech chunk size (2026-10-28)

Replaces the earlier fixed 3–5 sentence grouping. Speech Editor now exposes a positive sentence count for external TTS requests, defaulting to 10 per Speech Service. The additive `SpeechService.sentencesPerChunk` field is serialized for backup/export and persisted in structured engine settings without a Room migration. Existing JSON/rows without the field default to 10. Android System TTS retains its own utterance queue.

Sentence boundaries are periods and line breaks (including CRLF); adjacent boundary runs such as a period followed by whitespace/newlines count once. Decimal points inside numbers are preserved. The independent character cap still protects requests containing unusually long sentences. New labels/help text are translated across all supported locales.

Standalone JUnit coverage for text cleanup and configurable splitting passed all 4 tests. Added repository round-trip/legacy-row and editor Save coverage; those Android-dependent tests remain unexecuted because of the previously reported environment blockers.

### TTS status-bar notification (2026-10-28)

Added a session-scoped, silent playback notification for Android System TTS and external API audio. Playback-start callbacks show the service name and a localized Stop action; completion/error/Stop remove the notification. It remains stable between chunks and never includes the spoken transcript. Each Stop PendingIntent is scoped to its playback session, preventing stale notifications from cancelling a newer request. The dynamic receiver is app-private and unregistered when playback ends. Existing application notification permission handling is reused; denied notification permission does not interrupt audio.

Added SpeechPlaybackNotificationTest for posting/removing the notification and rejecting an old Stop action. Android/Robolectric tests remain unexecuted due to previously reported test-environment blockers; static Kotlin syntax checks, localization and AAPT2 resource compilation passed. This change adds a status notification and does not introduce a foreground playback service.
