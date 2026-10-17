# MultiGateway Structure

```text
📦 org.starfall.multigateway/
├── 📄 MainActivity.kt
├── 📄 MultiGatewayApplication.kt
├── 📁 appwiring/
│   └── 📄 AppContainer.kt
├── 📁 navigation/
│   └── 📄 AppNavigation.kt
├── 📁 home/
│   ├── 📁 chat/screen/
│   │   ├── 📄 ChatScreen.kt
│   │   ├── 📄 ChatViewModel.kt
│   │   ├── 📁 ChatAppbar/
│   │   │   ├── 📄 ui.kt
│   │   │   ├── 📄 logic.kt
│   │   │   ├── 📄 ContextWindowMenuUi.kt
│   │   │   └── 📄 ContextWindowMenuLogic.kt
│   │   ├── 📄 InputArea.kt
│   │   ├── 📄 MessageList.kt
│   │   └── 📄 QuickActionsLink.kt
│   ├── 📁 leftside/drawer/
│   │   ├── 📄 LeftSideDrawer.kt
│   │   └── 📄 ConversationItem.kt
│   ├── 📁 modelpicker/sheet/
│   │   └── 📄 ModelPickerSheet.kt
│   └── 📁 quickactions/sheet/
│       └── 📄 QuickActionsSheet.kt
├── 📁 features/
│   ├── 📁 defaultmodelsandtools/
│   │   ├── 📁 defaultmodels/screen/
│   │   │   └── 📄 DefaultModelsScreen.kt
│   │   ├── 📁 systemtools/screen/
│   │   │   └── 📄 SystemToolsScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 providers/
│   │   ├── 📁 providers/screen/
│   │   │   ├── 📄 ProvidersScreen.kt
│   │   │   ├── 📄 ProviderCard.kt
│   │   │   └── 📄 ProviderFolder.kt
│   │   ├── 📁 providereditor/screen/
│   │   │   └── 📄 ProviderEditorScreen.kt
│   │   ├── 📁 modeleditor/screen/
│   │   │   └── 📄 ModelEditorScreen.kt
        ├── 📁 fetchmodels/sheet
│   │   └── 📁 shared/
│   ├── 📁 mcp/
│   │   ├── 📁 serverlist/screen/
│   │   │   └── 📄 ServerListScreen.kt
│   │   ├── 📁 servereditor/screen/
│   │   │   └── 📄 ServerEditorScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 speechservices/
│   │   ├── 📁 speechservices/screen/
│   │   │   └── 📄 SpeechServicesScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 remotemachines/
│   │   ├── 📁 remotemachines/screen/
│   │   │   └── 📄 RemoteMachinesScreen.kt
│   │   ├── 📁 machinedetails/screen/
│   │   │   └── 📄 MachineDetailsScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 storage/
│   │   ├── 📁 storage/screen/
│   │   │   └── 📄 StorageScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 about/screen/
│   │   └── 📄 AboutScreen.kt
│   ├── 📁 appdata/
│   │   ├── 📁 model/
│   │   │   ├── 📄 JsonExportKind.kt
│   │   │   └── 📄 ProvidersExport.kt
│   │   ├── 📁 screen/
│   │   │   └── 📄 AppDataScreen.kt
│   │   └── 📁 service/
│   │       └── 📄 JsonDataTransfer.kt
│   ├── 📁 icons/screen/
│   │   └── 📄 IconsScreen.kt
│   └── 📁 settings/
│       ├── 📁 appsettings/screen/
│       │   └── 📄 AppSettingsScreen.kt
│       └── 📁 shared/
│           └── 📄 MainAppbar.kt
├── 📁 integrations/
│   └── 📁 modelapis/
│       ├── 📄 ModelApiAdapter.kt
│       ├── 📄 ModelApiAdapterRegistry.kt
│       ├── 📄 ModelCatalogAdapter.kt
│       ├── 📄 ModelCatalogMetadata.kt
│       ├── 📁 account/
│       │   ├── 📄 AccountProviderAdapter.kt
│       │   ├── 📄 AccountProviderAdapterRegistry.kt
│       │   ├── 📄 OAuthAccountAdapter.kt
│       │   ├── 📄 AccountTokenResponse.kt
│       │   └── 📄 AccountTokenStore.kt
│       ├── 📁 sdk/
│       │   ├── 📄 OfficialProviderSdk.kt
│       │   └── 📄 SdkAttachmentContent.kt
│       ├── 📁 openai/
│       │   └── 📄 OpenAIChatCompletionsAdapter.kt
│       ├── 📁 openairesponses/
│       │   └── 📄 OpenAIResponsesAdapter.kt
│       ├── 📁 anthropic/
│       │   └── 📄 AnthropicMessagesAdapter.kt
│       ├── 📁 gemini/
│       │   └── 📄 GeminiAdapter.kt
│       ├── 📁 ollama/
│       │   └── 📄 OllamaAdapter.kt
│       ├── 📁 githubcopilot/
│       │   └── 📄 GitHubCopilotAdapter.kt
│       ├── 📁 openaicodex/
│       │   ├── 📄 OpenAICodexAdapter.kt
│       │   ├── 📄 CodexTokenParser.kt
│       │   └── 📄 CodexTokenStore.kt
│       ├── 📁 claudecode/
│       │   └── 📄 ClaudeCodeAdapter.kt
│       └── 📁 antigravity/
│           ├── 📄 AntigravityAdapter.kt
│           └── 📄 AntigravityModels.kt
├── 📁 shared/
│   ├── 📁 ui/
│   │   └── 📄 OAuthBrowserActivity.kt
│   ├── 📁 icons/
│   │   ├── 📄 IconStore.kt
│   │   ├── 📄 LobeIconSource.kt
│   │   ├── 📄 LobeModelIconResolver.kt
│   │   ├── 📄 AppImages.kt
│   │   └── 📁 model/
│   │       ├── 📄 IconRule.kt
│   │       └── 📄 StoredIcon.kt
│   ├── 📁 theme/
│   ├── 📁 networking/
│   ├── 📁 files/
│   │   └── 📄 AttachmentResolver.kt
│   ├── 📁 auth/
│   │   ├── 📄 OAuthBrowser.kt
│   │   ├── 📄 OAuthBrowserKeepAliveService.kt
│   │   ├── 📄 OAuthCallbackService.kt
│   │   ├── 📄 OAuthLoopbackCompatibility.kt
│   │   └── 📄 StandardAppAuthAuthorization.kt
│   ├── 📁 storage/
│   │   ├── 📁 database/
│   │   └── 📁 preferences/
│   ├── 📁 media/
│   └── 📁 model/
│       ├── 📁 api/
│       │   └── 📄 ModelApiType.kt
│       ├── 📁 auth/
│       │   ├── 📄 AuthMethod.kt
│       │   ├── 📄 Authorization.kt
│       │   └── 📄 AuthorizationExtensions.kt
│       ├── 📁 generation/
│       │   ├── 📄 GenerationEvent.kt
│       │   └── 📄 StoredMessageExtensions.kt
│       ├── 📁 language/
│       │   ├── 📄 Capabilities.kt
│       │   ├── 📄 DiscoveredModel.kt
│       │   ├── 📄 LanguageModel.kt
│       │   ├── 📄 ModelConfiguration.kt
│       │   ├── 📄 ModelConfigurationConstants.kt
│       │   ├── 📄 ModelConfigurationExtensions.kt
│       │   ├── 📄 ModelConfigurationReasoning.kt
│       │   ├── 📄 ModelType.kt
│       │   └── 📄 ThinkingPassback.kt
│       └── 📁 provider/
│           ├── 📄 ModelProvider.kt
│           ├── 📄 ModelProviderExtensions.kt
│           ├── 📄 ProviderApiKey.kt
│           ├── 📄 ProviderConfiguration.kt
│           ├── 📄 ProviderConfigurationExtensions.kt
│           ├── 📄 ProviderGroup.kt
│           ├── 📄 ProviderModels.kt
│           ├── 📄 ProviderOAuthAccount.kt
│           ├── 📄 ProviderPlacement.kt
│           └── 📄 ProviderRootOrderItem.kt
└── 📁 preview/
```

