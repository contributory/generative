# Progress

## Integrations / AI models

- [x] Provider model type → `ModelProvider`; provider entities, DAO, repository and service identifiers renamed to `ModelProvider`, `ModelRepository` and `AiService`.
- [x] Renamed AI service and SDK types; no legacy acronym remains in active source identifiers.
- [x] Account-provider contract and registry: `data/adapter/AccountProviderAdapter.kt`, `AccountProviderAdapterRegistry.kt` → `integrations/languagemodels/account/`.
- [x] OpenAI Codex, Claude Code, Antigravity and GitHub Copilot adapters moved under `integrations/languagemodels/`.
- [x] SDK attachment conversion moved to `integrations/languagemodels/sdk/SdkAttachmentContent.kt`.
- [x] Added shared `LanguageModelAdapter` request contract and registry.
- [x] Moved OpenAI Chat Completions, OpenAI Responses, Anthropic Messages and Gemini request/stream implementations from `OfficialAiSdk` into their protocol adapter files.
- [x] Moved Ollama chat request construction and stream parsing into `OllamaAdapter.kt`.
- [x] Moved standard-provider model catalog discovery into `ModelCatalogAdapter.kt`.
- [x] Renamed Room tables to `model_providers` and `provider_models`; added migration 8→9. Historical schema files retain their original table names; schema 9 export awaits a build.
- [x] Moved connection testing for standard providers into their protocol adapters.
- [x] Move Ollama cache reads and model discovery behind the integration boundary; UI now receives cached/discovered models through `ConfigurationViewModel` → repository → `AiService` → integration registry.
- [x] Move domain/data model declarations from `data/model/` into `shared/model/` and update Kotlin references across `newApp/src/`.
- [x] Align the structure map with the requested `shared/model/` package.
- [x] Standardize all integration packages and directories under `integrations/languagemodels/`; update Kotlin package declarations, imports, tests, `structure.md`, and this progress log. No references to the old package remain in `newApp/`.
- [x] `./gradlew :newApp:compileDebugKotlin` passes after the package rename.
- [x] `./gradlew :newApp:compileDebugKotlin` succeeds.
- [ ] Migrate stale unit tests from the old `OfficialAiSdk` API to the new protocol adapters; `:newApp:testDebugUnitTest` currently fails at test compilation because `AuthenticationMethodsTest`, `OfficialAiSdkTest`, and `ReasoningSettingTest` still call removed SDK methods (`testConnection`, `streamOpenAi`, `streamAnthropic`, `streamGoogle`).

## Scope

Only `newApp/` was edited. `app/` remains untouched as fallback/reference. No build or commit was performed.
