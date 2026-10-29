# Generative Structure

This document is the detailed package map and architecture reference. The mandatory, high-priority rules agents must follow are summarized in [`AGENTS.md`](../AGENTS.md); keep that file concise and update it when a structural rule becomes critical for all code changes.

```text
📦 org.starfall.generative/
├── 📁 appwiring/
│   ├── 📄 AppContainer.kt
│   └── 📄 DefaultDataInitializer.kt
├── 📁 database/
│   ├── 📁 dao/
│   │   └── 📄 Daos.kt
│   ├── 📁 entities/
│   │   ├── 📄 ConversationEntities.kt
│   │   ├── 📄 Entities.kt
│   │   └── 📄 Phase4Entities.kt
│   ├── 📄 AppDatabase.kt
│   ├── 📄 AuthPayloads.kt
│   ├── 📄 ConfigurationMigration10Catalogs.kt
│   ├── 📄 ConfigurationMigration10Json.kt
│   ├── 📄 ConfigurationMigration10Mcp.kt
│   ├── 📄 ConfigurationMigration10Profiles.kt
│   ├── 📄 ConfigurationMigration10Providers.kt
│   ├── 📄 ConfigurationMigration10Schema.kt
│   ├── 📄 ConfigurationMigration10Speech.kt
│   ├── 📄 ConfigurationMigration9To10.kt
│   ├── 📄 ConversationMigration9To10.kt
│   ├── 📄 ConversationRepository.kt
│   ├── 📄 ConversationSaveJournal.kt
│   ├── 📄 EndpointSecrets.kt
│   ├── 📄 McpRepository.kt
│   ├── 📄 ModelRepository.kt
│   ├── 📄 ProfileRepository.kt
│   ├── 📄 ProviderStorageMapper.kt
│   ├── 📄 RepositoryJson.kt
│   ├── 📄 SpeechRepository.kt
│   └── 📄 StructuredJsonStorage.kt
├── 📁 features/
│   ├── 📁 about/
│   │   ├── 📁 logic/
│   │   │   └── 📄 GitHubReleaseService.kt
│   │   └── 📁 ui/
│   │       └── 📄 AboutScreen.kt
│   ├── 📁 appdata/
│   │   ├── 📁 logic/
│   │   │   ├── 📄 AppDataTransferService.kt
│   │   │   ├── 📄 JsonDataTransfer.kt
│   │   │   └── 📄 ZipDataTransfer.kt
│   │   ├── 📁 model/
│   │   │   ├── 📄 AppDataBackup.kt
│   │   │   ├── 📄 FullAppDataExport.kt
│   │   │   ├── 📄 ProvidersExport.kt
│   │   │   ├── 📄 RemoteTerminalPreferencesState.kt
│   │   │   └── 📄 ZipAppDataBackup.kt
│   │   └── 📁 ui/
│   │       └── 📄 AppDataScreen.kt
│   ├── 📁 chatoptions/
│   │   ├── 📁 logic/
│   │   │   └── 📄 AgentSkillFileTransfer.kt
│   │   └── 📁 ui/
│   │       ├── 📄 ChatOptionsScreen.kt
│   │       ├── 📄 HelperSettings.kt
│   │       ├── 📄 TtsProviderSettings.kt
│   │       ├── 📄 WebSearchProviderSettings.kt
│   │       ├── 📄 InstructionsTab.kt
│   │       ├── 📄 InstructionEditor.kt
│   │       ├── 📄 InstructionPreview.kt
│   │       ├── 📄 SkillEditor.kt
│   │       ├── 📄 SkillImportDialog.kt
│   │       └── 📄 SkillsTab.kt
│   ├── 📁 icons/
│   │   └── 📁 ui/
│   │       └── 📄 IconsScreen.kt
│   ├── 📁 machines/
│   │   ├── 📁 details/
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 MachineDetailsScreen.kt
│   │   │       ├── 📄 OAuthClientDialog.kt
│   │   │       ├── 📄 RemoteMachineAdvancedDialogs.kt
│   │   │       └── 📄 RemoteMachineSettings.kt
│   │   ├── 📁 list/
│   │   │   ├── 📁 model/
│   │   │   │   └── 📄 MachineIpUpdateNotice.kt
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 PortForwardDialog.kt
│   │   │       └── 📄 RemoteMachinesScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📁 logic/
│   │       │   └── 📄 RemoteMachinesViewModel.kt
│   │       ├── 📁 model/
│   │       │   └── 📄 BinaryTestNotice.kt
│   │       └── 📁 ui/
│   │           └── 📄 RemoteMachineComponents.kt
│   ├── 📁 menu/
│   │   └── 📁 ui/
│   │       └── 📄 AppMenuScreen.kt
│   ├── 📁 mcp/
│   │   ├── 📁 editor/
│   │   │   └── 📁 ui/
│   │   │       └── 📄 ServerEditor.kt
│   │   ├── 📁 list/
│   │   │   └── 📁 ui/
│   │   │       └── 📄 ServerListScreen.kt
│   │   └── 📁 shared/
│   │       └── 📁 ui/
│   │           ├── 📄 McpConnectionSettings.kt
│   │           ├── 📄 McpOAuthFields.kt
│   │           ├── 📄 McpServerCard.kt
│   │           └── 📄 McpToolsTab.kt
│   ├── 📁 providers/
│   │   ├── 📁 editor/
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 ModelEditor.kt
│   │   │       ├── 📄 ProviderEditor.kt
│   │   │       └── 📄 ProviderModelCatalogSheet.kt
│   │   ├── 📁 list/
│   │   │   ├── 📁 logic/
│   │   │   │   ├── 📄 ProviderDragLayout.kt
│   │   │   │   ├── 📄 ProviderRootItem.kt
│   │   │   │   └── 📄 ProviderSearch.kt
│   │   │   ├── 📁 model/
│   │   │   │   └── 📄 ProviderScreenModels.kt
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 ProviderCard.kt
│   │   │       ├── 📄 ProviderDialogHost.kt
│   │   │       ├── 📄 ProviderDragDrop.kt
│   │   │       ├── 📄 ProviderFolder.kt
│   │   │       ├── 📄 ProviderGroupDialogs.kt
│   │   │       ├── 📄 ProviderListParts.kt
│   │   │       ├── 📄 ProviderScreenTopBar.kt
│   │   │       └── 📄 ProvidersScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📁 logic/
│   │       │   ├── 📄 CatalogMetadataFormatting.kt
│   │       │   ├── 📄 ConfigurationViewModel.kt
│   │       │   ├── 📄 IntSizeExtensions.kt
│   │       │   ├── 📄 ModelConnectionRemovals.kt
│   │       │   ├── 📄 ModelConnectionTests.kt
│   │       │   └── 📄 OAuthAuthorizations.kt
│   │       ├── 📁 model/
│   │       │   ├── 📄 ModelConnectionStatus.kt
│   │       │   └── 📄 ProviderApiKeysState.kt
│   │       └── 📁 ui/
│   │           ├── 📄 AnthropicPromptCacheSettings.kt
│   │           ├── 📄 ModelConfigDialog.kt
│   │           ├── 📄 ModelConnectionDialog.kt
│   │           ├── 📄 ModelConnectionStatusAppearance.kt
│   │           ├── 📄 ModelConnectionStatusBar.kt
│   │           ├── 📄 OAuthAccountCard.kt
│   │           ├── 📄 ProviderApiKeysDialog.kt
│   │           ├── 📄 ProviderConnectionFields.kt
│   │           ├── 📄 ProviderHeadersDialog.kt
│   │           ├── 📄 ProviderModelsPane.kt
│   │           └── 📄 ProviderOAuthAccountsDialog.kt
│   ├── 📁 settings/
│   │   ├── 📁 logic/
│   │   │   └── 📄 SettingsViewModel.kt
│   │   └── 📁 ui/
│   │       ├── 📄 AppSettings.kt
│   │       ├── 📄 CodeRenderingPreferences.kt
│   │       ├── 📄 DeveloperSettingsSection.kt
│   │       ├── 📄 MainAppbar.kt
│   │       ├── 📄 McpAttachmentsPreferences.kt
│   │       └── 📄 SettingsComponents.kt
│   ├── 📁 speech/
│   │   └── 📁 ui/
│   │       ├── 📄 SpeechServiceDialogs.kt
│   │       └── 📄 SpeechServicesScreen.kt
│   ├── 📁 storage/
│   │   └── 📁 ui/
│   │       ├── 📄 MediaStorageSheet.kt
│   │       └── 📄 StorageScreen.kt
│   ├── 📁 userprofile/
│   │   └── 📁 ui/
│   │       ├── 📄 MediaStoragePhotoSheet.kt
│   │       ├── 📄 UserProfileImages.kt
│   │       └── 📄 UserProfileScreen.kt
│   └── 📁 tools/
│       └── 📁 ui/
│           ├── 📄 ImageToolSettings.kt
│           ├── 📄 MediaRequestChainSettings.kt
│           ├── 📄 MediaRequestChainStepEditor.kt
│           ├── 📄 MediaRequestProfileEditor.kt
│           ├── 📄 MediaTemplateAutocompleteField.kt
│           └── 📄 ToolsEditor.kt
│   └── 📁 home/
│   │   ├── 📁 chat/
│   │   │   ├── 📁 logic/
│   │   │   │   ├── 📁 input/
│   │   │   │   │   └── 📄 ChatInputAreaViewModel.kt
│   │   │   │   ├── 📁 markdown/
│   │   │   │   │   ├── 📄 LatexDetector.kt
│   │   │   │   │   ├── 📄 MarkdownHeading.kt
│   │   │   │   │   ├── 📄 MarkdownLibrary.kt
│   │   │   │   │   ├── 📄 MarkdownRenderWork.kt
│   │   │   │   │   ├── 📄 MathSplitter.kt
│   │   │   │   │   ├── 📄 PersistentMarkdownAstCache.kt
│   │   │   │   │   └── 📄 StableMarkdownPrefix.kt
│   │   │   │   ├── 📁 service/
│   │   │   │   │   ├── 📄 AutomaticConversationSummaryCoordinator.kt
│   │   │   │   │   ├── 📄 ChatConversationCoordinator.kt
│   │   │   │   │   ├── 📄 ChatMessageActions.kt
│   │   │   │   │   ├── 📄 ChatMessageRetryActions.kt
│   │   │   │   │   ├── 📄 ChatModelContextCoordinator.kt
│   │   │   │   │   ├── 📄 ChatToolSettingsActions.kt
│   │   │   │   │   └── 📄 ConversationTitleGenerator.kt
│   │   │   │   ├── 📁 tool/
│   │   │   │   │   ├── 📄 ContentApiMediaDownloader.kt
│   │   │   │   │   ├── 📄 McpAttachments.kt
│   │   │   │   │   ├── 📄 SkillToolRouting.kt
│   │   │   │   │   ├── 📄 ToolChatViewModel.kt
│   │   │   │   │   ├── 📄 ToolHistoryReplay.kt
│   │   │   │   │   └── 📄 ToolResultsViewModel.kt
│   │   │   │   ├── 📄 ChatGeneration.kt
│   │   │   │   ├── 📄 SentMessageDetector.kt
│   │   │   │   ├── 📄 ChatStreamDisplay.kt
│   │   │   │   ├── 📄 StreamCharBatcher.kt
│   │   │   │   ├── 📄 StreamResponseTextSplitter.kt
│   │   │   │   ├── 📄 ChatSpeechCoordinator.kt
│   │   │   │   ├── 📄 ChatViewModel.kt
│   │   │   │   ├── 📄 ContextWindowLogic.kt
│   │   │   │   ├── 📄 ContextUsageLogic.kt
│   │   │   │   ├── 📄 ConversationStructure.kt
│   │   │   │   ├── 📄 ConversationSummaryGeneration.kt
│   │   │   │   ├── 📄 ConversationSummaryCoordinator.kt
│   │   │   │   ├── 📄 SendFileTool.kt
│   │   │   │   └── 📄 UserMessageRetry.kt
│   │   │   ├── 📁 model/
│   │   │   │   ├── 📄 ActiveChatStreamState.kt
│   │   │   │   └── 📄 ContextWindowStatus.kt
│   │   │   └── 📁 ui/
│   │   │       ├── 📁 input/
│   │   │       │   ├── 📄 AgentSkillsMenu.kt
│   │   │       │   └── 📄 ChatInputAreaUi.kt
│   │   │       ├── 📁 markdown/
│   │   │       │   ├── 📄 ChunkedStreamingMarkdown.kt
│   │   │       │   ├── 📄 IncrementalMarkdownParser.kt
│   │   │       │   ├── 📄 LargeMarkdownParagraph.kt
│   │   │       │   ├── 📄 LatexFormulaRenderer.kt
│   │   │       │   ├── 📄 MarkdownBlockElement.kt
│   │   │       │   ├── 📄 MarkdownBlockVirtualizer.kt
│   │   │       │   ├── 📄 MarkdownCodeBlock.kt
│   │   │       │   ├── 📄 MarkdownHeading.kt
│   │   │       │   ├── 📄 MarkdownInlineText.kt
│   │   │       │   ├── 📄 MarkdownLatexBlock.kt
│   │   │       │   ├── 📄 MarkdownMathParagraph.kt
│   │   │       │   ├── 📄 MarkdownRenderer.kt
│   │   │       │   ├── 📄 MarkdownTable.kt
│   │   │       │   ├── 📄 StreamingMarkdownRenderer.kt
│   │   │       │   ├── 📄 StreamingMarkdownPreparation.kt
│   │   │       │   ├── 📄 VirtualizedFrozenChunks.kt
│   │   │       │   ├── 📄 VirtualizedIncrementalMarkdown.kt
│   │   │       │   ├── 📄 VirtualizedMarkdownList.kt
│   │   │       │   └── 📄 VirtualizedMarkdownTable.kt
│   │   │       ├── 📁 tool/
│   │   │       │   └── 📄 ToolActivityCards.kt
│   │   │       ├── 📄 AssistantMessageCard.kt
│   │   │       ├── 📄 AttachmentPreview.kt
│   │   │       ├── 📄 ChatAppbar.kt
│   │   │       ├── 📄 ChatMessageCallbacks.kt
│   │   │       ├── 📄 ChatMessageDialogs.kt
│   │   │       ├── 📄 ChatOpeningVeil.kt
│   │   │       ├── 📄 ChatRenderCaches.kt
│   │   │       ├── 📄 ChatScreen.kt
│   │   │       ├── 📄 ChatMessageList.kt
│   │   │       ├── 📄 ConversationMinimapLayout.kt
│   │   │       ├── 📄 ConversationMinimapSideBar.kt
│   │   │       ├── 📄 ConversationSummaryUi.kt
│   │   │       ├── 📄 GenerationLoadingIndicator.kt
│   │   │       ├── 📄 LiveThinkingBlock.kt
│   │   │       ├── 📄 MessageBubble.kt
│   │   │       ├── 📄 ReasoningDropdown.kt
│   │   │       ├── 📄 RetainedChatConversations.kt
│   │   │       ├── 📄 ThinkingPlainText.kt
│   │   │       └── 📄 UserMessageCard.kt
│   │   ├── 📁 drawer/
│   │   │   ├── 📁 logic/
│   │   │   │   └── 📄 ConversationModelIcon.kt
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 ConversationItem.kt
│   │   │       ├── 📄 ConversationSideDrawer.kt
│   │   │       └── 📄 DrawerQuickShortcuts.kt
│   │   ├── 📁 shared/
│   │   │   ├── 📁 logic/
│   │   │   │   ├── 📄 ChatBackgroundService.kt
│   │   │   │   ├── 📄 ToolAttachments.kt
│   │   │   │   ├── 📄 ToolExecutionService.kt
│   │   │   │   ├── 📄 ToolMediaGenerationService.kt
│   │   │   │   ├── 📄 ToolRequestService.kt
│   │   │   │   └── 📄 ToolResultSummaries.kt
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 ConversationSummarySheet.kt
│   │   │       ├── 📄 FeatureToolsSheet.kt
│   │   │       ├── 📄 InstructionsSheet.kt
│   │   │       ├── 📄 McpConnectorsSheet.kt
│   │   │       ├── 📄 QuickActionsSheet.kt
│   │   │       ├── 📄 ReasoningEffortSheet.kt
│   │   │       ├── 📄 SelectedModelOverviewSheet.kt
│   │   │       ├── 📄 ToolDetailsSheet.kt
│   │   │       └── 📄 ToolSheetState.kt
│   │   └── 📄 BottomSheetsLink.kt
├── 📁 integrations/
│   ├── 📁 github/
│   │   └── 📄 GitHubSkillDownloader.kt
│   ├── 📁 security/
│   │   └── 📄 SecretCipher.kt
│   ├── 📁 mcp/
│   │   ├── 📄 McpOAuthService.kt
│   │   ├── 📄 McpOAuthTokenStore.kt
│   │   ├── 📄 McpOAuthUrlUtils.kt
│   │   └── 📄 McpService.kt
│   ├── 📁 media/
│   │   ├── 📄 ImageStreaming.kt
│   │   ├── 📄 MediaRequestChainExecutor.kt
│   │   ├── 📄 SystemMediaTools.kt
│   │   └── 📄 VideoGeneration.kt
│   ├── 📁 modelapis/
│   │   ├── 📁 account/
│   │   │   ├── 📄 AccountProviderAdapter.kt
│   │   │   ├── 📄 AccountProviderAdapterRegistry.kt
│   │   │   ├── 📄 AccountTokenResponse.kt
│   │   │   ├── 📄 AccountTokenStore.kt
│   │   │   └── 📄 OAuthAccountAdapter.kt
│   │   ├── 📁 anthropic/
│   │   │   └── 📄 AnthropicMessagesAdapter.kt
│   │   ├── 📁 antigravity/
│   │   │   ├── 📄 AntigravityAdapter.kt
│   │   │   └── 📄 AntigravityModels.kt
│   │   ├── 📁 claudecode/
│   │   │   └── 📄 ClaudeCodeAdapter.kt
│   │   ├── 📁 gemini/
│   │   │   └── 📄 GeminiAdapter.kt
│   │   ├── 📁 githubcopilot/
│   │   │   └── 📄 GitHubCopilotAdapter.kt
│   │   ├── 📁 ollama/
│   │   │   ├── 📄 OllamaAdapter.kt
│   │   │   └── 📄 OllamaModelCache.kt
│   │   ├── 📁 openai/
│   │   │   └── 📄 OpenAIChatCompletionsAdapter.kt
│   │   ├── 📁 openaicodex/
│   │   │   ├── 📄 CodexTokenParser.kt
│   │   │   ├── 📄 CodexTokenStore.kt
│   │   │   └── 📄 OpenAICodexAdapter.kt
│   │   ├── 📁 openairesponses/
│   │   │   └── 📄 OpenAIResponsesAdapter.kt
│   │   ├── 📁 sdk/
│   │   │   ├── 📄 OfficialProviderSdk.kt
│   │   │   └── 📄 SdkAttachmentContent.kt
│   │   ├── 📄 AiService.kt
│   │   ├── 📄 ModelApiAdapter.kt
│   │   ├── 📄 ModelApiAdapterRegistry.kt
│   │   ├── 📄 ModelCatalogAdapter.kt
│   │   └── 📄 ModelCatalogMetadata.kt
│   └── 📁 remote/
│       ├── 📄 AndroidEdDSA.kt
│       ├── 📄 CloudShellApi.kt
│       ├── 📄 RemoteConnectionActionReceiver.kt
│       ├── 📄 RemoteConnectionService.kt
│       ├── 📄 RemoteMachineAssetsService.kt
│       ├── 📄 RemoteMachineCloudShellService.kt
│       ├── 📄 RemoteMachineConnectionManager.kt
│       ├── 📄 RemoteMachineService.kt
│       ├── 📄 RemoteMachineServiceOwner.kt
│       ├── 📄 RemoteMachineStore.kt
│       ├── 📄 RemoteSftpDocumentCallbacks.kt
│       ├── 📄 RemoteSftpDocumentFormatting.kt
│       ├── 📄 RemoteSftpDocumentsProvider.kt
│       ├── 📄 RemoteSftpSessionPool.kt
│       ├── 📄 RemoteSocksForwarder.kt
│       ├── 📄 RemoteTerminal.kt
│       ├── 📄 SshConfigParser.kt
│       ├── 📄 SshConnectionSupport.kt
│       ├── 📄 SshKeySupport.kt
│       └── 📄 SshOptions.kt
├── 📁 navigation/
│   ├── 📄 AppNavigation.kt
│   ├── 📄 RetainedNavHost.kt
│   └── 📄 TargetedEditorContent.kt
├── 📁 preview/
├── 📁 shared/
│   ├── 📁 auth/
│   │   ├── 📄 AppAuthTransactions.kt
│   │   ├── 📄 OAuthBrowser.kt
│   │   ├── 📄 OAuthBrowserKeepAliveService.kt
│   │   ├── 📄 OAuthCallbackService.kt
│   │   ├── 📄 OAuthLoopbackCompatibility.kt
│   │   └── 📄 StandardAppAuthAuthorization.kt
│   ├── 📁 code/
│   │   └── 📄 CodeSyntaxHighlighting.kt
│   ├── 📁 files/
│   │   ├── 📄 AgentSkillsStore.kt
│   │   ├── 📄 AppDocumentsProvider.kt
│   │   ├── 📄 AttachmentResolver.kt
│   │   ├── 📄 ConversationUploads.kt
│   │   ├── 📄 CrashReports.kt
│   │   └── 📄 ToolFiles.kt
│   ├── 📁 helpers/
│   │   └── 📁 media/
│   │       ├── 📄 ContentApiMedia.kt
│   │       ├── 📄 GeneratedMediaResponses.kt
│   │       ├── 📄 ImageEditRequests.kt
│   │       ├── 📄 ImageGenerationOptions.kt
│   │       ├── 📄 MediaRequestMapping.kt
│   │       ├── 📄 MediaInputReference.kt
│   │       ├── 📄 VideoGenerationOptions.kt
│   │       └── 📄 VideoRequests.kt
│   ├── 📁 icons/
│   │   ├── 📁 model/
│   │   │   ├── 📄 IconRule.kt
│   │   │   ├── 📄 IconStoreState.kt
│   │   │   └── 📄 StoredIcon.kt
│   │   ├── 📄 AppImages.kt
│   │   ├── 📄 FaviconService.kt
│   │   ├── 📄 IcoDecoder.kt
│   │   ├── 📄 IconStore.kt
│   │   ├── 📄 LobeIconSource.kt
│   │   └── 📄 LobeModelIconResolver.kt
│   ├── 📁 logging/
│   │   ├── 📄 DebugLog.kt
│   │   ├── 📄 DebugLogInterceptor.kt
│   │   ├── 📄 DebugLogKtorPlugin.kt
│   │   └── 📄 DebugLogRedactor.kt
│   ├── 📁 media/
│   │   ├── 📄 MediaPlayers.kt
│   │   ├── 📄 SpeechAudioPlayer.kt
│   │   ├── 📄 SpeechSynthesisService.kt
│   │   ├── 📄 SpeechText.kt
│   │   └── 📄 TtsHelper.kt
│   ├── 📁 model/
│   │   ├── 📁 api/
│   │   │   └── 📄 ModelApiType.kt
│   │   ├── 📁 auth/
│   │   │   ├── 📄 AuthMethod.kt
│   │   │   ├── 📄 Authorization.kt
│   │   │   ├── 📄 AuthorizationExtensions.kt
│   │   │   └── 📄 CredentialLoadState.kt
│   │   ├── 📁 generation/
│   │   │   ├── 📄 GenerationEvent.kt
│   │   │   ├── 📄 TokenUsage.kt
│   │   │   └── 📄 StoredMessageExtensions.kt
│   │   ├── 📁 language/
│   │   │   ├── 📄 Capabilities.kt
│   │   │   ├── 📄 DiscoveredModel.kt
│   │   │   ├── 📄 LanguageModel.kt
│   │   │   ├── 📄 ModelConfiguration.kt
│   │   │   ├── 📄 ModelConfigurationConstants.kt
│   │   │   ├── 📄 ModelConfigurationExtensions.kt
│   │   │   ├── 📄 ModelConfigurationReasoning.kt
│   │   │   ├── 📄 ModelType.kt
│   │   │   └── 📄 SendThinkingContent.kt
│   │   ├── 📁 logging/
│   │   │   └── 📄 DebugLogCategory.kt
│   │   ├── 📁 media/
│   │   │   ├── 📄 ContentApiMedia.kt
│   │   │   ├── 📄 GeneratedMediaReference.kt
│   │   │   ├── 📄 ImageOptionField.kt
│   │   │   ├── 📄 MediaInputImage.kt
│   │   │   ├── 📄 MediaInputVideo.kt
│   │   │   ├── 📄 MediaRequestChain.kt
│   │   │   └── 📄 VideoApi.kt
│   │   ├── 📁 provider/
│   │   │   ├── 📄 AnthropicPromptCache.kt
│   │   │   ├── 📄 ModelProvider.kt
│   │   │   ├── 📄 ModelProviderExtensions.kt
│   │   │   ├── 📄 ProviderApiKey.kt
│   │   │   ├── 📄 ProviderConfiguration.kt
│   │   │   ├── 📄 ProviderConfigurationExtensions.kt
│   │   │   ├── 📄 ProviderGroup.kt
│   │   │   ├── 📄 ProviderModels.kt
│   │   │   ├── 📄 ProviderOAuthAccount.kt
│   │   │   ├── 📄 ProviderPlacement.kt
│   │   │   └── 📄 ProviderRootOrderItem.kt
│   │   ├── 📄 ChatMessage.kt
│   │   ├── 📄 ChatProfile.kt
│   │   ├── 📄 AgentSkill.kt
│   │   ├── 📄 AgentSkillDocument.kt
│   │   ├── 📄 ClaudeThinking.kt
│   │   ├── 📄 ConversationListItem.kt
│   │   ├── 📄 DefaultOptions.kt
│   │   ├── 📄 BackupExportKind.kt
│   │   ├── 📄 McpInfo.kt
│   │   ├── 📄 InstructionLibrary.kt
│   │   ├── 📄 ProviderLink.kt
│   │   ├── 📄 RemoteMachineModels.kt
│   │   ├── 📁 remote/
│   │   │   ├── 📄 RemoteCloudShellAuth.kt
│   │   │   ├── 📄 RemoteSecretPrompt.kt
│   │   │   ├── 📄 RemoteTerminalSession.kt
│   │   │   └── 📄 TerminalInputMode.kt
│   │   ├── 📄 ResponseVersions.kt
│   │   ├── 📄 SidebarOrganization.kt
│   │   ├── 📄 SpeechService.kt
│   │   └── 📄 Tools.kt
│   ├── 📁 networking/
│   │   ├── 📄 ProviderParsing.kt
│   │   ├── 📄 ProviderUsage.kt
│   │   ├── 📄 ToolHttp.kt
│   │   └── 📄 ToolStreaming.kt
│   ├── 📁 preview/
│   │   ├── 📄 CodePreviewActivity.kt
│   │   └── 📄 CodePreviewDocument.kt
│   ├── 📁 storage/
│   │   ├── 📁 preferences/
│   │   │   ├── 📄 AppLanguage.kt
│   │   │   ├── 📄 AppPreferencesRepository.kt
│   │   │   ├── 📄 McpAttachmentSettings.kt
│   │   │   ├── 📄 ModelConfigurationMemory.kt
│   │   │   ├── 📄 RemoteTerminalPreferences.kt
│   │   │   └── 📄 ToolSettingsStore.kt
│   │   └── 📄 ImmediateState.kt
│   ├── 📁 terminal/
│   │   ├── 📄 PendingRemoteSecretRequest.kt
│   │   ├── 📄 RemoteMouseTouchListener.kt
│   │   ├── 📄 RemoteTerminalBridge.kt
│   │   ├── 📄 RemoteTerminalInput.kt
│   │   ├── 📄 RemoteTerminalInputConnection.kt
│   │   ├── 📄 RemoteTerminalLaunch.kt
│   │   ├── 📄 RemoteTerminalSessions.kt
│   │   └── 📄 RemoteTerminalView.kt
│   ├── 📁 theme/
│   │   └── 📄 Theme.kt
│   └── 📁 ui/
│       ├── 📁 auth/
│       │   ├── 📄 AppAuthResultActivity.kt
│       │   ├── 📄 OAuthBrowserActivity.kt
│       │   └── 📄 OAuthNotificationPermission.kt
│       ├── 📁 code/
│       │   ├── 📄 CodeBlockHeader.kt
│       │   ├── 📄 CodeBlockSurface.kt
│       │   ├── 📄 CodeRendering.kt
│       │   └── 📄 LazyCodeBlock.kt
│       ├── 📁 dialogs/
│       │   ├── 📄 AppDialogs.kt
│       │   ├── 📄 CrashReportDialog.kt
│       │   └── 📄 FaviconDialog.kt
│       ├── 📁 icons/
│       │   ├── 📁 fluentui/
│       │   │   ├── 📁 regular/ (downloaded ImageVector sources)
│       │   │   ├── 📁 filled/
│       │   │   ├── 📁 light/
│       │   │   └── 📁 color/
│       │   ├── 📄 EntityIcon.kt
│       │   ├── 📄 FluentIcon.kt
│       │   ├── 📄 FluentUi.kt (generated typed icon registry)
│       │   ├── 📄 Icons.kt (Material-name compatibility aliases)
│       │   └── 📄 TemporaryChatIcon.kt
│       ├── 📁 layout/
│       │   ├── 📄 AdaptiveCardLayout.kt
│       │   ├── 📄 AmoledBorder.kt
│       │   ├── 📄 ResponsiveLayout.kt
│       │   ├── 📄 ScreenCover.kt
│       │   ├── 📄 SettingsCard.kt
│       │   ├── 📄 RetainedScreenContent.kt
│       │   ├── 📄 ScreenSlideMotion.kt
│       │   └── 📄 SystemBars.kt
│       ├── 📁 lists/
│       │   ├── 📄 AppListContainer.kt
│       │   ├── 📄 AppListItem.kt
│       │   ├── 📄 EmptyListContent.kt
│       │   ├── 📄 FadeGridListContent.kt
│       │   ├── 📄 ListOrder.kt
│       │   └── 📄 ListViewToggle.kt
│       ├── 📁 logging/
│       │   ├── 📄 DebugLogCategoryLabel.kt
│       │   ├── 📄 DebugLogOverlay.kt
│       │   └── 📄 DebugLogPanel.kt
│       ├── 📁 machines/
│       │   ├── 📄 MachineDialogTitle.kt
│       │   └── 📄 MachineToolsDialog.kt
│       ├── 📁 media/
│       │   ├── 📄 ChatAttachmentSelection.kt
│       │   ├── 📄 ChatFilePreview.kt
│       │   ├── 📄 MediaFileCard.kt
│       │   ├── 📄 MediaPlaybackState.kt
│       │   ├── 📄 MediaPreview.kt
│       │   ├── 📄 MediaViewerCover.kt
│       │   └── 📄 MediaViewerMenu.kt
│       ├── 📁 menus/
│       │   ├── 📄 AppDropdownMenu.kt
│       │   ├── 📄 AppPopup.kt
│       │   ├── 📄 ItemContextMenu.kt
│       │   └── 📄 RoundedDropdownMenuItem.kt
│       ├── 📁 model/
│       │   ├── 📄 ModelInitial.kt
│       │   ├── 📄 ModelPickerCards.kt
│       │   ├── 📄 ModelPickerItems.kt
│       │   ├── 📄 ModelPickerRows.kt
│       │   ├── 📄 ModelPickerSheet.kt
│       │   ├── 📄 ModelPickerToolbar.kt
│       │   ├── 📄 ModelPickerTabRow.kt
│       │   ├── 📄 ReasoningEffortControl.kt
│       │   └── 📄 ThinkingColorBurst.kt
│       ├── 📁 sheets/
│       │   ├── 📄 AppSheet.kt
│       │   ├── 📄 BottomSheetHeight.kt
│       │   └── 📄 SlideSheetContent.kt
│       ├── 📁 text/
│       │   ├── 📄 ApiKeyField.kt
│       │   ├── 📄 ApiKeyMask.kt
│       │   ├── 📄 GlyphSelectionGate.kt
│       │   ├── 📄 MessageMarkdownStyle.kt
│       │   ├── 📄 PlatformTextSelection.kt
│       │   ├── 📄 SelectableTextFields.kt
│       │   ├── 📄 StreamingTextFade.kt
│       │   └── 📄 WordWrappedFadeText.kt
│       ├── 📄 ProfileMcpPermissions.kt
│       ├── 📄 LoadingIndicator.kt
│       ├── 📄 InstructionControls.kt
│       ├── 📄 ToolControls.kt
│       ├── 📄 ToolSwitch.kt
│       └── 📁 terminal/
│           ├── 📄 RemoteTerminalScreen.kt
│           ├── 📄 RemoteTerminalAppBar.kt
│           ├── 📄 RemoteTerminalDialogs.kt
│           └── 📄 RemoteTerminalPanels.kt
├── 📄 MainActivity.kt
└── 📄 GenerativeApplication.kt
```
## Legend