## Legend

- 📁 Folder
- 📄 File
- 📦 Root package

## Rules

- Keep each source file at 500 lines or fewer, counting every line including blank lines and comments. This is a soft limit, not a strict rule: a file may exceed 500 lines when splitting it would not provide a meaningful benefit or would create unnecessary fragmentation.
- Only `Screen`, `BottomSheet`, `Dialog`, `SideDrawer`, and `Cover` always get dedicated view folders. Ordinary components stay as files inside their owning view unless their UI or logic becomes too long or complex to justify a component folder.
- When a component gets its own folder, `ui.kt` is its main UI file and `logic.kt` is its main logic file. If either grows too long, split out additional files with descriptive names such as `ContextWindowMenuUi.kt` and `ContextWindowMenuLogic.kt`. Add only the files needed.
- Folder paths separate the component name and its type with `/`, for example `chat/screen/`, `modelpicker/sheet/`, `leftside/drawer/`, and `about/screen/`. Each path segment maps to a valid Kotlin package identifier; do not use hyphens in Kotlin package names.
- A file containing navigation or link-handling logic for opening another component uses the `Link` suffix (for example, `QuickActionsLink.kt`); the destination view keeps its own type suffix (for example, `QuickActionsSheet.kt`).
- `feature/shared/` is for components shared inside that feature; root `shared/` is for cross-feature code. App-wide UI belongs in root `shared/`.
- A feature `service/` must not depend on that feature’s `screen/` or any other feature-local package except its own `model/`. It may use `shared/` and other allowed cross-feature code. If feature-specific logic needs to call or depend on a non-model component from the same feature, that logic belongs in `screen/`, not `service/`. Keep dependencies flowing from screens toward services/models, never from services back into screens.
- Provider UI belongs in `ui/providers/`; model API protocol adapters and catalog parsing belong in `integrations/modelapis/`. Account-provider OAuth adapters are kept under `integrations/modelapis/account/` for now. Kotlin package declarations use the valid identifier `integrations.modelapis`. Shared model packages use `shared.model.api`, `shared.model.auth`, `shared.model.generation`, `shared.model.language`, and `shared.model.provider`.
- Cross-feature attachment metadata and file access belongs in `shared/files/`; generic domain models belong in `shared/model/` grouped by API, auth, generation, language-model, and provider concerns; app-wide OAuth browser/session plumbing belongs in `shared/auth/`.
- Cross-feature icon caching, remote icon lookup, image decoding, and icon records belong in `shared/icons/`; feature screens consume this shared API rather than duplicating icon storage or lookup behavior.
- `app/` remains untouched as fallback. `newApp/` is a rewrite workspace; copied code is reference material.
- No forced `ui/domain/data` layers or separate Gradle modules per feature.

## Main feature

- `features/main/screen/FeaturesScreen.kt` is the untitled feature directory; its header displays the appropriate light/dark app logo.

- Theme-specific feature header assets are `drawable-nodpi/multigateway_logo_light.png` and `drawable-nodpi/multigateway_logo_dark.png`.
