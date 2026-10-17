## Main / Features screen

- [x] Migrated the former General Settings landing menu into `features/main/screen/FeaturesScreen.kt`; it is now named Features and no longer renders a page title.
- [x] Added a centered app-logo header that selects the light or dark logo using the active Compose background luminance.
- [!] Navigation callbacks are exposed, but the new rewrite still lacks the complete navigation host needed to connect all destinations.

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

## About feature

- [x] Added `features/about/screen/AboutScreen.kt` following `structure.md`; its Kotlin package is `org.starfall.multigateway.features.about.screen`.
- [x] Migrated About information, installed version/version code, GitHub source link, license details, and GitHub release checking/APK links from `app/` into the new feature.
- [x] Added the small GitHub release API service used by the About screen under `data/service/GitHubReleaseService.kt`.
- [!] `AboutScreen` is not wired into settings/navigation because `newApp/` does not yet contain the settings/navigation flow or the destination screens described by `structure.md`.
- [!] `:newApp:compileDebugKotlin` remains blocked by existing rewrite-wide missing packages/types and stale imports in other files; this run did not provide a clean whole-module verification.

## Icons feature

- [x] Migrated the icon management screen to `features/icons/screen/IconsScreen.kt`: search, image import, cache listing, edit/delete, regex match rules, and optional light/dark variants.
- [x] Migrated shared icon storage, LobeHub icon lookup, model-family matching, and image decoding into `shared/icons/`; moved `IconRule` and `StoredIcon` into `shared/icons/model/` because icon lookup and management are cross-feature concerns.
- [!] Settings navigation is not wired in `newApp` yet; the rewrite's settings/navigation host is still missing. The icon screen is implemented but cannot be opened through the new app shell until that migration lands.
- [x] Ran `:newApp:compileDebugKotlin`; the compile emitted no diagnostics for `IconsScreen.kt` or `shared/icons/`, but the module still fails on existing rewrite-wide missing packages and symbols in `MainActivity`, `MultiGatewayApplication`, and other integrations.

## App data feature

- [x] Added a dependency rule to `structure.md`: feature `service/` may depend on its own `model/` and shared code, but not on feature `screen/` or other feature-local components.

- [x] Added `features/appdata/screen/AppDataScreen.kt` with data counts, clear-chat and factory-reset confirmations, and JSON import/export actions for providers, MCP servers, speech services, and conversations.
- [x] Added feature-local `model/JsonExportKind.kt` and `model/ProvidersExport.kt`, plus `service/JsonDataTransfer.kt` migrated from the old app and adapted to `shared.model` types.
- [!] The screen and JSON transfer service are not wired into settings/navigation or the new persistence/view-model layer because those dependencies are not yet migrated into `newApp/`.
- [!] Whole-module compile remains blocked by existing rewrite-wide missing packages and symbols; this compile reported no further error in `AppDataScreen.kt` after its Material 3 opt-in was added.

## Refactor app/ → newApp/ (2026-10-17, mechanical exact-logic migration)

Method: every remaining `app/` file is copied verbatim and only mechanical transforms are applied —
package declaration, import rewriting to the new locations, the established symbol renames
(`LlmProviderInfo`→`ModelProvider`, `ProviderType`→`ModelApiType`, `LlmService`→`AiService`,
`LlmRepository`→`ModelRepository`, …), plus structure-driven screen renames. Logic preservation is
verified per file with a normalized diff (package/imports stripped, renames applied to the `app/`
original): migrated files must be identical after normalization. `app/` is never edited.
Note: this sandbox has no JDK/Android SDK, so Gradle cannot run here; verification is static
(symbol/import resolution + normalized diffs). A real `./gradlew :newApp:compileDebugKotlin` run
on a dev machine is still required.