- 📁 Folder
- 📄 File
- 📦 Root package

## Detailed rules

The following sections give the package map's supporting conventions and feature-specific details.

### Layer placement and boundaries

- Dependency direction: `shared` ← `integrations` ← `database` ← `features` ← `navigation` ← `appwiring`. A layer may depend only on layers to its left.
- `database/` owns Room databases, DAOs, entities, migrations, database-backed repositories, and their storage mappers/auth payloads. Keep preferences and the reusable state primitive in `shared/storage/`. Repositories may coordinate integration services, but `shared/` and `integrations/` must not import `database/`.
- The Android Keystore adapter lives in `integrations/security/SecretCipher.kt`; both database credentials and MCP OAuth token storage use it.
- Room schema history lives in `app/schemas/org.starfall.generative.database.AppDatabase/`. Package-only moves must preserve every schema snapshot and the existing database filename, version, migration SQL, and credential key alias.

- Keep code close to the layer and users that need it. Code used in only one layer stays there; move code to global `shared/` only when it is genuinely used across layers. For example, a media request builder used only by `integrations/media/` stays there, while chat-tool-specific code stays in `features/home/chat/logic/tool/`.
- `integrations/` owns external communication, including HTTP clients, streaming/SSE readers, polling loops, OAuth flows, and remote connections. Keep each integration's request builders, response parsers, and small data types beside the code that uses them; these are integration-specific, not shared helpers.
- `shared/helpers/<topic>/` contains cross-layer helpers for preparing inputs or interpreting outputs, such as option validation, field descriptors, provider detection, tool schemas, argument parsers, and input-file resolution. Helpers must not import `integrations/`. Small incidental I/O may be tolerated as a stopgap—for example, fetching a URL returned by a response through injected `ToolHttp` from `shared/networking`, or reading a file through `shared/files`. Move the I/O into `integrations/` when it becomes the file's main purpose or grows its own endpoint, authentication, streaming, or polling protocol.
- Put plain data types (data classes and enums) alongside their consumers by default. Use global `shared/model/<topic>/` for types shared across layers. Helpers may import models; models must not import helpers or integrations.
- Terminal is a full-screen `terminal/{machineId}` navigation destination. Its app bar disconnects and closes on the left, and opens the sessions list on the right. Local/Remote input routing and IME composition are independent per-machine preferences; notification taps open the corresponding machine through `RemoteTerminalLaunch`.
- Cross-feature terminal presentation and its Android/Termux adapter live in `shared/terminal/` and `shared/ui/terminal/`. They depend on the shared `RemoteTerminalSession` contract, not on a feature ViewModel or a concrete integration implementation.

