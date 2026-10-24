# Account Provider Correctness TODO

Static audit of the Antigravity, OpenAI Codex, Claude Code, and GitHub Copilot account providers.

Reference baseline: [CLIProxyAPI v8.0.41 (`8289ec4`)](https://github.com/router-for-me/CLIProxyAPI/tree/8289ec4c71ad914d0612c2776352b76ba3d355a6). CLIProxyAPI has no first-party Copilot provider, so the Copilot catalog and protocol findings also use the current opencode and Kilo implementations as supplementary references.

The initial audit did not run builds, tests, or live provider requests. Findings marked **static** follow directly from the source control flow. Findings marked **parity** are confirmed implementation differences whose production impact still needs provider-backed verification. Checked items have been implemented and reviewed against the reference source; this does not claim that their tests or live-provider verification have run.

## P0 — Correctness and credential safety

### [x] 1. Make Copilot model resolution fail closed

**Status:** implemented and source-reviewed against opencode/Kilo; regression tests added but not executed. The reference throws on catalog HTTP failure and derives models only from the current remote snapshot, matching the new fail-closed behavior.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/githubcopilot/GitHubCopilotAdapter.kt:75-90`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt:187-199`

**Current behavior:**

- An empty or unavailable catalog falls back to inferring the protocol from the model name.
- A model removed from the account catalog can therefore remain routable.
- `runCatching` around the suspending catalog request can also swallow `CancellationException`.
- `copilotRefreshDoesNotKeepRemovedModelsOrRouteThemUsingOldAccountMetadata` expects an unavailable-model error, but the current control flow returns a provider instead.

**Required changes:**

- Return an unavailable-model error when a successfully loaded catalog does not contain the selected model.
- Propagate catalog request failures instead of silently using stale account metadata or name-based routing.
- Rethrow `CancellationException`; do not convert coroutine cancellation into a catalog miss.
- Use `supported_endpoints` as the authoritative protocol source.
- Restrict any name-based fallback to an explicit, documented compatibility path.

**Acceptance tests:**

- The existing removed-model test passes without changing its intended behavior.
- Empty catalogs and catalog transport failures do not route removed models.
- Cancellation during catalog loading remains cancelled and does not start generation.
- Models with unchanged catalog entries retain their existing protocol behavior.

### [x] 2. Serialize Codex token refresh per credential

**Status:** implemented and source-reviewed against `internal/auth/codex/openai_auth.go`; concurrency and cancellation regression tests added but not executed. The process-wide per-credential lock covers multiple adapter instances, and the token is reloaded after lock acquisition so later callers reuse the rotated credential instead of issuing another upstream refresh.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openaicodex/OpenAICodexAdapter.kt:215-229`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/account/OAuthAccountAdapter.kt`
- `app/src/test/java/org/starfall/generative/AccountAdapterTest.kt`

**Current behavior:**

`ensureToken` performs load, expiry check, network refresh, and save without a mutex or singleflight. Concurrent requests can replay the same rotating refresh token and overwrite newer credential state. Codex overrides the shared OAuth path, so the shared `refreshLock` does not protect it.

**Required changes:**

- Add per-credential refresh serialization or singleflight.
- Reload and recheck token expiry after acquiring the lock.
- Ensure only one refresh is in flight for a credential and all waiters use its result.
- Preserve cancellation and avoid leaving stale credentials after a failed refresh.

**Acceptance tests:**

- Multiple coroutines refreshing one expired Codex credential produce exactly one token-endpoint request.
- All callers receive the same refreshed state.
- A second caller that arrives after the first refresh does not refresh again.
- Refresh failure does not overwrite valid newer credentials.

## P1 — Upstream protocol compatibility

### [x] 3. Decouple caller streaming from upstream streaming for Codex

**Status:** implemented and source-reviewed against `codex_executor_execute.go`; SDK and manual/MCP regression fixtures updated but not executed. Both paths now require upstream SSE, while non-stream SDK events are buffered and manual/MCP callbacks are suppressed until the aggregate response is returned. OpenAI Java 4.63.1 was also checked to confirm that `createStreaming` writes `stream=true` and the SSE Accept header.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openairesponses/OpenAIResponsesAdapter.kt:108-120`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openaicodex/OpenAICodexAdapter.kt`
- `app/src/test/java/org/starfall/generative/AccountTokenUsageTest.kt`

**Current behavior:**

When `supportStream=false`, Generative sends an ordinary non-stream Responses request and expects complete JSON. CLIProxyAPI instead forces upstream `stream=true` for Codex, consumes SSE, and aggregates the result for a non-stream caller. Existing idealized non-stream fixtures only test the local parser.

**Required changes:**

- Force the required upstream transport independently of the caller-visible response mode.
- Aggregate upstream SSE into the existing non-stream result path when necessary.
- Preserve text, reasoning, tool output, usage, errors, and terminal-event handling.
- Apply the same rule to both SDK and manual/MCP request paths where applicable.

**Acceptance tests:**

- A non-stream Codex caller still receives one completed result while the captured upstream request uses streaming.
- SSE aggregation handles terminal usage without a trailing blank line.
- Stream and non-stream callers produce equivalent content and usage from equivalent upstream events.
- Error and cancellation propagation remain intact.

### [x] 4. Apply Antigravity special-model non-stream transport rules

**Status:** implemented and source-reviewed against `antigravity_executor_execute.go`; regular-chat and forced-SSE fixtures updated but not executed. The model predicate matches the reference (Claude, `gemini-3-pro`, and `gemini-3.1-flash-image`), and both generation paths reuse the existing SSE aggregator without changing caller-visible non-stream delivery or adding endpoint fallback.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityAdapter.kt`
- `app/src/test/java/org/starfall/generative/AccountTokenUsageTest.kt`

**Current behavior:**

A false logical stream flag selects `:generateContent`. The reference still uses upstream SSE for special non-stream cases, including Claude, `gemini-3-pro`, and `gemini-3.1-flash-image`, then aggregates the result.

**Required changes:**

- Reproduce the reference's model-family transport selection.
- Keep caller-facing non-stream semantics while consuming upstream SSE where required.
- Do not introduce cross-endpoint generation fallback that the reference intentionally removed.

**Acceptance tests:**

- Request-capture tests verify the upstream method and streaming flag for each special model family.
- Aggregated non-stream responses preserve text, tools, usage, and errors.
- Ordinary Gemini models retain their existing transport behavior.

### [x] 5. Update Antigravity client identity and onboarding wire contract

**Status:** implemented and source-reviewed against `internal/auth/antigravity/auth.go` and `internal/misc/antigravity_version.go`; exact-payload/header tests updated but not executed. Load/onboard metadata, tier/project extraction, Hub 2.24.0 fallback identity, long onboarding UA, and daily default now match the reference. Onboarding polls by repeating `POST /v1internal:onboardUser` up to five times; an inferred `GET /v1internal/{operation.name}` was removed after reference review because it can produce an entity-not-found response. The previous sandbox default remains recognized as a legacy built-in value so existing installs are not reclassified as custom endpoints.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityModels.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/api/ModelApiType.kt`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt:93-110`

**Current differences:**

- The static user agent is `antigravity/1.23.2 windows/amd64`, while metadata describes macOS.
- The reference documents rejection of newer models below Antigravity `2.23.0` and uses `2.24.0` as its fallback version.
- Onboarding uses `tierId`, camelCase metadata, and `PLATFORM_UNSPECIFIED`; the reference uses `tier_id` and snake_case onboarding metadata.
- Load metadata differs from the reference's minimal `ideType=ANTIGRAVITY` payload.
- The local fallback tier is `legacy-tier`; the reference considers `currentTier` and falls back to `free-tier`.
- The default daily endpoint is `daily-cloudcode-pa.sandbox.googleapis.com`; the reference uses `daily-cloudcode-pa.googleapis.com`.

**Required changes:**

- Use a coherent client identity across user agent and metadata.
- Meet the reference's documented minimum version and keep the version update mechanism maintainable.
- Align onboarding/load payloads and tier selection with the reference wire contract.
- Review the default daily endpoint and preserve explicit custom endpoint behavior.

**Acceptance tests:**

- Capture exact load and onboarding requests for default-tier, current-tier, and fallback-tier cases.
- Verify the successful onboarding base URL is persisted and used for generation.
- Verify user agent, metadata platform, and version are internally consistent.
- Do not treat camelCase/snake_case spelling alone as proof of live rejection; verify semantics with sanitized real responses where possible.

### [x] 6. Port Claude Code request and response cloaking

**Status:** implemented and source-reviewed against `claude_executor_request.go`, `claude_executor_cloaking.go`, `helps/claude_mcp_alias.go`, and the current reference device profile; exact-wire JSON/SSE and SDK regression tests added but not executed. Third-party tools now use deterministic request-local MCP aliases with response restoration before `providerTurn`, while valid MCP and typed server tools pass through. Caller system blocks move to mid-conversation system turns or legacy reminders; advisor history keeps them top-level to preserve message indices. The CLI identity is 2.1.280 and beta selection follows the model/thinking fields emitted by the app. Anthropic Java 2.62.0 was checked for typed system-message support.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/claudecode/ClaudeCodeAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/anthropic/ClaudeCodeCloaking.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/anthropic/ClaudeCodeToolAliases.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/anthropic/AnthropicMessagesAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/home/shared/logic/ToolRequestService.kt`
- `app/src/test/java/org/starfall/generative/ClaudeCodeCloakingTest.kt`
- `app/src/test/java/org/starfall/generative/ProviderAdaptersTest.kt`

**Current differences:**

- Third-party tool names are forwarded unchanged.
- Claude Code identity is prepended while arbitrary caller system blocks remain at top level.
- The pinned CLI user agent and beta profile are older and less feature-aware than the reference.
- The reference aliases non-MCP tools, updates declarations/history/tool choice, reverses aliases in responses, and relocates caller system prompts.

**Required changes:**

- Add request-local, bidirectional tool alias mapping across declarations, message history, tool choice, non-stream responses, and SSE responses.
- Relocate caller system prompts according to the reference's OAuth Claude Code behavior.
- Update the Claude Code user agent and feature-dependent beta profile coherently.
- Apply equivalent behavior to SDK and manual/MCP HTTP paths.

**Acceptance tests:**

- Tool calls survive a complete request/response round trip with their original public names.
- Streaming and non-stream responses reverse aliases identically.
- Existing MCP-native tools are not incorrectly renamed.
- Caller system content appears only in the intended wire location.

### [x] 7. Remove unnecessary Claude OAuth scope

**Status:** implemented and source-reviewed against `internal/auth/claude/anthropic_auth.go`; authorization URL and real-adapter refresh regression tests added but not executed. The authorization scope is now exactly `user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload`. Existing-token refresh remains independent of authorization scope and does not send a `scope` field.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/claudecode/ClaudeCodeAdapter.kt`
- `app/src/test/java/org/starfall/generative/ClaudeCodeOAuthTest.kt`

**Required changes:**

- Remove `org:create_api_key` unless a concrete app feature requires it.
- Keep the scopes required for profile, inference, Claude Code sessions, MCP servers, and file upload.
- Confirm existing credentials refresh correctly after the authorization-scope change.

**Acceptance tests:**

- The authorization URL contains only the intended scopes.
- New sign-in and existing-token refresh remain functional.

## P2 — Request normalization and model catalog quality

### [x] 8. Align Antigravity generation normalization by model family

**Status:** implemented and source-reviewed against `antigravity_executor_request.go`; helper and exact-envelope regression tests added but not executed. Resolved Claude models keep `maxOutputTokens`, receive `VALIDATED`, and sanitize declaration schemas with reversible placeholders; other models remove only that token limit. Session IDs preserve explicit values and otherwise derive stably from account + first-user text, with no global map and no automatic session for image models. Image envelopes now use `image_gen` identity, and tool-call history is not schema-sanitized.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityRequestNormalization.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityModels.kt`
- `app/src/test/java/org/starfall/generative/AntigravityRequestNormalizationTest.kt`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt`

**Required changes:**

- Set Claude `toolConfig.functionCallingConfig.mode=VALIDATED` where required.
- Remove non-Claude `generationConfig.maxOutputTokens` to match the reference's deliberate behavior.
- Add a stable `request.sessionId` with the same lifetime semantics as the reference.
- Keep model-specific rules explicit rather than applying one generic Gemini envelope policy to every family.

**Acceptance tests:**

- Golden request tests cover Claude, ordinary Gemini, and special Gemini model families.
- Tool declarations and tool-call history remain valid after normalization.
- Session IDs remain stable for the intended conversation/cache scope and do not leak across unrelated accounts.

### [x] 9. Reuse stable Codex session/cache identity

**Status:** implemented and source-reviewed against Codex cache/request handling; regression tests added but not executed. `Session_id`, `Conversation_id`, and `prompt_cache_key` now share explicit caller identity or a stable UUID derived from credential + first-user message ID. SDK generation and every manual tool round use the same scope, independent conversations/accounts diverge, and request-only scope metadata is removed before dispatch. There is no process-global conversation map.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openaicodex/OpenAICodexAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openaicodex/OpenAICodexSession.kt`
- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openairesponses/OpenAIResponsesAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/home/chat/logic/tool/ToolChatViewModel.kt`
- `app/src/main/kotlin/org/starfall/generative/home/shared/logic/ToolRequestService.kt`
- `app/src/test/java/org/starfall/generative/AccountAdapterTest.kt`

**Current behavior:**

`Session_id` and `Conversation_id` are regenerated for every prepared request, and `Session_id` also becomes the prompt cache key. The reference reuses session/cache identity.

**Required changes:**

- Define the intended conversation/account scope for Codex session identity.
- Reuse identifiers within that scope without sharing them across unrelated credentials or conversations.
- Keep request payload cache keys consistent with the transmitted headers.

**Acceptance tests:**

- Requests in one conversation reuse the intended identity.
- Independent conversations or credentials do not accidentally share identity.
- Prompt-cache key and session headers remain consistent.

### [x] 10. Filter Copilot catalog entries by account policy and usability

**Status:** implemented and source-reviewed against current opencode/Kilo catalog loaders; regression fixtures updated but tests not executed. Disabled policy, picker-disabled, non-chat, incomplete serving-limit/capability, and unsupported-endpoint entries are rejected. Missing optional display/aggregate-context metadata remains tolerated, explicit chat/Messages/Responses routing is preserved, and refreshed catalogs fail closed for removed models.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/githubcopilot/GitHubCopilotAdapter.kt`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt`
- `app/src/test/java/org/starfall/generative/AccountAdapterTest.kt`
- `app/src/test/java/org/starfall/generative/AccountTokenUsageTest.kt`
- `app/src/test/java/org/starfall/generative/McpToolLoopTest.kt`

**Current behavior:**

The catalog accepts essentially every entry with an ID. Tests explicitly retain disabled models, picker-disabled models, and embedding models. This can expose entries that cannot serve the selected chat use case.

**Required changes:**

- Reject account-policy-disabled entries.
- Exclude non-chat model types such as embeddings from text-generation pickers.
- Honor `model_picker_enabled` at the appropriate catalog or UI boundary.
- Validate required capabilities and limits without rejecting otherwise usable models solely for missing optional metadata.
- Derive protocol from `supported_endpoints`; extend explicit routing only where supported by evidence.

**Acceptance tests:**

- Disabled and embedding entries are not offered for chat.
- Picker-disabled entries follow the chosen product policy consistently.
- Valid chat, Anthropic Messages, and Responses models remain discoverable.
- Removed models are neither displayed nor routable after refresh.

### [x] 11. Scope Copilot protocol headers correctly

**Status:** implemented and source-reviewed against supplementary protocol routing; regression tests added but not executed. `anthropic-beta` is now emitted only for Anthropic Messages and removed from Chat Completions/Responses even if inherited from provider headers. Explicit `supported_endpoints` continues to win; unsupported endpoint entries were rejected in item 10, and no unsupported name-based route was added.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/githubcopilot/GitHubCopilotAdapter.kt`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt`

**Required changes:**

- Send `anthropic-beta` only on Anthropic Messages requests.
- Keep OpenAI Chat Completions and Responses headers limited to their required Copilot/client identity fields.
- Review protocol routing for non-Claude/non-GPT models instead of assuming Chat Completions for every unmatched name.

**Acceptance tests:**

- Captured requests for all three protocols contain only the intended beta and API headers.
- Explicit `supported_endpoints` always wins over name heuristics.

### [x] 12. Make the Codex client version configurable per provider

**Status:** implemented and source-reviewed against `cmd/fetch_codex_models/main.go`; regression tests added but not executed. The default is now `0.159.0`, matching the reference fetcher's actual constant. ProviderEditor can override or clear it to restore the default. The effective value is URL-encoded into the catalog `client_version` query and used in the Codex User-Agent, with validation against unsafe header/query input. Structured provider metadata persists the setting without a Room schema migration, and catalog refresh identity includes it.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/openaicodex/OpenAICodexAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/provider/ProviderConfiguration.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/provider/ProviderConfigurationExtensions.kt`
- `app/src/main/kotlin/org/starfall/generative/features/providers/editor/ui/ProviderEditor.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/storage/ProviderStorageMapper.kt`
- `app/src/test/java/org/starfall/generative/AccountAdapterTest.kt`
- `app/src/test/java/org/starfall/generative/ConfigurationTest.kt`
- `app/src/test/java/org/starfall/generative/Phase4StorageTest.kt`

**Acceptance tests:**

- An unset or blank version sends `client_version=0.159.0` and the matching User-Agent.
- A valid per-provider override reaches both paths without affecting other providers.
- ProviderEditor rejects invalid values and the adapter rejects them before dispatch into headers/query parameters.
- Custom values survive metadata, resolved-provider, export/import, and credential-preserving storage round trips; clearing the value removes the stored override.

### [x] 13. Expose optional Antigravity build and per-account project settings

**Status:** implemented and source-reviewed against `internal/misc/antigravity_version.go`, `internal/auth/antigravity/auth.go`, and `internal/runtime/executor/antigravity_executor_request.go`; regression tests added but not executed. ProviderEditor exposes optional client version and changelist while Antigravity's built-in route remains unchanged. Blank values preserve version `2.24.0` and its verified CL `1000335682`; a custom version without a CL omits `cl` instead of borrowing another release's identity. Version reaches onboarding `ide_version` and short/long User-Agents, and version/CL participate in catalog refresh identity. Each OAuth account can also have an optional project ID beside the selected-account controls: explicit values take precedence over discovery for catalog and generation, while blank values retain onboarding. Project overrides are stored per credential in structured provider metadata and removed with their account; they are not written into encrypted authentication payloads or the token store. Settings persist without a Room migration. Codex and Antigravity literal fields now use raw IME options, and only canonical English strings were added; the earlier Vietnamese-only addition was reverted in accordance with AGENTS.md. The reference's custom `base_url` is a private-deployment mechanism, not a general Antigravity-compatible provider option, so it is not exposed by this change.

**Relevant code:**

- `app/src/main/kotlin/org/starfall/generative/integrations/modelapis/antigravity/AntigravityAdapter.kt`
- `app/src/main/kotlin/org/starfall/generative/features/providers/editor/ui/ProviderEditor.kt`
- `app/src/main/kotlin/org/starfall/generative/features/providers/shared/ui/ProviderConnectionFields.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/provider/ProviderConfiguration.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/provider/ProviderConfigurationExtensions.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/model/provider/ProviderOAuthAccount.kt`
- `app/src/main/kotlin/org/starfall/generative/shared/storage/ProviderStorageMapper.kt`
- `app/src/test/java/org/starfall/generative/AccountModelDiscoveryTest.kt`
- `app/src/test/java/org/starfall/generative/ConfigurationTest.kt`
- `app/src/test/java/org/starfall/generative/Phase4StorageTest.kt`
- `app/src/test/java/org/starfall/generative/ProviderOAuthAccountsTest.kt`

**Acceptance tests:**

- Default requests retain the verified `2.24.0`/`1000335682` identity and existing payload behavior.
- Antigravity remains on its built-in route in ProviderEditor; no general compatibility endpoint is advertised.
- Custom version and matching CL reach short/long User-Agents and onboarding metadata consistently.
- A custom version with no CL does not claim the default release's changelist.
- Optional values survive metadata, resolved-provider, export/import, and credential-preserving storage round trips.
- A selected account's explicit project overrides token discovery for catalog and generation without an onboarding request; blank entries retain discovery.
- Removing an OAuth account removes only its project override and leaves the selected remaining account's override intact.
- No partial non-English localization change remains; literal fields disable capitalization, autocorrection, and IME composition.

## Regression coverage status

The checked coverage below is implemented but has not been executed; project instructions prohibit build/test execution unless explicitly requested.

- [x] Exercise the Codex-specific refresh override concurrently; do not rely only on the shared OAuth adapter test.
- [x] Replace idealized account-provider fixtures with sanitized upstream-shaped fixtures for transport and envelope behavior.
- [x] Capture upstream requests for every account provider in stream and non-stream modes, with exact-wire golden checks for the changed normalization/cloaking rules.
- [x] Cover cancellation during OAuth refresh, catalog loading, and SSE aggregation; synchronous response restoration has separate round-trip coverage.
- [x] Verify both SDK and manual/MCP paths where they build requests independently.
- [x] Capture default and overridden Codex client versions in catalog and generation headers, plus validation and storage round trips.
- [x] Capture Antigravity version/changelist across load, onboarding, and catalog requests; verify unknown releases omit stale CL metadata.
- [x] Capture per-account Antigravity project overrides in catalog and generation normalization; verify storage round trips and account-deletion cleanup.
- [ ] Run the existing account adapter, model discovery, token usage, OAuth persistence, and MCP tool-loop tests together after the fixes (awaiting explicit permission to build/test).

## Checked non-issues — do not regress or “fix” without new evidence

- Codex refresh already sends `scope=openid profile email`.
- Codex normalization already strips unsupported token-limit/sampling fields and supplies instructions, cache key, reasoning include, and summary.
- `OfficialProviderSdk.baseUrl` strips `/responses`; there is no Codex double-endpoint defect.
- Missing encrypted-reasoning replay is not established as a native Responses defect.
- Antigravity aliases such as `gemini-pro-agent` and `gemini-3-flash-agent` occur in reference tests; their mappings are not reported as defects.
- Shared non-Codex OAuth refresh already has a refresh lock.
- Effective per-model stream configuration is propagated before SDK dispatch.
- Copilot JSON device flow and client ID `Ov23li8tweQw6odWQebz` match opencode/Kilo and are not defects.
- Copilot API version `2026-06-01` differs from newer opencode code but is also used by Kilo; the version difference alone is not proof of breakage.
- Claude direct-browser-access header remains present in the current reference and is not a parity defect.
- Copilot max-token normalization is not reported as a defect because upstream evidence is insufficient.
