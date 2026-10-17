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
│   ├── 📁 chat-screen/
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
│   ├── 📁 leftside-drawer/
│   │   ├── 📄 LeftSideDrawer.kt
│   │   └── 📄 ConversationItem.kt
│   ├── 📁 modelpicker-sheet/
│   │   └── 📄 ModelPickerSheet.kt
│   └── 📁 quickactions-sheet/
│       └── 📄 QuickActionsSheet.kt
├── 📁 features/
│   ├── 📁 defaultmodelsandtools/
│   │   ├── 📁 defaultmodels-screen/
│   │   │   └── 📄 DefaultModelsScreen.kt
│   │   ├── 📁 systemtools-screen/
│   │   │   └── 📄 SystemToolsScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 providers/
│   │   ├── 📁 providerlist-screen/
│   │   │   ├── 📄 ProviderListScreen.kt
│   │   │   ├── 📄 ProviderCard.kt
│   │   │   └── 📄 ProviderFolder.kt
│   │   ├── 📁 providereditor-screen/
│   │   │   └── 📄 ProviderEditorScreen.kt
│   │   ├── 📁 modeleditor-screen/
│   │   │   └── 📄 ModelEditorScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 mcp/
│   │   ├── 📁 serverlist-screen/
│   │   │   └── 📄 ServerListScreen.kt
│   │   ├── 📁 servereditor-screen/
│   │   │   └── 📄 ServerEditorScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 speechservices/
│   │   ├── 📁 speechservices-screen/
│   │   │   └── 📄 SpeechServicesScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 remotemachines/
│   │   ├── 📁 remotemachines-screen/
│   │   │   └── 📄 RemoteMachinesScreen.kt
│   │   ├── 📁 machinedetails-screen/
│   │   │   └── 📄 MachineDetailsScreen.kt
│   │   └── 📁 shared/
│   ├── 📁 storage/
│   │   ├── 📁 storage-screen/
│   │   │   └── 📄 StorageScreen.kt
│   │   └── 📁 shared/
│   └── 📁 settings/
│       ├── 📁 appsettings-screen/
│       │   └── 📄 AppSettingsScreen.kt
│       ├── 📁 icons-screen/
│       │   └── 📄 IconsScreen.kt
│       ├── 📁 appdata-screen/
│       │   └── 📄 AppDataScreen.kt
│       ├── 📁 about-screen/
│       │   └── 📄 AboutScreen.kt
│       └── 📁 shared/
│           └── 📄 MainAppbar.kt
├── 📁 integrations/
│   └── 📁 languagemodels/
│       ├── 📄 LanguageModelAdapter.kt
│       ├── 📄 LanguageModelAdapterRegistry.kt
│       ├── 📄 ModelCatalogAdapter.kt
│       ├── 📄 ModelCatalogMetadata.kt
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
│   ├── 📁 icons/
│   ├── 📁 theme/
│   ├── 📁 networking/
│   ├── 📁 files/
│   │   └── 📄 AttachmentResolver.kt
│   ├── 📁 storage/
│   │   ├── 📁 database/
│   │   └── 📁 preferences/
│   ├── 📁 media/
│   ├── 📁 files/
|   └── 📁 model/
└── 📁 preview/
```

## Legend

- 📁 Folder
- 📄 File
- 📦 Root package

## Rules

- Only `Screen`, `BottomSheet`, `Dialog`, `SideDrawer`, and `Cover` always get dedicated view folders. Ordinary components stay as files inside their owning view unless their UI or logic becomes too long or complex to justify a component folder.
- When a component gets its own folder, `ui.kt` is its main UI file and `logic.kt` is its main logic file. If either grows too long, split out additional files with descriptive names such as `ContextWindowMenuUi.kt` and `ContextWindowMenuLogic.kt`. Add only the files needed.
- Folder names use one hyphen only before the type suffix: `chat-screen/`, `modelpicker-sheet/`, `leftside-drawer/`. Do not hyphenate words within the component name or repeat the type.
- A file containing navigation or link-handling logic for opening another component uses the `Link` suffix (for example, `QuickActionsLink.kt`); the destination view keeps its own type suffix (for example, `QuickActionsSheet.kt`).
- `feature/shared/` is for components shared inside that feature; root `shared/` is for cross-feature code. App-wide UI belongs in root `shared/`.
- Provider UI belongs in `features/providers/`; provider API adapters, provider metadata parsing, and account-provider OAuth code belong in `integrations/languagemodels/`.
- Cross-feature attachment metadata and file access belongs in `shared/files/`; generic domain models and events belong in `shared/model/`.
- `app/` remains untouched as fallback. `newApp/` is a rewrite workspace; copied code is reference material.
- No forced `ui/domain/data` layers or separate Gradle modules per feature.