### UI component and file naming

Retain a useful feature or entity prefix, and keep closely related private helpers in the same file when that improves cohesion. Choose names by a component's responsibility and interaction, not just its appearance.

| Name pattern | Use |
| --- | --- |
| `AwesomeScreen` | The primary screen for a specific feature or capability. |
| `AwesomeEditor` | A screen for editing an entity or configuration, commonly opened from an AppBar add action or an item's Edit action. |
| `AwesomeSettings` | A screen for configuring settings for a feature or entity, commonly opened from an AppBar settings action. |
| `AwesomeSheet` | Every bottom-sheet component. Use `Sheet`, not the verbose legacy `BottomSheet` suffix. |
| `AwesomeSideDrawer` | A non-screen surface that slides in from a screen edge and covers more than half or all of the screen. |
| `AwesomeDialog` | A dialog only. |
| `ContextMenu` | A compact menu of actions/options belonging to an item in a list or similar collection. |
| `SideBar` | A vertical strip of details or controls alongside content, at the left or right edge, no wider than 33% of the screen. |
| `ToolBar` | A bar of interaction buttons belonging to a dialog, sheet, or other UI component. |
| `FeatureButton` | An in-screen, non-icon-only button that normally includes an icon and title; commonly opens configuration for an extension of its category. |
| `Dropdown` | A button/highlighted section that reveals additional content when pressed; use when the content is too modest for a `FeatureButton` but not important enough to stay permanently visible. |
| `IconButton` | A space-saving icon-only action used in an AppBar, `SideBar`, `ToolBar`, or elsewhere. |
| `DropdownOptions` | A control for choosing from a predefined set of options for an item or configuration. |

