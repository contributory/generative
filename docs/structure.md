# Generative Structure

This document is the detailed package map and architecture reference. The mandatory, high-priority rules agents must follow are summarized in [`AGENTS.md`](../AGENTS.md); keep that file concise and update it when a structural rule becomes critical for all code changes.

```text
📦 org.starfall.generative/
├── 📁 appwiring/
│   ├── 📄 AppContainer.kt
│   └── 📄 DefaultDataInitializer.kt
├── 📁 features/
│   ├── 📁 about/
│   │   ├── 📁 logic/
│   │   │   └── 📄 GitHubReleaseService.kt
│   │   └── 📁 ui/
│   │       └── 📄 AboutScreen.kt
│   ├── 📁 appdata/
│   │   ├── 📁 logic/
│   │   │   └── 📄 JsonDataTransfer.kt
│   │   ├── 📁 model/
│   │   │   ├── 📄 FullAppDataExport.kt
│   │   │   └── 📄 ProvidersExport.kt
│   │   └── 📁 ui/
│   │       └── 📄 AppDataScreen.kt
│   ├── 📁 chatoptions/
│   │   └── 📁 ui/
│   │       ├── 📄 ChatOptionsScreen.kt
│   │       ├── 📄 HelperSettings.kt
│   │       ├── 📄 InstructionsTab.kt
│   │       ├── 📄 PromptEditor.kt
│   │       └── 📄 PromptPreview.kt
│   ├── 📁 icons/
│   │   └── 📁 ui/
│   │       └── 📄 IconsScreen.kt
│   ├── 📁 machines/
│   │   ├── 📁 details/
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 MachineDetailsScreen.kt
│   │   │       ├── 📄 OAuthClientDialog.kt
│   │   │       ├── 📄 PortForwardingSection.kt
│   │   │       ├── 📄 RemoteMachineAdvancedDialogs.kt
│   │   │       └── 📄 RemoteMachineSettings.kt
│   │   ├── 📁 list/
│   │   │   ├── 📁 model/
│   │   │   │   └── 📄 MachineIpUpdateNotice.kt
│   │   │   └── 📁 ui/
│   │   │       └── 📄 RemoteMachinesScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📁 logic/
│   │       │   └── 📄 RemoteMachinesViewModel.kt
│   │       ├── 📁 model/
│   │       │   └── 📄 BinaryTestNotice.kt
│   │       └── 📁 ui/
│   │           └── 📄 RemoteMachineComponents.kt
│   ├── 📁 main/
│   │   └── 📁 ui/
│   │       └── 📄 FeaturesScreen.kt
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
│   │       │   └── 📄 ProviderApiKeysState.kt
│   │       └── 📁 ui/
│   │           ├── 📄 ModelConfigDialog.kt
│   │           ├── 📄 ModelConnectionDialog.kt
│   │           ├── 📄 OAuthAccountCard.kt
│   │           ├── 📄 ProfileScreen.kt
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
│   │       └── 📄 SettingsComponents.kt
│   ├── 📁 speech/
│   │   └── 📁 ui/
│   │       ├── 📄 SpeechServiceDialogs.kt
│   │       └── 📄 SpeechServicesScreen.kt
│   ├── 📁 storage/
│   │   └── 📁 ui/
│   │       └── 📄 StorageScreen.kt
│   └── 📁 tools/
│       └── 📁 ui/
│           ├── 📄 ImageToolSettings.kt
│           ├── 📄 MediaRequestChainSettings.kt
│           ├── 📄 MediaRequestChainStepEditor.kt
│           ├── 📄 MediaRequestProfileEditor.kt
│           ├── 📄 MediaTemplateAutocompleteField.kt
│           └── 📄 ToolsEditor.kt
├── 📁 home/
│   ├── 📁 chat/
│   │   ├── 📁 logic/
│   │   │   ├── 📁 input/
│   │   │   │   └── 📄 ChatInputAreaViewModel.kt
│   │   │   ├── 📁 markdown/
│   │   │   │   ├── 📄 LatexDetector.kt
│   │   │   │   ├── 📄 MarkdownLibrary.kt
│   │   │   │   ├── 📄 MathSplitter.kt
│   │   │   │   └── 📄 StableMarkdownPrefix.kt
│   │   │   ├── 📁 service/
│   │   │   │   ├── 📄 AutomaticConversationSummaryCoordinator.kt
│   │   │   │   ├── 📄 ChatConversationCoordinator.kt
│   │   │   │   ├── 📄 ChatMessageActions.kt
│   │   │   │   ├── 📄 ChatMessageRetryActions.kt
│   │   │   │   ├── 📄 ChatModelContextCoordinator.kt
│   │   │   │   ├── 📄 ChatToolSettingsActions.kt
│   │   │   │   └── 📄 ConversationTitleGenerator.kt
│   │   │   ├── 📁 tool/
│   │   │   │   ├── 📄 ContentApiMediaDownloader.kt
│   │   │   │   ├── 📄 ToolChatViewModel.kt
│   │   │   │   ├── 📄 ToolHistoryReplay.kt
│   │   │   │   └── 📄 ToolResultsViewModel.kt
│   │   │   ├── 📄 ChatGeneration.kt
│   │   │   ├── 📄 ChatScrollAnchor.kt
│   │   │   ├── 📄 ChatSpeechCoordinator.kt
│   │   │   ├── 📄 ChatViewModel.kt
│   │   │   ├── 📄 ContextWindowLogic.kt
│   │   │   ├── 📄 ContextUsageLogic.kt
│   │   │   ├── 📄 ConversationSummaryGeneration.kt
│   │   │   ├── 📄 ConversationSummaryCoordinator.kt
│   │   │   ├── 📄 SendFileTool.kt
│   │   │   └── 📄 UserMessageRetry.kt
│   │   ├── 📁 model/
│   │   │   └── 📄 ContextWindowStatus.kt
│   │   └── 📁 ui/
│   │       ├── 📁 input/
│   │       │   └── 📄 ChatInputAreaUi.kt
│   │       ├── 📁 markdown/
│   │       │   ├── 📄 ChunkedStreamingMarkdown.kt
│   │       │   ├── 📄 LatexFormulaRenderer.kt
│   │       │   ├── 📄 MarkdownCodeBlock.kt
│   │       │   ├── 📄 MarkdownInlineText.kt
│   │       │   ├── 📄 MarkdownLatexBlock.kt
│   │       │   ├── 📄 MarkdownMathParagraph.kt
│   │       │   ├── 📄 MarkdownRenderer.kt
│   │       │   └── 📄 MarkdownTable.kt
│   │       ├── 📁 tool/
│   │       │   └── 📄 ToolActivityCards.kt
│   │       ├── 📄 AssistantMessageCard.kt
│   │       ├── 📄 AttachmentPreview.kt
│   │       ├── 📄 ChatAppbar.kt
│   │       ├── 📄 ChatMessageDialogs.kt
│   │       ├── 📄 ChatOpeningVeil.kt
│   │       ├── 📄 ChatRenderCaches.kt
│   │       ├── 📄 ChatScreen.kt
│   │       ├── 📄 ChatScrollAnchor.kt
│   │       ├── 📄 ConversationSummaryUi.kt
│   │       ├── 📄 GenerationLoadingIndicator.kt
│   │       ├── 📄 LiveThinkingBlock.kt
│   │       ├── 📄 MessageBubble.kt
│   │       ├── 📄 ReasoningDropdown.kt
│   │       └── 📄 UserMessageCard.kt
│   ├── 📁 drawer/
│   │   ├── 📁 logic/
│   │   │   └── 📄 ConversationModelIcon.kt
│   │   └── 📁 ui/
│   │       ├── 📄 ConversationItem.kt
│   │       ├── 📄 DrawerBackdrop.kt
│   │       └── 📄 ConversationSideDrawer.kt
│   ├── 📁 shared/
│   │   ├── 📁 logic/
│   │   │   ├── 📄 ChatBackgroundService.kt
│   │   │   ├── 📄 ToolAttachments.kt
│   │   │   ├── 📄 ToolExecutionService.kt
│   │   │   ├── 📄 ToolMediaGenerationService.kt
│   │   │   ├── 📄 ToolRequestService.kt
│   │   │   └── 📄 ToolResultSummaries.kt
│   │   └── 📁 ui/
│   │       ├── 📄 ConversationSummarySheet.kt
│   │       ├── 📄 FeatureToolsSheet.kt
│   │       ├── 📄 McpConnectorsSheet.kt
│   │       ├── 📄 PromptInstructionsSheet.kt
│   │       ├── 📄 QuickActionsSheet.kt
│   │       ├── 📄 ReasoningEffortSheet.kt
│   │       ├── 📄 RemoteMachinesSheet.kt
│   │       ├── 📄 SelectedModelOverviewSheet.kt
│   │       ├── 📄 ToolDetailsSheet.kt
│   │       └── 📄 ToolSheetState.kt
│   └── 📄 BottomSheetsLink.kt
├── 📁 integrations/
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
│   │   ├── 📄 ClaudeThinking.kt
│   │   ├── 📄 ConversationListItem.kt
│   │   ├── 📄 DefaultOptions.kt
│   │   ├── 📄 JsonExportKind.kt
│   │   ├── 📄 McpInfo.kt
│   │   ├── 📄 PromptLibrary.kt
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
│   │   ├── 📁 database/
│   │   │   ├── 📁 dao/
│   │   │   │   └── 📄 Daos.kt
│   │   │   ├── 📁 entities/
│   │   │   │   ├── 📄 ConversationEntities.kt
│   │   │   │   ├── 📄 Entities.kt
│   │   │   │   └── 📄 Phase4Entities.kt
│   │   │   ├── 📄 AppDatabase.kt
│   │   │   ├── 📄 ConfigurationMigration10Catalogs.kt
│   │   │   ├── 📄 ConfigurationMigration10Json.kt
│   │   │   ├── 📄 ConfigurationMigration10Mcp.kt
│   │   │   ├── 📄 ConfigurationMigration10Profiles.kt
│   │   │   ├── 📄 ConfigurationMigration10Providers.kt
│   │   │   ├── 📄 ConfigurationMigration10Schema.kt
│   │   │   ├── 📄 ConfigurationMigration10Speech.kt
│   │   │   ├── 📄 ConfigurationMigration9To10.kt
│   │   │   ├── 📄 ConversationMigration9To10.kt
│   │   │   └── 📄 SecretCipher.kt
│   │   ├── 📁 preferences/
│   │   │   ├── 📄 AppLanguage.kt
│   │   │   ├── 📄 AppPreferencesRepository.kt
│   │   │   ├── 📄 ModelConfigurationMemory.kt
│   │   │   ├── 📄 RemoteTerminalPreferences.kt
│   │   │   └── 📄 ToolSettingsStore.kt
│   │   ├── 📄 AuthPayloads.kt
│   │   ├── 📄 ConversationRepository.kt
│   │   ├── 📄 EndpointSecrets.kt
│   │   ├── 📄 ImmediateState.kt
│   │   ├── 📄 McpRepository.kt
│   │   ├── 📄 ModelRepository.kt
│   │   ├── 📄 ProfileRepository.kt
│   │   ├── 📄 ProviderStorageMapper.kt
│   │   ├── 📄 RepositoryJson.kt
│   │   ├── 📄 SpeechRepository.kt
│   │   └── 📄 StructuredJsonStorage.kt
│   ├── 📁 terminal/
│   │   ├── 📄 PendingRemoteSecretRequest.kt
│   │   ├── 📄 RemoteMouseTouchListener.kt
│   │   ├── 📄 RemoteTerminalBridge.kt
│   │   ├── 📄 RemoteTerminalInput.kt
│   │   └── 📄 RemoteTerminalSessions.kt
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
│       │   └── 📄 Icons.kt (Material-name compatibility aliases)
│       ├── 📁 layout/
│       │   ├── 📄 AdaptiveCardLayout.kt
│       │   ├── 📄 AmoledBorder.kt
│       │   ├── 📄 ResponsiveLayout.kt
│       │   ├── 📄 SettingsCard.kt
│       │   └── 📄 SlideScreenContent.kt
│       ├── 📁 lists/
│       │   ├── 📄 FadeGridListContent.kt
│       │   └── 📄 ListOrder.kt
│       ├── 📁 logging/
│       │   ├── 📄 DebugLogCategoryLabel.kt
│       │   ├── 📄 DebugLogOverlay.kt
│       │   └── 📄 DebugLogPanel.kt
│       ├── 📁 media/
│       │   ├── 📄 ChatAttachmentSelection.kt
│       │   ├── 📄 ChatFilePreview.kt
│       │   ├── 📄 MediaFileCard.kt
│       │   ├── 📄 MediaPlaybackState.kt
│       │   ├── 📄 MediaPreview.kt
│       │   └── 📄 MediaViewerToolBar.kt
│       ├── 📁 menus/
│       │   ├── 📄 ItemContextMenu.kt
│       │   └── 📄 RoundedDropdownMenuItem.kt
│       ├── 📁 model/
│       │   ├── 📄 ModelInitial.kt
│       │   ├── 📄 ModelPickerCards.kt
│       │   ├── 📄 ModelPickerItems.kt
│       │   ├── 📄 ModelPickerRows.kt
│       │   ├── 📄 ModelPickerSheet.kt
│       │   ├── 📄 ModelPickerTabRow.kt
│       │   ├── 📄 ModelPickerToolbar.kt
│       │   ├── 📄 ReasoningEffortControl.kt
│       │   └── 📄 ThinkingColorBurst.kt
│       ├── 📁 sheets/
│       │   ├── 📄 AppSheet.kt
│       │   ├── 📄 BottomSheetHeight.kt
│       │   └── 📄 SlideSheetContent.kt
│       ├── 📁 text/
│       │   ├── 📄 ApiKeyMask.kt
│       │   ├── 📄 GlyphSelectionGate.kt
│       │   ├── 📄 MessageMarkdownStyle.kt
│       │   ├── 📄 PlatformTextSelection.kt
│       │   ├── 📄 SelectableTextFields.kt
│       │   ├── 📄 StreamingTextFade.kt
│       │   └── 📄 WordWrappedFadeText.kt
│       ├── 📄 ProfileMcpPermissions.kt
│       ├── 📄 PromptControls.kt
│       ├── 📄 ToolControls.kt
│       ├── 📄 ToolSwitch.kt
│       └── 📁 terminal/
│           ├── 📄 RemoteTerminalSheet.kt
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

- Keep code close to the layer and users that need it. Code used in only one layer stays there; move code to global `shared/` only when it is genuinely used across layers. For example, a media request builder used only by `integrations/media/` stays there, while chat-tool-specific code stays in `home/chat/logic/tool/`.
- `integrations/` owns external communication, including HTTP clients, streaming/SSE readers, polling loops, OAuth flows, and remote connections. Keep each integration's request builders, response parsers, and small data types beside the code that uses them; these are integration-specific, not shared helpers.
- `shared/helpers/<topic>/` contains cross-layer helpers for preparing inputs or interpreting outputs, such as option validation, field descriptors, provider detection, tool schemas, argument parsers, and input-file resolution. Helpers must not import `integrations/`. Small incidental I/O may be tolerated as a stopgap—for example, fetching a URL returned by a response through injected `ToolHttp` from `shared/networking`, or reading a file through `shared/files`. Move the I/O into `integrations/` when it becomes the file's main purpose or grows its own endpoint, authentication, streaming, or polling protocol.
- Put plain data types (data classes and enums) alongside their consumers by default. Use global `shared/model/<topic>/` for types shared across layers. Helpers may import models; models must not import helpers or integrations.
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

- Every folder directly under `features/` except `main/` matches exactly one entry in `FeaturesScreen`; `main/` is that screen itself. Folder names are short, lowercase, a single word, with no underscores.

| Group | FeaturesScreen entry | Folder |
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

### Home organization

- `home/` contains the chat experience and its drawer: `chat/` holds messages, input, markdown, and tool UI; `drawer/` holds the conversation drawer; `shared/` holds code needed by both. Keep `chat/` and `drawer/` independent.
- Chat bottom sheets currently live in `home/shared/ui/`; `home/BottomSheetsLink.kt` is a top-level home component.

### Source-folder conventions

- Sub-folders of `ui/` and `logic/` should mirror each other (for example, `chat/ui/markdown/` ↔ `chat/logic/markdown/`).
- If a `logic/` folder grows beyond about 10 files, consider splitting it into `viewmodel/` and `service/`.
- `shared/ui/` holds reusable UI. Other `shared/` and `integrations/` folders are organized by responsibility (for example, `storage/`, `networking/`, `helpers/`, and `modelapis/`) rather than a universal `ui/`/`logic/` split.

### Main feature

- `features/main/ui/FeaturesScreen.kt` is the untitled feature directory; its header displays the light-background app logo (`drawable-nodpi/generative_logo_light.png`) in every theme.

### Context and summary accounting

- API adapters emit one normalized usage snapshot per model request, including streaming/tool rounds. Context usage is the latest matching request's input plus its reported output, not a sum across requests. Cached input follows each protocol's accounting without double counting.
- Usage is stored with each response version in nullable relational columns (Room v12). Hashed history/configuration keys prevent reuse after an edit, branch, model/provider change, prompt/tool-settings change or compact. Old/imported history without usage falls back to the local estimator; new messages after a valid snapshot add only an explicitly labeled local estimate.
- The context details distinguish API usage, estimates and mixed counts. A missing output count remains explicitly unavailable rather than becoming a fabricated zero. The maximum context capacity still comes from model configuration/catalog metadata.
- Custom summary targets are not restricted to the slider's 6000-token range. Summary requests override the provider's default output budget, while explicit model output limits and input-plus-output context budgets remain enforced. Multiple-pass summaries reserve room for the requested final output.
