# Generative Structure

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
│   │       └── 📄 ChatOptionsScreen.kt
│   ├── 📁 icons/
│   │   └── 📁 ui/
│   │       └── 📄 IconsScreen.kt
│   ├── 📁 machines/
│   │   ├── 📁 details/
│   │   │   └── 📁 ui/
│   │   │       ├── 📄 MachineDetailsScreen.kt
│   │   │       ├── 📄 RemoteMachineSettings.kt
│   │   │       └── 📄 RemoteTerminalScreen.kt
│   │   ├── 📁 list/
│   │   │   └── 📁 ui/
│   │   │       └── 📄 RemoteMachinesScreen.kt
│   │   └── 📁 shared/
│   │       ├── 📁 logic/
│   │       │   └── 📄 RemoteMachinesViewModel.kt
│   │       └── 📁 ui/
│   │           ├── 📄 BinaryTestNotice.kt
│   │           ├── 📄 RemoteMachineComponents.kt
│   │           └── 📄 RemoteTerminalBridge.kt
│   ├── 📁 main/
│   │   └── 📁 ui/
│   │       └── 📄 FeaturesScreen.kt
│   ├── 📁 mcp/
│   │   ├── 📁 editor/
│   │   │   └── 📁 ui/
│   │   │       └── 📄 ServerEditorScreen.kt
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
│   │   │       ├── 📄 ModelEditorScreen.kt
│   │   │       ├── 📄 ProviderEditorScreen.kt
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
│   │       ├── 📄 AppSettingsScreen.kt
│   │       ├── 📄 CodeRenderingPreferences.kt
│   │       ├── 📄 DeveloperSettingsSection.kt
│   │       ├── 📄 MainAppbar.kt
│   │       └── 📄 SettingsComponents.kt
│   ├── 📁 speech/
│   │   └── 📁 ui/
│   │       └── 📄 SpeechServicesScreen.kt
│   ├── 📁 storage/
│   │   └── 📁 ui/
│   │       └── 📄 StorageScreen.kt
│   └── 📁 tools/
│       └── 📁 ui/
│           ├── 📄 ImageToolSettingsScreen.kt
│           ├── 📄 MediaRequestChainSettingsScreen.kt
│           ├── 📄 MediaRequestChainStepEditor.kt
│           ├── 📄 MediaRequestProfileEditorScreen.kt
│           ├── 📄 MediaTemplateAutocompleteField.kt
│           └── 📄 ToolsEditorScreen.kt
├── 📁 home/
│   ├── 📁 chat/
│   │   ├── 📁 logic/
│   │   │   ├── 📁 input/
│   │   │   │   └── 📄 ChatInputAreaViewModel.kt
│   │   │   ├── 📁 markdown/
│   │   │   │   ├── 📄 LatexDetector.kt
│   │   │   │   ├── 📄 MarkdownLibrary.kt
│   │   │   │   └── 📄 MathSplitter.kt
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
│   │       ├── 📄 LeftSideDrawer.kt
│   │       └── 📄 PromptLibraryDialog.kt
│   ├── 📁 shared/
│   │   ├── 📁 logic/
│   │   │   ├── 📄 ChatBackgroundService.kt
│   │   │   ├── 📄 ToolAttachments.kt
│   │   │   ├── 📄 ToolExecutionService.kt
│   │   │   ├── 📄 ToolMediaGenerationService.kt
│   │   │   ├── 📄 ToolRequestService.kt
│   │   │   └── 📄 ToolResultSummaries.kt
│   │   └── 📁 ui/
│   │       ├── 📄 ConversationSummaryBottomSheet.kt
│   │       ├── 📄 QuickActionsBottomSheet.kt
│   │       ├── 📄 ReasoningEffortBottomSheet.kt
│   │       ├── 📄 RemoteMachinesBottomSheet.kt
│   │       ├── 📄 SelectedModelOverviewBottomSheet.kt
│   │       ├── 📄 ToolDetailsBottomSheet.kt
│   │       ├── 📄 ToolSheetState.kt
│   │       └── 📄 ToolsManageBottomSheet.kt
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
│       ├── 📄 ExecutableBinaryStore.kt
│       ├── 📄 RemoteBinarySupport.kt
│       ├── 📄 RemoteMachineAssetsService.kt
│       ├── 📄 RemoteMachineCloudShellService.kt
│       ├── 📄 RemoteMachineConnectionManager.kt
│       ├── 📄 RemoteMachineService.kt
│       ├── 📄 RemoteMachineStore.kt
│       ├── 📄 RemoteTerminal.kt
│       ├── 📄 SshConfigParser.kt
│       └── 📄 SshConnectionSupport.kt
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
│       │   └── 📄 MediaViewerToolbar.kt
│       ├── 📁 menus/
│       │   ├── 📄 ItemOverflowMenu.kt
│       │   └── 📄 RoundedDropdownMenuItem.kt
│       ├── 📁 model/
│       │   ├── 📄 ModelInitial.kt
│       │   ├── 📄 ModelPickerBottomSheet.kt
│       │   ├── 📄 ModelPickerCards.kt
│       │   ├── 📄 ReasoningEffortControl.kt
│       │   └── 📄 ThinkingColorBurst.kt
│       ├── 📁 sheets/
│       │   ├── 📄 AppBottomSheet.kt
│       │   └── 📄 BottomSheetHeight.kt
│       ├── 📁 text/
│       │   ├── 📄 ApiKeyMask.kt
│       │   ├── 📄 GlyphSelectionGate.kt
│       │   ├── 📄 PlatformTextSelection.kt
│       │   ├── 📄 SelectableTextFields.kt
│       │   ├── 📄 StreamingTextFade.kt
│       │   └── 📄 WordWrappedFadeText.kt
│       ├── 📄 ProfileMcpPermissions.kt
│       ├── 📄 ToolControls.kt
│       └── 📄 ToolSwitch.kt
├── 📄 MainActivity.kt
└── 📄 GenerativeApplication.kt
```
## Legend

- 📁 Folder
- 📄 File
- 📦 Root package

## Rules

- Keep each source file at 500 lines or fewer, counting every line including blank lines and comments. This is a soft limit, not a strict rule: a file may exceed 500 lines when splitting it would not provide a meaningful benefit or would create unnecessary fragmentation.

### Layers

- Dependency direction: `shared` ← `integrations` ← `features` and `home` ← `navigation` ← `appwiring`. A layer may import only the layers to its left.
- `shared/` and `integrations/` never import `features/` or `home/`.
- `features/` and `home/` never import each other, and a feature never imports another feature. Code needed by both moves down to `shared/` (UI, models, helpers) or `integrations/` (API clients).
- `integrations/` holds code that calls an external API or service: HTTP clients, streaming/SSE readers, polling loops, OAuth flows, remote connections. A folder also keeps, next to its call files, the request builders, response parsers and small data types that only those files use.
- Place code by who uses it, as close to its users as possible. Code used by one layer stays in that layer (a builder used only by `integrations/media/` lives in `integrations/media/`; code used only by the chat tool flow lives in `home/chat/logic/tool/`). Only code needed by at least two of `features/`, `home/` and `integrations/` moves down to `shared/`.
- `shared/helpers/<topic>/` holds those multi-layer helpers: option validation and field descriptors, provider detection, tool schemas and argument parsers, input-file resolution. Their main job is preparing inputs or interpreting outputs, not I/O. Small, incidental I/O that is not worth its own integration (for example fetching a URL that a response returned, through the injected `ToolHttp` from `shared/networking`, or reading a file via `shared/files`) is tolerated in a helper as a stopgap. Once that I/O grows its own endpoint, authentication, streaming or polling protocol, or becomes the main purpose of the file, move that part into `integrations/`. Helpers never import `integrations/`.
- Plain data types (data classes, enums) follow the same rule: they sit in `shared/model/<topic>/` only when at least two layers use them (for example `ImageOptionField`, `VideoApi`); otherwise they stay in the single layer's own `model/` or next to the code that uses them. Helpers import models; models never import helpers or integrations.
- Rule of thumb: count the layers that import a file. One layer means it stays there; two or more means `shared/`. Inside `integrations/`, only the files that perform the call are clients; the builders and parsers beside them belong to that integration and are not helpers. A little incidental I/O does not turn a helper into an integration; I/O that is the file's purpose does.
- Moving between screens goes through callbacks wired in `navigation/AppNavigation.kt`, never through a direct import of another screen.

### features/

- Every folder directly under `features/` except `main/` matches exactly one entry in `FeaturesScreen`; `main/` is that screen itself. Folder names are short, lowercase, a single word, with no underscores.

| FeaturesScreen entry | Folder |
| --- | --- |
| Chat Options | `chatoptions/` |
| Tools Editor | `tools/` |
| Providers | `providers/` |
| MCP | `mcp/` |
| Speech services | `speech/` |
| Remote machines | `machines/` |
| Storage | `storage/` |
| App settings | `settings/` |
| Icons | `icons/` |
| App data | `appdata/` |
| About | `about/` |

- A feature has either flat `ui/`, `logic/`, `model/` folders, or sub-features (for example `list/`, `editor/`, `details/`), each with its own `ui/`, `logic/`, `model/`, plus a `shared/` folder.
- Sub-features never import each other. Code used by more than one sub-feature goes in that feature's `shared/`.
- Do not repeat the feature name as a nested folder (no `storage/storage/`).

### home/

- `home/` contains the chat experience and its drawer. It is split into `chat/` (messages, input, markdown, tool UI), `drawer/`, and `shared/` (code used by more than one of them). Chat bottom sheets currently live in `home/shared/ui/`; `home/BottomSheetsLink.kt` is a top-level home component.
- Keep `chat/` and `drawer/` independent; common code goes in `home/shared/`.

### ui/, logic/, model/

| Folder | Contains | Rule |
| --- | --- | --- |
| `ui/` | Screens, sheets, dialogs, cards | Composable code only. Imports `logic/` and `model/` of the same feature. |
| `logic/` | ViewModels, coordinators, services, parsers | No `@Composable` and no `androidx.compose.material3` import. |
| `model/` | Data classes, enums, UI state | Never imports `ui/` or `logic/`. |

- Direction inside a feature: `ui` → `logic` → `model`. Never the other way.
- Sub-folders of `ui/` and `logic/` mirror each other (`chat/ui/markdown/` ↔ `chat/logic/markdown/`).
- If a `logic/` folder grows past about 10 files, split it into `viewmodel/` and `service/`.
- `shared/ui/` holds reusable UI; the other `shared/` and `integrations/` folders are named by role (`storage`, `networking`, `helpers`, `modelapis`...) and have no `ui/`/`logic/` split.

## Main feature

- `features/main/ui/FeaturesScreen.kt` is the untitled feature directory; its header displays the light-background app logo in every theme.

- The feature header asset is `drawable-nodpi/generative_logo_light.png`.

## Context and summary accounting

- API adapters emit one normalized usage snapshot per model request, including streaming/tool rounds. Context usage is the latest matching request's input plus its reported output, not a sum across requests. Cached input follows each protocol's accounting without double counting.
- Usage is stored with each response version in nullable relational columns (Room v12). Hashed history/configuration keys prevent reuse after an edit, branch, model/provider change, prompt/tool-settings change or compact. Old/imported history without usage falls back to the local estimator; new messages after a valid snapshot add only an explicitly labeled local estimate.
- The context details distinguish API usage, estimates and mixed counts. A missing output count remains explicitly unavailable rather than becoming a fabricated zero. The maximum context capacity still comes from model configuration/catalog metadata.
- Custom summary targets are not restricted to the slider's 6000-token range. Summary requests override the provider's default output budget, while explicit model output limits and input-plus-output context budgets remain enforced. Multiple-pass summaries reserve room for the requested final output.