Replace `Awesome` with the relevant feature/entity name (for example, `ChatScreen`, `ProviderEditor`, or `ModelConfigDialog`). Use each suffix only for its defined role, and keep AppBars and platform/framework API names distinct from component `ToolBar` names.

### Feature organization

`features/` uses a UI-First Clean Architecture–inspired, feature-first organization around user/business capabilities (vertical slices), not around the screens listed in `FeaturesScreen` or `AppMenuScreen`. A feature owns the presentation for its capability, the application workflows/use-case coordination and state holders that implement it, and its feature-specific data/state models. It may contain several screens and supporting logic that is not rendered by any one screen; a screen is only a presentation entry point, not the feature boundary.

The project uses `ui/`, `logic/`, and `model/` instead of a canonical `presentation/`, `domain/`, and `data/` split. Treat these as responsibility and dependency boundaries, not one-to-one aliases for textbook Clean Architecture layers: `logic/` coordinates use cases and state transitions, while models should remain independent of UI and logic. Place external protocol/API communication in `integrations/`, Room persistence and database-backed repositories in `database/`, and broadly reused primitives/preferences in root `shared/`.

- Keep feature-specific code together under `features/<name>/`: `ui/` for Compose presentation and user interaction, `logic/` for ViewModels/coordinators/workflows, and `model/` for feature-specific data and state types. UI may depend on logic and model; logic may depend on model; model must not depend on UI or logic. Use only the folders a feature needs.
- Put external service/protocol communication in `integrations/`, database persistence in `database/`, broadly reused primitives in root `shared/`, and code reused only by sub-features in that feature's own `shared/`. A feature must not import another feature; shared needs should be moved to an appropriate shared layer rather than creating cross-feature dependencies.
- Menu exposure is a UI/navigation convention, not the definition of a feature. Most settings/configuration features below have an `AppMenuScreen` entry; `menu/` implements that menu surface, while `home/` owns the primary chat experience and is not a menu entry. Feature directory names are short, lowercase, a single word, with no underscores.

