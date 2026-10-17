# Progress

## Integrations / AI models

- [x] Provider model type → `ModelProvider`; provider entities, DAO, repository and service identifiers renamed to `ModelProvider`, `ModelRepository` and `AiService`.
- [x] Move the model API integration boundary to `integrations/modelapis/` and name its contract/request/registry `ModelApiAdapter`, `ModelApiRequest`, and `ModelApiAdapterRegistry`; rename the API selector enum to `ModelApiType` while preserving serialized enum values and legacy DB enum names.
- [x] Renamed AI service and SDK types; no legacy acronym remains in active source identifiers.
- [x] Account-provider contract and registry: `data/adapter/AccountProviderAdapter.kt`, `AccountProviderAdapterRegistry.kt` → `integrations/modelapis/account/`.
- [x] OpenAI Codex, Claude Code, Antigravity and GitHub Copilot adapters moved under `integrations/modelapis/`.
- [x] SDK attachment conversion moved to `integrations/modelapis/sdk/SdkAttachmentContent.kt`.
- [x] Added `ModelApiAdapter`, `ModelApiRequest`, and `ModelApiAdapterRegistry`; all represent the selected model API protocol, not a language-model entity.
- [x] Moved OpenAI Chat Completions, OpenAI Responses, Anthropic Messages and Gemini request/stream implementations from `OfficialProviderSdk` into their protocol adapter files.
- [x] Moved Ollama chat request construction and stream parsing into `OllamaAdapter.kt`.
- [x] Moved standard-provider model catalog discovery into `ModelCatalogAdapter.kt`.
- [x] Renamed Room tables to `model_providers` and `provider_models`; added migration 8→9. Historical schema files retain their original table names; schema 9 export awaits a build.
- [x] Moved connection testing for standard providers into their protocol adapters.
- [x] Move Ollama cache reads and model discovery behind the integration boundary; UI now receives cached/discovered models through `ConfigurationViewModel` → repository → `AiService` → integration registry.
- [x] Move domain/data model declarations from `data/model/` into `shared/model/` and update Kotlin references across `newApp/src/`.
- [x] Align the structure map with the requested `shared/model/` package.
- [x] Standardize all integration source directories under `integrations/modelapis/`; use the valid Kotlin package identifier `integrations.modelapis` and update source/test imports and docs. No references to the previous integration package or adapter contract remain.
- [x] `./gradlew :newApp:compileDebugKotlin :newApp:compileDebugUnitTestKotlin` succeeds after the integration package and symbol rename.
- [x] Migrate provider integration tests to exercise the new protocol adapters directly through `ProviderAdaptersTestSupport`; unit-test source compilation succeeds.
- [x] Targeted provider/OAuth/model-catalog/reasoning tests pass with `:newApp:testDebugUnitTest` after the integration rename.
- [x] Move cross-feature `AttachmentResolver` into `shared/files/`, generation events into `shared/model/GenerationEvent.kt`, and OAuth browser/session plumbing into `shared/auth/`.
- [x] Split the `shared/model/` AI model aggregate into focused files, with each model/enum declared in a matching filename (for example `LanguageModel.kt`, `ModelProvider.kt`, and `ModelConfiguration.kt`).
- [x] Move `ProviderConfiguration.reorderedModels` into `ProviderConfigurationExtensions.kt`, keeping `ProviderConfiguration.kt` focused on its matching data model.
- [x] Move provider catalog metadata parsing to `integrations/modelapis/ModelCatalogMetadata.kt`, and account-provider OAuth implementation/token state under `integrations/modelapis/account/`.
- [ ] Follow-up architecture cleanup: `ModelApiType` still contains account-integration entries (`ANTIGRAVITY`, `GITHUB_COPILOT`, `OPENAI_CODEX`, `CLAUDE_CODE`). Move these provider identities into a separate provider-integration field/type so `ModelApiType` contains only API protocols.

## Scope

Only `newApp/` was edited; `app/` remains untouched as fallback/reference. Compile and test-source compilation were run. No commit or push was performed.
- [x] Replaced the aggregate `AiModels.kt` with model/enum files whose names match their primary declarations; moved model helper functions into extension files.
- [!] Build was not re-run successfully after the split because Gradle still references legacy source paths that are absent from the requested target structure; those out-of-structure files were not restored or modified.
- [x] Group related shared models into `api/`, `auth/`, `generation/`, `language/`, and `provider/` subpackages; update the packages/imports in the target integration files and keep the tree aligned with `structure.md`.
- Move `OAuthBrowserActivity` from `ui/auth/` to root `shared/ui/`, update its Kotlin package and auth-helper imports, and align the manifest and `OAuthBrowser` reference with the new location.

## Settings / About screen

- [x] Added `features/settings/about/screen/AboutScreen.kt` following `structure.md`; its Kotlin package is `org.starfall.multigateway.features.settings.about.screen`.
- [x] Migrated About information, installed version/version code, GitHub source link, license details, and GitHub release checking/APK links from `app/` into the new feature.
- [x] Added the small GitHub release API service used by the About screen under `data/service/GitHubReleaseService.kt`.
- [!] `AboutScreen` is not wired into settings/navigation because `newApp/` does not yet contain the settings/navigation flow or the destination screens described by `structure.md`.
- [!] `:newApp:compileDebugKotlin` remains blocked by existing rewrite-wide missing packages/types and stale imports in other files; this run did not provide a clean whole-module verification.