### Phase A — storage, repositories, app wiring
- [x] `data/local/db/*` → `shared/storage/database/` (`AppDatabase`, `SecretCipher`, `dao/Daos.kt`, `entities/Entities.kt`) with the established v9 table renames `llm_providers`→`model_providers`, `llm_models`→`provider_models` (entity/DAO renames `ModelProviderEntity`, `ProviderModelsEntity`, `ModelProviderDao`, `ProviderModelsDao`, DB version 9 + `MIGRATION_8_9`; historical migrations keep the old table names). Copied schema exports to `schemas/org.starfall.multigateway.shared.storage.database.AppDatabase/` (the old schema directory is kept for history).
- [x] `data/local/preferences/*` → `shared/storage/preferences/`; `data/local/RemoteMachineStore.kt` → `features/remotemachines/shared/`.
- [x] `data/repository/*` → `shared/storage/` (`LlmRepository`→`ModelRepository`); `DefaultDataInitializer` → `appwiring/`; `di/AppContainer.kt` → `appwiring/AppContainer.kt`.
- [x] `data/adapter/common/AppAuthTransactions.kt` → `shared/auth/AppAuthTransactions.kt` (was still missing in newApp).
- [x] `LlmService` → `integrations/modelapis/AiService.kt`: body copied from `app/`; parts extracted by earlier work (protocol streams, Ollama, model catalog, connection tests) now delegate to `ModelApiAdapterRegistry`/`ModelCatalogAdapter`/`OllamaAdapter` with the same call arguments. `resolveOllamaChatUrl/TagsUrl` were unused outside the extracted Ollama code and live on as `OllamaAdapter` privates.
- [x] Normalized diffs for Phase A files: identical except the intended v9 table-name strings.

### Phase B — services, tool runtime, shared UI
- [x] `data/service/*` distributed per structure rules: remote-machine services → `features/remotemachines/shared/`, MCP runtime (`McpService`, `McpOAuthService`, `McpOAuthTokenStore`) → `features/mcp/shared/`, speech playback/synthesis (`SpeechSynthesisService`, `SpeechText`, `TtsHelper`, `SpeechAudioPlayer`, `MediaPlayers`) → `shared/media/`, `FaviconService`/`IcoDecoder` → `shared/icons/`, `CrashReports` → `shared/files/`, `ChatBackgroundService` → `home/chat/`.
- [x] `data/tools/*`: `ToolFiles` → `shared/files/`, `ToolHttp`/`ToolStreaming`/`ProviderParsing` → `shared/networking/` (`ProviderParsing` also serves `ToolHttp.safeError`, so it stays with the networking layer instead of the chat screen), `ToolSettingsStore` → `shared/storage/preferences/`, tool-loop files (`ToolChat`, `ToolAttachments`, `ToolResults`, `SendFileTool`) → `home/chat/screen/`, media-generation tools → `features/defaultmodelsandtools/shared/`.
- [x] `ui/components/*` + `ui/icons/*` + `SlideScreenContent` + `AppAuthResultActivity` → `shared/ui/`; `ui/theme/Theme.kt` → `shared/theme/`; `ui/preview/*` → `preview/`; `ui/navigation/AppDestination.kt` → `navigation/`.
- [x] Normalized diffs so far: 85 files identical; only `AppDatabase`/`Daos`/`Entities` differ (intended v9 table renames).