| Group | AppMenuScreen entry | Feature folder |
| --- | --- | --- |
| Configuration | Chat Options | `chatoptions/` |
| Configuration | Feature Tools | `tools/` |
| External Services | Model Providers | `providers/` |
| External Services | MCP Connectors | `mcp/` |
| External Services | Speech Services | `speech/` |
| External Services | Remote Machines | `machines/` |
| Data & Caching | Media Storage | `storage/` |
| Data & Caching | Icons & Trademarks | `icons/` |
| System | General Settings | `settings/` |
| System | Data Controls | `appdata/` |
| System | About | `about/` |

- A feature can use flat `ui/`, `logic/`, and `model/` folders, or be split into sub-features such as `list/`, `editor/`, and `details/`, each with its own applicable folders. Put code shared by multiple sub-features in the feature-local `shared/`, including `shared/model/` for data types that do not need to be shared across layers.
- Do not repeat the feature name as a nested folder (for example, avoid `storage/storage/`).

### Home feature organization

- `features/home/` contains the chat experience and its drawer: `chat/` holds messages, input, markdown, and tool UI; `drawer/` holds the conversation drawer; `shared/` holds code needed by both. Keep `chat/` and `drawer/` independent.
- Chat bottom sheets currently live in `features/home/shared/ui/`; `features/home/BottomSheetsLink.kt` is a top-level home component.

