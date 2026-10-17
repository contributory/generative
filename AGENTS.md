# MultiGateway Structure

```text
📦 org.starfall.multigateway/
├── 📄 MainActivity.kt
├── 📄 MultiGatewayApplication.kt
├── 📁 appwiring/
│   ├── 📄 AppContainer.kt
│   └── 📄 DefaultDataInitializer.kt
├── 📁 features/
│   ├── 📁 about/
│   │   ├── 📁 screen/
│   │   │   └── 📄 AboutScreen.kt
│   │   └── 📁 service/
│   │       └── 📄 GitHubReleaseService.kt
│   ├── 📁 appdata/
│   │   ├── 📁 model/
│   │   │   ├── 📄 JsonExportKind.kt
│   │   │   └── 📄 ProvidersExport.kt
│   │   ├── 📁 screen/
│   │   │   └── 📄 AppDataScreen.kt
│   │   └── 📁 service/
│   │       └── 📄 JsonDataTransfer.kt
│   ├── 📁 defaultmodelsandtools/
│   │   ├── 📁 defaultmodels/
│   │   │   └── 📁 screen/
│   │   │       └── 📄 DefaultModelsScreen.kt
│   │   ├── 📁 shared/
│   │   │   ├── 📄 AgnesMedia.kt
│   │   │   ├── 📄 ContentApiMedia.kt
│   │   │   ├── 📄 ImageEditRequests.kt
│   │   │   ├── 📄 ImageGenerationOptions.kt
│   │   │   ├── 📄 ImageStreaming.kt
│   │   │   ├── 📄 ImageToolSettingsScreen.kt
│   │   │   ├── 📄 MediaInputReference.kt
│   │   │   ├── 📄 ProfileMcpPermissions.kt
│   │   │   ├── 📄 SystemMediaTools.kt
│   │   │   ├── 📄 ToolControls.kt
│   │   │   ├── 📄 ToolSwitch.kt
│   │   │   ├── 📄 VideoGeneration.kt
│   │   │   ├── 📄 VideoGenerationOptions.kt
│   │   │   └── 📄 VideoRequests.kt
│   │   └── 📁 systemtools/
│   │       └── 📁 screen/
│   │           └── 📄 SystemToolsScreen.kt
│   ├── 📁 icons/
│   │   └── 📁 screen/
│   │       └── 📄 IconsScreen.kt
│   ├── 📁 main/
│   │   └── 📁 screen/
│   │       └── 📄 FeaturesScreen.kt
│   ├── 📁 mcp/
│   │   ├── 📁 servereditor/
│   │   │   └── 📁 screen/
│   │   │       └── 📄 ServerEditorScreen.kt
│   │   ├── 📁 serverlist/
│   │   │   └── 📁 screen/
│   │   │       └── 📄 ServerListScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📄 McpConnectionSettings.kt
│   │       ├── 📄 McpOAuthFields.kt
│   │       ├── 📄 McpOAuthService.kt
│   │       ├── 📄 McpOAuthTokenStore.kt
│   │       ├── 📄 McpServerCard.kt
│   │       ├── 📄 McpService.kt
│   │       └── 📄 McpToolsTab.kt
│   ├── 📁 providers/
│   │   ├── 📁 fetchmodels/
│   │   │   └── 📁 sheet/
│   │   │       └── 📄 ProviderModelCatalogSheet.kt
│   │   ├── 📁 modeleditor/
│   │   │   └── 📁 screen/
│   │   │       └── 📄 ModelEditorScreen.kt
│   │   ├── 📁 providereditor/
│   │   │   └── 📁 screen/
│   │   │       └── 📄 ProviderEditorScreen.kt
│   │   ├── 📁 providers/
│   │   │   └── 📁 screen/
│   │   │       ├── 📄 ProviderCard.kt
│   │   │       ├── 📄 ProviderDragDrop.kt
│   │   │       ├── 📄 ProviderDragLayout.kt
│   │   │       ├── 📄 ProviderFolder.kt
│   │   │       ├── 📄 ProviderGroupDialogs.kt
│   │   │       ├── 📄 ProviderListParts.kt
│   │   │       ├── 📄 ProviderRootItem.kt
│   │   │       ├── 📄 ProviderScreenModels.kt
│   │   │       ├── 📄 ProviderScreenTopBar.kt
│   │   │       ├── 📄 ProviderSearch.kt
│   │   │       └── 📄 ProvidersScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📄 CatalogMetadataFormatting.kt
│   │       ├── 📄 ConfigurationViewModel.kt
│   │       ├── 📄 ModelConfigDialog.kt
│   │       ├── 📄 ModelConnectionDialog.kt
│   │       ├── 📄 ModelConnectionRemovals.kt
│   │       ├── 📄 ModelConnectionTests.kt
│   │       ├── 📄 OAuthAccountCard.kt
│   │       ├── 📄 OAuthAuthorizations.kt
│   │       ├── 📄 ProfileScreen.kt
│   │       ├── 📄 ProviderApiKeysDialog.kt
│   │       ├── 📄 ProviderApiKeysState.kt
│   │       ├── 📄 ProviderConnectionFields.kt
│   │       ├── 📄 ProviderHeadersDialog.kt
│   │       ├── 📄 ProviderModelsPane.kt
│   │       └── 📄 ProviderOAuthAccountsDialog.kt
│   ├── 📁 remotemachines/
│   │   ├── 📁 machinedetails/
│   │   │   └── 📁 screen/
│   │   │       ├── 📄 MachineDetailsScreen.kt
│   │   │       ├── 📄 RemoteMachineSettings.kt
│   │   │       └── 📄 RemoteTerminalScreen.kt
│   │   ├── 📁 remotemachines/
│   │   │   └── 📁 screen/
│   │   │       ├── 📄 RemoteMachinesScreen.kt
│   │   │       └── 📄 RemoteMachinesViewModel.kt
│   │   └── 📁 shared/
│   │       └── 📄 RemoteMachineComponents.kt
│   ├── 📁 settings/
│   │   ├── 📁 appsettings/
│   │   │   └── 📁 screen/
│   │   │       ├── 📄 AppSettingsScreen.kt
│   │   │       ├── 📄 CodeRenderingPreferences.kt
│   │   │       └── 📄 SettingsViewModel.kt
│   │   └── 📁 shared/
│   │       ├── 📄 MainAppbar.kt
│   │       └── 📄 SettingsComponents.kt
│   ├── 📁 speechservices/
│   │   └── 📁 speechservices/
│   │       └── 📁 screen/
│   │           └── 📄 SpeechServicesScreen.kt
│   └── 📁 storage/
│       └── 📁 storage/
│           └── 📁 screen/
│               └── 📄 StorageScreen.kt
├── 📁 home/
│   ├── 📁 chat/
│   │   ├── 📄 ChatBackgroundService.kt
│   │   └── 📁 screen/
│   │       ├── 📄 AssistantMessageCard.kt
│   │       ├── 📄 AttachmentPreview.kt
│   │       ├── 📄 ChatAppbar.kt
│   │       ├── 📄 ChatContextSheets.kt
│   │       ├── 📄 ChatGeneration.kt
│   │       ├── 📄 ChatOpeningVeil.kt
│   │       ├── 📄 ChatRenderCaches.kt
│   │       ├── 📄 ChatScreen.kt
│   │       ├── 📄 ChatSpeechCoordinator.kt
│   │       ├── 📄 ChatViewModel.kt
│   │       ├── 📄 CodeSyntaxHighlighting.kt
│   │       ├── 📄 ConversationSummaryCoordinator.kt
│   │       ├── 📄 ConversationSummaryUi.kt
│   │       ├── 📄 FilesActionSheet.kt
│   │       ├── 📄 GlyphSelectionGate.kt
│   │       ├── 📄 InputArea.kt
│   │       ├── 📄 InputBar.kt
│   │       ├── 📄 LatexDetector.kt
│   │       ├── 📄 LatexFormulaRenderer.kt
│   │       ├── 📄 LazyCodeBlock.kt
│   │       ├── 📄 LiveThinkingBlock.kt
│   │       ├── 📄 MarkdownCodeBlock.kt
│   │       ├── 📄 MarkdownInlineText.kt
│   │       ├── 📄 MarkdownLatexBlock.kt
│   │       ├── 📄 MarkdownLibrary.kt
│   │       ├── 📄 MarkdownMathParagraph.kt
│   │       ├── 📄 MarkdownRenderer.kt
│   │       ├── 📄 MarkdownTable.kt
│   │       ├── 📄 MathSplitter.kt
│   │       ├── 📄 MessageBubble.kt
│   │       ├── 📄 QuickActionsLink.kt
│   │       ├── 📄 ReasoningDropdown.kt
│   │       ├── 📄 ReasoningEffortControl.kt
│   │       ├── 📄 RemoteMachinesSheet.kt
│   │       ├── 📄 SendFileTool.kt
│   │       ├── 📄 StreamingTextFade.kt
│   │       ├── 📄 ThinkingColorBurst.kt
│   │       ├── 📄 ToolActivityCards.kt
│   │       ├── 📄 ToolAttachments.kt
│   │       ├── 📄 ToolChat.kt
│   │       ├── 📄 ToolResults.kt
│   │       ├── 📄 UserMessageCard.kt
│   │       └── 📄 UserMessageRetry.kt
│   ├── 📁 leftside/
│   │   └── 📁 drawer/
│   │       ├── 📄 ConversationItem.kt
│   │       ├── 📄 ConversationModelIcon.kt
│   │       ├── 📄 LeftSideDrawer.kt
│   │       └── 📄 PromptLibraryDialog.kt
│   ├── 📁 modelpicker/
│   │   └── 📁 sheet/
│   │       └── 📄 ModelPickerSheet.kt
│   └── 📁 quickactions/
│       └── 📁 sheet/
│           └── 📄 QuickActionsSheet.kt
├── 📁 integrations/
│   ├── 📁 remote/
│   │   ├── 📄 AndroidEdDSA.kt
│   │   ├── 📄 CloudShellApi.kt
│   │   ├── 📄 ExecutableBinaryStore.kt
│   │   ├── 📄 RemoteBinarySupport.kt
│   │   ├── 📄 RemoteMachineService.kt
│   │   ├── 📄 RemoteMachineStore.kt
│   │   ├── 📄 RemoteTerminal.kt
│   │   ├── 📄 RemoteTerminalBridge.kt
│   │   ├── 📄 SshConfigParser.kt
│   │   └── 📄 SshConnectionSupport.kt
│   └── 📁 modelapis/
│       ├── 📄 AiService.kt
│       ├── 📄 ModelApiAdapter.kt
│       ├── 📄 ModelApiAdapterRegistry.kt
│       ├── 📄 ModelCatalogAdapter.kt
│       ├── 📄 ModelCatalogMetadata.kt
│       ├── 📁 account/
│       │   ├── 📄 AccountProviderAdapter.kt
│       │   ├── 📄 AccountProviderAdapterRegistry.kt
│       │   ├── 📄 AccountTokenResponse.kt
│       │   ├── 📄 AccountTokenStore.kt
│       │   └── 📄 OAuthAccountAdapter.kt
│       ├── 📁 anthropic/
│       │   └── 📄 AnthropicMessagesAdapter.kt
│       ├── 📁 antigravity/
│       │   ├── 📄 AntigravityAdapter.kt
│       │   └── 📄 AntigravityModels.kt
│       ├── 📁 claudecode/
│       │   └── 📄 ClaudeCodeAdapter.kt
│       ├── 📁 gemini/
│       │   └── 📄 GeminiAdapter.kt
│       ├── 📁 githubcopilot/
│       │   └── 📄 GitHubCopilotAdapter.kt
│       ├── 📁 ollama/
│       │   ├── 📄 OllamaAdapter.kt
│       │   └── 📄 OllamaModelCache.kt
│       ├── 📁 openai/
│       │   └── 📄 OpenAIChatCompletionsAdapter.kt
│       ├── 📁 openaicodex/
│       │   ├── 📄 CodexTokenParser.kt
│       │   ├── 📄 CodexTokenStore.kt
│       │   └── 📄 OpenAICodexAdapter.kt
│       ├── 📁 openairesponses/
│       │   └── 📄 OpenAIResponsesAdapter.kt
│       └── 📁 sdk/
│           ├── 📄 OfficialProviderSdk.kt
│           └── 📄 SdkAttachmentContent.kt
├── 📁 navigation/
│   └── 📄 AppNavigation.kt
├── 📁 preview/
│   ├── 📄 CodePreviewActivity.kt
│   └── 📄 CodePreviewDocument.kt
└── 📁 shared/
    ├── 📁 auth/
    │   ├── 📄 AppAuthTransactions.kt
    │   ├── 📄 OAuthBrowser.kt
    │   ├── 📄 OAuthBrowserKeepAliveService.kt
    │   ├── 📄 OAuthCallbackService.kt
    │   ├── 📄 OAuthLoopbackCompatibility.kt
    │   └── 📄 StandardAppAuthAuthorization.kt
    ├── 📁 files/
    │   ├── 📄 AttachmentResolver.kt
    │   ├── 📄 CrashReports.kt
    │   └── 📄 ToolFiles.kt
    ├── 📁 icons/
    │   ├── 📄 AppImages.kt
    │   ├── 📄 FaviconService.kt
    │   ├── 📄 IcoDecoder.kt
    │   ├── 📄 IconStore.kt
    │   ├── 📄 LobeIconSource.kt
    │   ├── 📄 LobeModelIconResolver.kt
    │   └── 📁 model/
    │       ├── 📄 IconRule.kt
    │       └── 📄 StoredIcon.kt
    ├── 📁 media/
    │   ├── 📄 MediaPlayers.kt
    │   ├── 📄 SpeechAudioPlayer.kt
    │   ├── 📄 SpeechSynthesisService.kt
    │   ├── 📄 SpeechText.kt
    │   └── 📄 TtsHelper.kt
    ├── 📁 model/
    │   ├── 📄 ChatMessage.kt
    │   ├── 📄 ChatProfile.kt
    │   ├── 📄 ClaudeThinking.kt
    │   ├── 📄 DefaultOptions.kt
    │   ├── 📄 McpInfo.kt
    │   ├── 📄 PromptLibrary.kt
    │   ├── 📄 ProviderLink.kt
    │   ├── 📄 RemoteMachineModels.kt
    │   ├── 📄 ResponseVersions.kt
    │   ├── 📄 SidebarOrganization.kt
    │   ├── 📄 SpeechService.kt
    │   ├── 📄 Tools.kt
    │   ├── 📁 api/
    │   │   └── 📄 ModelApiType.kt
    │   ├── 📁 auth/
    │   │   ├── 📄 AuthMethod.kt
    │   │   ├── 📄 Authorization.kt
    │   │   └── 📄 AuthorizationExtensions.kt
    │   ├── 📁 generation/
    │   │   ├── 📄 GenerationEvent.kt
    │   │   └── 📄 StoredMessageExtensions.kt
    │   ├── 📁 language/
    │   │   ├── 📄 Capabilities.kt
    │   │   ├── 📄 DiscoveredModel.kt
    │   │   ├── 📄 LanguageModel.kt
    │   │   ├── 📄 ModelConfiguration.kt
    │   │   ├── 📄 ModelConfigurationConstants.kt
    │   │   ├── 📄 ModelConfigurationExtensions.kt
    │   │   ├── 📄 ModelConfigurationReasoning.kt
    │   │   ├── 📄 ModelType.kt
    │   │   └── 📄 SendThinkingContent.kt
    │   └── 📁 provider/
    │       ├── 📄 ModelProvider.kt
    │       ├── 📄 ModelProviderExtensions.kt
    │       ├── 📄 ProviderApiKey.kt
    │       ├── 📄 ProviderConfiguration.kt
    │       ├── 📄 ProviderConfigurationExtensions.kt
    │       ├── 📄 ProviderGroup.kt
    │       ├── 📄 ProviderModels.kt
    │       ├── 📄 ProviderOAuthAccount.kt
    │       ├── 📄 ProviderPlacement.kt
    │       └── 📄 ProviderRootOrderItem.kt
    ├── 📁 networking/
    │   ├── 📄 ProviderParsing.kt
    │   ├── 📄 ToolHttp.kt
    │   └── 📄 ToolStreaming.kt
    ├── 📁 storage/
    │   ├── 📄 ConversationRepository.kt
    │   ├── 📄 ImmediateState.kt
    │   ├── 📄 McpRepository.kt
    │   ├── 📄 ModelRepository.kt
    │   ├── 📄 ProfileRepository.kt
    │   ├── 📄 RepositoryJson.kt
    │   ├── 📄 SpeechRepository.kt
    │   ├── 📁 database/
    │   │   ├── 📄 AppDatabase.kt
    │   │   ├── 📄 SecretCipher.kt
    │   │   ├── 📁 dao/
    │   │   │   └── 📄 Daos.kt
    │   │   └── 📁 entities/
    │   │       └── 📄 Entities.kt
    │   └── 📁 preferences/
    │       ├── 📄 AppLanguage.kt
    │       ├── 📄 AppPreferencesRepository.kt
    │       ├── 📄 ModelConfigurationMemory.kt
    │       └── 📄 ToolSettingsStore.kt
    ├── 📁 theme/
    │   └── 📄 Theme.kt
    └── 📁 ui/
        ├── 📁 auth/
        │   ├── 📄 AppAuthResultActivity.kt
        │   ├── 📄 OAuthBrowserActivity.kt
        │   └── 📄 OAuthNotificationPermission.kt
        ├── 📁 dialogs/
        │   ├── 📄 AppDialogs.kt
        │   ├── 📄 CrashReportDialog.kt
        │   └── 📄 FaviconDialog.kt
        ├── 📁 icons/
        │   ├── 📄 EntityIcon.kt
        │   ├── 📄 FluentIcon.kt
        │   └── 📄 Icons.kt
        ├── 📁 layout/
        │   ├── 📄 AdaptiveCardLayout.kt
        │   ├── 📄 AmoledBorder.kt
        │   ├── 📄 ResponsiveLayout.kt
        │   └── 📄 SlideScreenContent.kt
        ├── 📁 lists/
        │   ├── 📄 FadeGridListContent.kt
        │   └── 📄 ListOrder.kt
        ├── 📁 media/
        │   ├── 📄 ChatAttachmentSelection.kt
        │   ├── 📄 ChatFilePreview.kt
        │   ├── 📄 MediaPlaybackState.kt
        │   ├── 📄 MediaPreview.kt
        │   └── 📄 MediaViewerToolbar.kt
        ├── 📁 menus/
        │   ├── 📄 ItemOverflowMenu.kt
        │   └── 📄 RoundedDropdownMenuItem.kt
        ├── 📁 sheets/
        │   ├── 📄 AppBottomSheet.kt
        │   └── 📄 BottomSheetHeight.kt
        └── 📁 text/
            ├── 📄 ApiKeyMask.kt
            ├── 📄 PlatformTextSelection.kt
            ├── 📄 SelectableTextFields.kt
            └── 📄 WordWrappedFadeText.kt
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
- `shared/ui/` groups app-wide UI by concern into subpackages: `auth/` (auth-flow activities and permission helper), `dialogs/`, `sheets/`, `text/` (text fields, selection, and text rendering), `media/` (media/file previews and playback state), `icons/` (icon set and icon composables), `layout/` (layout containers and surface/window modifiers), `lists/`, and `menus/`. New app-wide UI components go into the matching subpackage instead of the `shared/ui/` root.
- A feature `service/` must not depend on that feature’s `screen/` or any other feature-local package except its own `model/`. It may use `shared/` and other allowed cross-feature code. If feature-specific logic needs to call or depend on a non-model component from the same feature, that logic belongs in `screen/`, not `service/`. Keep dependencies flowing from screens toward services/models, never from services back into screens.
- Provider UI belongs in `features/providers/`; model API protocol adapters and catalog parsing belong in `integrations/modelapis/`. Account-provider OAuth adapters are kept under `integrations/modelapis/account/` for now. Kotlin package declarations use the valid identifier `integrations.modelapis`. Shared model packages use `shared.model.api`, `shared.model.auth`, `shared.model.generation`, `shared.model.language`, and `shared.model.provider`.
- Cross-feature attachment metadata and file access belongs in `shared/files/`; generic domain models belong in `shared/model/` grouped by API, auth, generation, language-model, and provider concerns; app-wide OAuth browser/session plumbing belongs in `shared/auth/`.
- Cross-feature icon caching, remote icon lookup, image decoding, and icon records belong in `shared/icons/`; feature screens consume this shared API rather than duplicating icon storage or lookup behavior.
- `legacyApp/` preserves the original implementation as a fallback. `app/` is the refactored implementation and active module.
- No forced `ui/domain/data` layers or separate Gradle modules per feature.

## Main feature

- `features/main/screen/FeaturesScreen.kt` is the untitled feature directory; its header displays the appropriate light/dark app logo.

- Theme-specific feature header assets are `drawable-nodpi/multigateway_logo_light.png` and `drawable-nodpi/multigateway_logo_dark.png`.