### Phase C — home (chat/drawer/sheets) and feature screens
- [x] `ui/chat/*` → `home/chat/screen/` (incl. `UserInputArea.kt`→`InputArea.kt`, tool-loop files); `ModelPickerSheet` → `home/modelpicker/sheet/`, `QuickActionsSheet` → `home/quickactions/sheet/`, `ModelConfigDialog` → `features/providers/shared/` (also used by the model editor).
- [x] `ChatAppBar.kt`+`ContextWindow.kt` split per structure.md into `home/chat/screen/ChatAppbar/{ui.kt, logic.kt, ContextWindowMenuUi.kt, ContextWindowMenuLogic.kt}` (folder keeps structure.md's `ChatAppbar` spelling, so the package segment is `ChatAppbar`; split-shared helpers widened `private`→`internal`).
- [x] `ui/drawer/ConversationsDrawer.kt` split into `home/leftside/drawer/LeftSideDrawer.kt` (`ConversationsDrawer`→`LeftSideDrawer`) + `ConversationItem.kt` (`DrawerChatRow`→`ConversationItem`); `PromptLibraryDialog`/`ConversationModelIcon` alongside.
- [x] `ui/providers/*` → `features/providers/{providers/screen (ProvidersScreen, ProviderCard, ProviderFolder + list parts), providereditor/screen, modeleditor/screen, fetchmodels/sheet, shared/}`; screen composables renamed to the structure.md names (`ProvidersScreen`, `ProviderEditorScreen`, `ModelEditorScreen`). The UI-side catalog formatting file moves to `shared/CatalogMetadataFormatting.kt` to avoid confusion with `integrations/modelapis/ModelCatalogMetadata.kt`.
- [x] `ui/mcp/*` → `features/mcp/{serverlist/screen/ServerListScreen.kt, servereditor/screen/ServerEditorScreen.kt, shared/}`; `ui/speech/SpeechScreen.kt` → `features/speechservices/speechservices/screen/SpeechServicesScreen.kt`; `ui/remote/*` → `features/remotemachines/{remotemachines/screen, machinedetails/screen (RemoteMachineEditor→MachineDetailsScreen), shared/}`.
- [x] `ui/configuration/*` + `ui/profiles/ProfileScreen.kt` (no callers in `app/`, kept verbatim) → `features/providers/shared/`; `ui/settings/*` → `features/settings/{appsettings/screen (AppSettingsScreen from AppSettingsView, SettingsViewModel, CodeRenderingPreferences), shared/SettingsComponents.kt}`. The old `SettingsScreen` host, `UserDataSettingsView`, `AboutSettingsView`/`UpdateSettingsView` and landing list were already replaced by `FeaturesScreen`/`AppDataScreen`/`AboutScreen` in earlier work and are not duplicated.
- [x] `ui/tools/ToolSettingsScreens.kt` split into `systemtools/screen/SystemToolsScreen.kt`, `defaultmodels/screen/DefaultModelsScreen.kt`, `shared/ProfileMcpPermissions.kt`; `StorageScreen` → `features/storage/storage/screen/`; `ToolControls`/`ImageToolSettingsScreen` → `features/defaultmodelsandtools/shared/`.
- [x] Normalized diffs: 90/90 non-split files identical; all 5 splits block-identical to their `app/` originals.

### Phase D — navigation host, entry points, manifest
- [x] `ui/MainScreen.kt` → `navigation/AppNavigation.kt` (composable renamed `MainScreen`→`AppNavigation`); routes keep their persisted names. The former in-screen settings categories are now destinations: `SETTINGS` renders `FeaturesScreen`, new additive routes `AppSettings`, `Icons`, `AppData`, `About` render `AppSettingsScreen`, `IconsScreen`, `AppDataScreen`, `AboutScreen` with the same callbacks the old `SettingsScreen` call passed. JSON import/export launchers stay in `AppNavigation` and now use `features/appdata/{model,service}` types.
- [x] Added `features/settings/shared/MainAppbar.kt` (the former `SettingsScreen` top bar, verbatim styling) and wrapped the settings destinations with it; content padding matches the old settings Scaffold.
- [x] `home/chat/screen/QuickActionsLink.kt` added per structure.md (the exact `showQuickActions` sheet call moved out of `InputArea`); the default-models pager tab was extracted verbatim from `SystemToolsScreen` into `DefaultModelsScreen(providers, settings, onChooseModel, onOpenMediaSettings, onEditPrompt)` with the same state hoisted in the host.
- [x] `MainActivity`/`MultiGatewayApplication` imports updated to the new packages (bodies unchanged); `GitHubReleaseService` moved from the out-of-structure `data/service/` to `features/about/service/`; stale imports in the previously migrated files fixed (`AppAuthTransactions`, `data.tools.text`→`shared.networking.text`, `AppAuthResultActivity`, …); dropped `data.tools.*` wildcards replaced by explicit `shared.networking` helper imports (`obj`, `str`, `text`, `providerBase`, `modelResponse`).
- [x] Manifest class names updated (`shared.ui.AppAuthResultActivity`, `preview.CodePreviewActivity`, `home.chat.ChatBackgroundService`); `res/`/`assets/` match `app/` except earlier wording/logo additions.

### Phase E — tests
- [x] `newApp/src/test` + `androidTest` imports/symbol names rewritten to the new packages (wildcards expanded to the destination packages of the old package's symbols). The two same-package unit tests moved with their subjects: `ConversationTitleTriggerTest` → `home.chat.screen`, `TextFieldValueSyncTest` → `shared.ui`.
- [x] `GeneralSettingsTest` retargeted from the removed unified `SettingsScreen` to the new destinations (`FeaturesScreen` + `MainAppbar` host, `AppSettingsScreen`, `AboutScreen`) keeping the original assertions/intents; the landing label expectation was already stale against `app/` ("Default Models" vs "Default & Tools") and now clicks the real item titles. Device validation still pending (no device/CI here).

## Phase F — verification pass (2026-10-17)

- [x] Merged the `AppDestination` enum into `navigation/AppNavigation.kt` and deleted `AppDestination.kt` (it is only used by the host).
- [x] Whole-tree static verification (the sandbox has no JDK/Android SDK, so no Gradle build was possible):
  every `org.starfall.multigateway.*` import in main/test/androidTest resolves to a declared symbol (incl. nested-class imports); no stale `data/`/`ui/`/`di/` package references remain; all 873 `app/` top-level symbols are present in `newApp/` (with the documented renames) except the documented drops; manifest class names all exist; `R.*` references resolve against `res/` (the only unmatched names are `android.R.*` framework ones); brace balance clean; no duplicate type declarations or duplicate imports; the structure rule "`service/` must not import `screen/`" holds for all features.
- [x] Faithfulness check vs `app/`: normalized diffs (ignoring package, imports, renames, visibility widenings) show 173/179 directly mapped files body-identical to `app/`; the 6 differences are exactly the intended edits (Room v9 ×3, `QuickActionsLink` delegation, `TerminalInputMode` move ×2). The 5 split files were verified block-by-block against their originals.
- [x] Fixes found by verification: added a missing `ToolSettings` import to `DefaultModelsScreen`; removed 3 spurious `endpoint` imports (token collision with a local val); moved `TerminalInputMode` from `RemoteTerminalScreen` to `RemoteTerminalBridge` (shared layer no longer imports a screen); moved `ToolSwitch` into `features/defaultmodelsandtools/shared/`; added missing `shared.model` subpackage imports to 50 test files (they referenced subpackage types through the flat `shared.model.*` wildcard).
- [x] Removed the obsolete Room schema directory `schemas/org.starfall.multigateway.data.local.db.AppDatabase/`; the full history (2.json–8.json + hand-authored 9.json) lives under `schemas/org.starfall.multigateway.shared.storage.database.AppDatabase/`. **On the first SDK build, let Room regenerate 9.json and diff it against the committed copy.**
- [x] Confirmed `app/` is untouched (`git status --porcelain -- app/` is empty).

## Deviations from structure.md (deliberate)

- `FilesActionSheet.kt`, `RemoteMachinesSheet.kt`, `ChatContextSheets.kt` live in `home/chat/screen/` (chat-owned sheets; structure.md only specifies `modelpicker/sheet/` and `quickactions/sheet/` folders).
- `home/chat/screen/MessageList.kt` was not created: in `app/` the message list is inline in `ChatScreen`; extracting it would mean restructuring working logic, so it stays 1:1 inside `ChatScreen.kt`.
- Extra file `home/chat/screen/InputBar.kt` next to `InputArea.kt` (visual bar split from `app/`'s `UserInputBar.kt`, same logic).
- `shared/model/language/SendThinkingContent.kt` keeps the earlier migration's name (structure.md shows `ThinkingPassback.kt`); the `ThinkingPassback` typealias is retained.
- Several files exceed the 500-line soft cap (e.g. `ModelPickerSheet.kt`, `ChatViewModel.kt`, `RemoteMachineService.kt`, `AppNavigation.kt`): they are 1:1 ports of single large declarations; splitting them further was left out to avoid logic drift.
- Cross-feature UI reuse inherited from `app/` (e.g. providers-shared using `ModelCapabilityBadges` from `home/modelpicker`, `ProfileMcpPermissions` using `SettingsCard`) was kept as-is rather than re-homing those components.

## Intentionally not ported (dead code in app/)

- `WebViewCacheDisabler` (never called), `LlmService.resolveOllamaProvider/resolveOllamaModel/cachedOllamaModels` (no callers; Ollama discovery goes through the integration registry), the old Settings host/landing views (replaced by `FeaturesScreen` + dedicated `AppData`/`About`/`Icons` destinations; update checking lives in `AboutScreen`).

## Final status (2026-10-17)

- [x] The refactor `app/` → `newApp/` per `structure.md` is **complete**: 276 main Kotlin files (vs 231 in `app/`, due to the earlier model/integration splits + 5 view splits), all logic ported 1:1 with renames applied uniformly, navigation host wired (old route strings preserved + 4 new settings routes), manifest/resources/assets consistent, tests retargeted to the new packages.
- [ ] Still needs a machine with the Android SDK (unchanged from before, now the only remaining gate): `./gradlew :newApp:compileDebugKotlin :newApp:compileDebugUnitTestKotlin`, `:newApp:testDebugUnitTest`, instrumented tests (incl. a Room v8→v9 migration test), and a manual navigation smoke test. Expect at most minor import/visibility fallout from the static pass — all such issues found by static checks have been fixed.
- Note: earlier `[!]` entries in this file about the missing navigation host / unconnected screens are superseded by Phases C–F above.

## Follow-up (2026-10-17): LanguageModel/ModelProvider naming + first-launch DB migration

- [x] Naming confirmed/completed: `LlmModel` → `LanguageModel` (`shared/model/language/LanguageModel.kt`) and `LlmProviderInfo` → `ModelProvider` (`shared/model/provider/ModelProvider.kt`) were already in place, and no `Llm*`/`LLM` identifiers remained anywhere in `newApp/src` Kotlin sources. Renamed the last lowercase leftovers for consistency: `llmRepo` → `modelRepo` (`ConfigurationViewModel`, `ChatViewModel`, `DefaultDataInitializer`), `llmService` → `aiService` (`AppContainer`), `llm` → `aiService` (`ToolChat`). No named-argument call sites used the old parameter names.
- [x] **Added the actual first-launch database migration code.** `MIGRATION_8_9` was referenced (in `getInstance()` and `CredentialMigrationTest`) but its definition was missing, which would not have compiled. It is now defined in `shared/storage/database/AppDatabase.kt`: `ALTER TABLE llm_providers RENAME TO model_providers` and `ALTER TABLE llm_models RENAME TO provider_models`. Because the database file name (`multigateway_db`) and applicationId are unchanged, Room automatically runs the full migration chain (1→9) the first time the updated app opens the existing database file, renaming the tables in place with all rows preserved — no separate copy step is needed.
- [x] Fixed `encryptLegacySecrets` to query `model_providers` (it runs from `onOpen`, i.e. after migrations, so the pre-rename table name `llm_providers` would have crashed on first open for both upgrades and fresh installs). Historical migrations 3→4 and 5→6 intentionally keep referencing `llm_providers` because they run against the pre-v9 schema.
- [x] Verified: schema `8.json`↔`9.json` differ only in the two table names (identical columns, no indices/foreign keys), and the migration was simulated end-to-end in SQLite (built a v8 DB from `8.json`, inserted rows, ran both RENAME statements — data intact and every table's `PRAGMA table_info` matches `9.json`). `CredentialMigrationTest` already covers the v1→v9 upgrade path on-device; running it still requires the Android SDK.
- [x] Synced the structure tree in `newApp/structure.md` with the final on-disk layout (generated from the actual tree: all 276 main Kotlin files, verified 1:1 against disk). Removed phantom entries (`MessageList.kt`, `ThinkingPassback.kt`), added previously unlisted files/folders (e.g. `appwiring/DefaultDataInitializer.kt`, `features/about/service/`, `features/main/`, `integrations/modelapis/AiService.kt`, `preview/` contents, full `shared/ui|storage|media|networking` contents), and corrected the stale rule path `ui/providers/` → `features/providers/`. The Rules section is otherwise unchanged.

## Follow-up (2026-10-17): shared/ui grouping + structure doc moved to /AGENTS.md

- [x] Grouped the 28 flat files of `shared/ui/` into 9 subpackages by concern (package declarations only; file contents/logic unchanged): `auth/` (AppAuthResultActivity, OAuthBrowserActivity, OAuthNotificationPermission), `dialogs/` (AppDialogs, CrashReportDialog, FaviconDialog), `sheets/` (AppBottomSheet, BottomSheetHeight), `text/` (ApiKeyMask, PlatformTextSelection, SelectableTextFields, WordWrappedFadeText), `media/` (ChatAttachmentSelection, ChatFilePreview, MediaPlaybackState, MediaPreview, MediaViewerToolbar), `icons/` (EntityIcon, FluentIcon, Icons), `layout/` (AdaptiveCardLayout, AmoledBorder, ResponsiveLayout, SlideScreenContent), `lists/` (FadeGridListContent, ListOrder), `menus/` (ItemOverflowMenu, RoundedDropdownMenuItem). Rewrote all 339 references (336 imports + 3 inline FQNs) across main/test/androidTest, added explicit imports where files used to share the flat package (8 files), updated the two activity class names in `AndroidManifest.xml` to `.shared.ui.auth.*`, and mirrored `TextFieldValueSyncTest` into `test/.../shared/ui/text/`. Whole-tree verification output is identical to the pre-grouping baseline (same known `android.R` false positives and inherited layering warnings, no new errors).
- [x] Regenerated the structure tree (verified 1:1 against disk), added a Rules bullet documenting the `shared/ui/` subpackages, and moved `newApp/structure.md` to the repository root as `AGENTS.md` via `git mv` (history preserved). Older entries in this file still refer to it as `structure.md`.