### Source-folder conventions

- Attachment and stored-file previews share `shared/ui/media/MediaViewerCover.kt`: media is presented without a dialog container, uncovered areas dismiss it, and actions live in the overlay menu. Zoomed images use the same edge-to-edge cover.
- Every Activity host applies `shared/ui/layout/SystemBars.kt`; screens draw behind transparent system bars and handle their own content insets.
- Sub-folders of `ui/` and `logic/` should mirror each other (for example, `chat/ui/markdown/` ↔ `chat/logic/markdown/`).
- If a `logic/` folder grows beyond about 10 files, consider splitting it into `viewmodel/` and `service/`.
- `shared/ui/` holds reusable UI. Other `shared/` and `integrations/` folders are organized by responsibility (for example, `storage/`, `networking/`, `helpers/`, and `modelapis/`) rather than a universal `ui/`/`logic/` split.

### App menu feature

- `navigation/RetainedNavHost.kt` retains active `NavController` destinations while hidden and keeps popped pages for the duration of their horizontal GPU-layer slide, then calls `onTransitionComplete` to release them. Chat is the single start destination. No horizontal Pager or custom Chat swipe-gating remains; the native drawer handles swipes. One Menu-family photo backdrop is owned by the retained host: it slides only for the direct Chat ↔ App Menu transition, remaining stationary under all other pages. Shared `RetainedScreenContent` uses the same horizontal slide for embedded editors, retaining their parent and disposing the exiting editor after its slide. `shared/ui/layout/ScreenSlideMotion.kt` owns slide duration and direction: Forward moves right to left, PopBack moves left to right.

- `features/menu/ui/AppMenuScreen.kt` is the untitled menu feature; its header displays the light-background app logo (`drawable-nodpi/generative_logo_light.png`) in every theme.

### Context and summary accounting

- API adapters emit one normalized usage snapshot per model request, including streaming/tool rounds. Context usage is the latest matching request's input plus its reported output, not a sum across requests. Cached input follows each protocol's accounting without double counting.
- Usage is stored with each response version in nullable relational columns (Room v12). Hashed history/configuration keys prevent reuse after an edit, branch, model/provider change, prompt/tool-settings change or compact. Old/imported history without usage falls back to the local estimator; new messages after a valid snapshot add only an explicitly labeled local estimate.
- The context details distinguish API usage, estimates and mixed counts. A missing output count remains explicitly unavailable rather than becoming a fabricated zero. The maximum context capacity still comes from model configuration/catalog metadata.
- Custom summary targets are not restricted to the slider's 6000-token range. Summary requests override the provider's default output budget, while explicit model output limits and input-plus-output context budgets remain enforced. Multiple-pass summaries reserve room for the requested final output.

### Standalone speech and music requests

- `shared/model/SpeechService.kt` declares `SpeechEngine` with legacy System/Provider inference.
- `integrations/speech/SpeechEngineAdapters.kt` owns ElevenLabs, Fish Audio, Cartesia and Speechify HTTP mappings and their separate voice pagination protocols; `StandaloneSpeechClient.kt` handles bounded, cancellable transport without diagnostic request logging.
- `features/speech/ui/SpeechVoiceSheet.kt` owns on-demand voice search, pagination, retries and selection. `SpeechEditor.kt` keeps standalone model/voice IDs separate from Provider models.
- `SpeechRepository` stores additive engine/voice-name metadata in structured JSON and continues encrypting keys in `SpeechAuthEntity`; no Room schema change is required.
- `shared/model/media/MediaRequestChain.kt` adds `MediaKind.MUSIC`, `AUDIO`, protected credential envelopes and Custom music-profile validation. `SystemToolConfig` owns a separate music library and active ID.
- `features/tools/ui/MusicToolSettings.kt` provides the profile-only Music Generation card; `MusicProfileCredentialField.kt` masks and encrypts the profile key before updating the draft. The existing profile library/editor receives an explicit `MediaKind`; Music omits the mode selector and built-in Default.
- `integrations/media/MusicGenerationClient.kt` snapshots a Custom profile and executes it without a Provider or chat model. It disables request debug logging, uses profile-owned encrypted credentials, bounds execution, and rolls back newly created output files on failure/cancellation through `shared/files/ToolFileBatch.kt`.
- `shared/media/AudioContent.kt` recognizes supported audio containers for TTS and Music; `ToolFiles` validates audio before publishing a generated file. Existing chat audio previews and Media Storage render saved tracks.

- `integrations/speech/SpeechModelCatalog.kt` fetches synthesis model catalogs independently of voice lists. `features/speech/ui/SpeechModelField.kt` automatically loads on engine/key changes, allows refresh/selection, and preserves manual IDs on failures or empty results.

- `shared/media/SpeechChunks.kt` groups external TTS requests by each service’s configurable sentence count (default 10), using period/newline boundaries without duplicate adjacent separators and retaining a character cap. `SpeechText.kt` strips decorative symbols/colons before synthesis. `SpeechAudioPlayer.playAndAwait` lets the chat speech coordinator synthesize and play one clip at a time, retaining active-message state until the whole sequence completes.

- `shared/media/SpeechPlaybackNotification.kt` owns the TTS notification channel and session-specific Stop action. `ChatSpeechCoordinator` shows it on actual playback start for both Android TTS and API audio, keeps it through chunk boundaries, and clears it on completion/error/Stop. AppContainer injects its application-context instance into ChatViewModel. Existing notification permission handling is reused; this is a playback-status notification, not a foreground service.

### Chat viewport

- `chat/ui/ChatMessageList.kt` owns the conversation-keyed native `LazyListState`, initial viewport layout, message markers, follow eligibility and the single cancellable scroll-intent consumer. The current source uses a normal chronological `LazyColumn`, initialized at its final keyed boundary without an opening scroll. The rejected reversed-list implementation has been removed from source; UI validation and acceptance under `plans/chatscreen-refactor.md` remain pending. Turns are keyed by stable message IDs. A short final turn uses a minimum usable-viewport height without imposing an upper bound; declarative list padding supplies the capped 30% bottom allowance. Child renderers determine their own dimensions.
- `chat/ui/RetainedChatConversations.kt` keeps chat compositions keyed by conversation ID, with no fixed chat-count limit. Parked chats remain composed but are not measured, placed or drawn; their viewport, Thinking, Actions, composer draft and chat attachments survive immediate return. On navigation it uses actual JVM heap headroom to trim least-recently-used inactive pages, and Android memory-pressure callbacks (`EvictableCache` via `ChatRenderCaches`) release further inactive pages. No periodic polling or fixed chat-count cutoff. Temporary chats are never retained, and deleted conversations are dropped. Android does not provide an actual per-composition low-priority RAM tier; this is a bounded, explicitly reclaimable in-memory cache.
- `ChatScreen` forwards existing card actions and composer events. History data loads before the real list mounts; renderer/cache readiness never gates viewport creation. `ConversationMinimapSideBar` is a conventional list scrollbar. It appears by itself while the list scrolls (between the app bar and the composer) and fades after a short idle; it adds no horizontal-swipe rules, so edge swipes behave exactly as on any chat body. Only the thumb takes touches, and only vertical drags. Thumb position and size are measured in turns from the lazy list's native visible items (`minimapVisibleSpan`/`minimapThumb`), with no text-height estimates. Dragging scrolls the list directly (`scrollToItem` with an in-turn fraction only for on-screen turns), reports manual scrolling through `onScrub` so follow stops, and dragging to the bottom resumes it at the end of the latest content.
- Stopped stream snapshots stay available for the mounted conversation's handoff and are released on navigation/deletion; live streams for other conversations continue independently. Persistence remains at completed/interrupted block boundaries, with temporary chats excluded.

- `chat/ui/ThinkingPlainText.kt` displays raw reasoning with the configured message font and a subdued text color. It does not parse Markdown or own scroll physics. `LiveThinkingBlock` smooths only the drawn auto-follow position with a short graphics-layer animation; native `ScrollState`, manual drag and the inactivity pause remain authoritative. The Actions timeline starts folded; running steps are shown outside the fold and shrink into history on completion. Manually expanded Thinking and Actions states are retained through the transition.
