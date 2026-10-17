# Fluent UI System Icons migration

Status: **implemented in the working tree; verification in progress.** The app now uses a central compatibility facade at `ui/icons/Icons.kt`; call sites keep their existing `Icons.Outlined.*` / `Icons.Default.*` spelling while resolving to Fluent `ImageVector`s.

## Runtime dependency

```kotlin
implementation("io.github.niyajali:fluentui-system-icons:1.0.1")
```

This is a third-party Kotlin/Compose port of Microsoft's Fluent UI System Icons, published under the MIT license. Upstream project: https://github.com/niyajali/fluentui-system-icons. Microsoft’s official Android AAR (`com.microsoft.design:fluent-system-icons`) exposes `Drawable` resources and does **not** include a `Light` style, so it cannot provide the requested `ImageVector` API directly.

## Light style and limitations

The Compose port exposes `FluentIcons.Light`, `FluentIcons.Regular`, `FluentIcons.Filled`, and `FluentIcons.Color`. Its Light collection is only a subset of the full icon set. The current compatibility mapping uses **43 Light icons and 56 Regular fallbacks** for 99 unique Material icon names. The fallback is explicit in `Icons.kt`; it avoids inventing non-existent Light variants, but it means the migration is not yet 100% Light. To make every icon Light-only, matching Light vector definitions must be added for the missing icons or those icons must be replaced with visually acceptable Light equivalents.

The current facade intentionally centralizes all mapping decisions. `Icons.Outlined`, `Icons.Default`, `Icons.Filled`, and `Icons.AutoMirrored.*` all resolve to the Fluent mapping so existing call sites and shared APIs that accept `ImageVector` continue to compile. Directional icons are mapped centrally; verify left/right direction in RTL before release.

## Scope and behavior preserved

- Replaced the `androidx.compose.material:material-icons-extended` dependency with `io.github.niyajali:fluentui-system-icons:1.0.1`.
- Removed Material icon imports from app source and migrated 55 Kotlin files to the central facade.
- Kept existing `contentDescription` strings, click handlers, tinting, sizing, and layout code unchanged.
- Kept custom provider/model/MCP icons and the existing `IconStore` image-resolution path unchanged.
- Kept `res/drawable/ic_side_navigation.xml` out of scope.

## Verification

- `./gradlew :legacyApp:compileDebugKotlin --no-daemon` — **passed**.
- `./gradlew :legacyApp:testDebugUnitTest --no-daemon` — currently blocked by a pre-existing test compilation error in `ChatOpeningScrollTest.kt:72`: the test call to `ChatScreen` omits the required `onNewChat` parameter. This is unrelated to icon migration and was not changed here.
- Before release, visually inspect common actions, back/forward arrows, settings, file/media icons, and RTL layouts. Some Material-to-Fluent matches are semantic approximations, not pixel-identical substitutions.

## Icon inventory

`Fluent candidate` records the original proposed semantic match. The implemented mapping lives in `legacyApp/src/main/java/org/starfall/multigateway/ui/icons/Icons.kt`; a `?` means the semantic match needs visual review. The file list below records where each Material icon currently occurred.

| Material icon | Style | Uses | Fluent candidate |
| --- | --- | ---: | --- |
| `ArrowBack` | AutoMirrored.Filled | 21 | ArrowLeft |
| `ArrowForward` | AutoMirrored.Filled | 2 | ArrowRight |
| `ArrowForwardIos` | AutoMirrored.Filled | 1 | ChevronRight |
| `KeyboardArrowRight` | AutoMirrored.Filled | 2 | ChevronRight |
| `Send` | AutoMirrored.Filled | 2 | Send |
| `Logout` | AutoMirrored.Outlined | 1 | SignOut |
| `Add` | Default | 15 | Add |
| `ArrowDropDown` | Default | 1 | CaretDown |
| `ArrowDropUp` | Default | 1 | CaretUp |
| `ArrowUpward` | Default | 2 | ArrowUp |
| `Check` | Default | 5 | Checkmark |
| `CheckCircle` | Default | 1 | CheckmarkCircle |
| `ChevronRight` | Default | 1 | ChevronRight |
| `Close` | Default | 8 | Dismiss |
| `Error` | Default | 1 | ErrorCircle |
| `ExpandLess` | Default | 2 | ChevronUp |
| `ExpandMore` | Default | 4 | ChevronDown |
| `GridView` | Default | 4 | Grid |
| `KeyboardArrowDown` | Default | 9 | ChevronDown |
| `KeyboardArrowRight` | Default | 4 | ChevronRight |
| `KeyboardArrowUp` | Default | 1 | ChevronUp |
| `List` | Default | 4 | TextBulletList |
| `Menu` | Default | 1 | Navigation |
| `MoreHoriz` | Default | 1 | MoreHorizontal |
| `MoreVert` | Default | 8 | MoreVertical |
| `Pause` | Default | 2 | Pause |
| `PlayArrow` | Default | 6 | Play |
| `PushPin` | Default | 2 | Pin |
| `Search` | Default | 2 | Search |
| `Stop` | Default | 2 | Stop |
| `Check` | Filled | 1 | Checkmark |
| `ExpandLess` | Filled | 1 | ChevronUp |
| `ExpandMore` | Filled | 1 | ChevronDown |
| `AccountCircle` | Outlined | 2 | PersonCircle |
| `Add` | Outlined | 3 | Add |
| `AddPhotoAlternate` | Outlined | 1 | ImageAdd |
| `AttachFile` | Outlined | 1 | Attach |
| `Audiotrack` | Outlined | 5 | MusicNote1 |
| `BrokenImage` | Outlined | 1 | ImageOff |
| `Build` | Outlined | 2 | Wrench |
| `CameraAlt` | Outlined | 1 | Camera |
| `Chat` | Outlined | 1 | Chat |
| `ChatBubbleOutline` | Outlined | 2 | ChatBubblesQuestion? |
| `Check` | Outlined | 2 | Checkmark |
| `CheckCircle` | Outlined | 3 | CheckmarkCircle |
| `Close` | Outlined | 11 | Dismiss |
| `Cloud` | Outlined | 2 | Cloud |
| `CloudQueue` | Outlined | 2 | Cloud |
| `Code` | Outlined | 2 | Code |
| `Computer` | Outlined | 5 | Desktop |
| `ContentCopy` | Outlined | 7 | Copy |
| `Create` | Outlined | 3 | Compose |
| `CreateNewFolder` | Outlined | 2 | FolderAdd |
| `Delete` | Outlined | 14 | Delete |
| `DeleteSweep` | Outlined | 2 | DeleteLines? |
| `Description` | Outlined | 1 | Document |
| `Deselect` | Outlined | 2 | SelectObjectSkewDismiss? |
| `Download` | Outlined | 4 | ArrowDownload |
| `Edit` | Outlined | 11 | Edit |
| `EditNote` | Outlined | 1 | DocumentEdit |
| `ErrorOutline` | Outlined | 2 | ErrorCircle |
| `ExpandLess` | Outlined | 1 | ChevronUp |
| `ExpandMore` | Outlined | 1 | ChevronDown |
| `Extension` | Outlined | 8 | PuzzlePiece |
| `FileDownload` | Outlined | 1 | ArrowDownload |
| `FileOpen` | Outlined | 3 | FolderOpen |
| `FilterList` | Outlined | 1 | Filter |
| `Folder` | Outlined | 9 | Folder |
| `FolderOpen` | Outlined | 4 | FolderOpen |
| `FormatListBulleted` | Outlined | 2 | TextBulletList |
| `Fullscreen` | Outlined | 2 | FullScreenMaximize |
| `FullscreenExit` | Outlined | 1 | FullScreenMinimize |
| `Hub` | Outlined | 2 | Share? |
| `Image` | Outlined | 12 | Image |
| `Info` | Outlined | 1 | Info |
| `InsertDriveFile` | Outlined | 5 | Document |
| `Key` | Outlined | 4 | Key |
| `Keyboard` | Outlined | 1 | Keyboard |
| `Language` | Outlined | 1 | Globe |
| `Lightbulb` | Outlined | 1 | Lightbulb |
| `LinkOff` | Outlined | 1 | LinkDismiss |
| `Lock` | Outlined | 4 | LockClosed |
| `ManageHistory` | Outlined | 1 | History |
| `Memory` | Outlined | 1 | DeveloperBoard |
| `MoreVert` | Outlined | 2 | MoreVertical |
| `NetworkCheck` | Outlined | 3 | Wifi1? |
| `OpenInBrowser` | Outlined | 2 | Open |
| `OpenInNew` | Outlined | 1 | Open |
| `PlayArrow` | Outlined | 2 | Play |
| `Psychology` | Outlined | 3 | Brain? |
| `PushPin` | Outlined | 2 | Pin |
| `RadioButtonUnchecked` | Outlined | 1 | Circle |
| `RecordVoiceOver` | Outlined | 3 | PersonVoice |
| `Refresh` | Outlined | 7 | ArrowClockwise |
| `RestartAlt` | Outlined | 1 | ArrowCounterclockwise |
| `Restore` | Outlined | 2 | History |
| `Save` | Outlined | 3 | Save |
| `Search` | Outlined | 2 | Search |
| `SelectAll` | Outlined | 2 | SelectAllOn |
| `Settings` | Outlined | 4 | Settings |
| `Share` | Outlined | 2 | Share |
| `StopCircle` | Outlined | 5 | RecordStop? |
| `Summarize` | Outlined | 1 | TextDescription |
| `SystemUpdateAlt` | Outlined | 1 | ArrowDownload |
| `Terminal` | Outlined | 1 | WindowConsole |
| `Tune` | Outlined | 1 | Options |
| `UploadFile` | Outlined | 4 | DocumentArrowUp |
| `Videocam` | Outlined | 10 | Video |
| `Visibility` | Outlined | 6 | Eye |
| `VisibilityOff` | Outlined | 5 | EyeOff |
| `VolumeUp` | Outlined | 1 | Speaker2 |
| `Warning` | Outlined | 1 | Warning |
| `WrapText` | Outlined | 1 | TextWrap |


## Implementation notes

The implementation uses the Compose icon port and the central `org.starfall.multigateway.ui.icons.Icons` facade, not Android `R.drawable` resources. `Icons.kt` is the authoritative source for the actual Fluent names and whether each mapping uses `Light` or `Regular`. The inventory and file map below document the old Material call sites; they are not the source of truth for final icon names when they differ from the compiled facade.

Preserve `contentDescription` values and click behavior. Review approximations such as `Deselect`, `SelectAll`, and `OpenInNew` before release. Do not re-add `material-icons-extended` or change the external provider/model/MCP icon flow.

## Icon-to-file map

Each entry maps an old Material icon to its proposed Fluent name and lists the Kotlin source files containing it. Paths are repository-relative. The actual compiled mapping and style are defined in `ui/icons/Icons.kt`.

### `Add` → `Add`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpConnectionSettings.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderHeadersDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/settings/IconSettingsView.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ToolSettingsScreens.kt`

### `AddPhotoAlternate` → `ImageAdd`

`legacyApp/src/main/java/org/starfall/multigateway/ui/settings/IconSettingsView.kt`

### `ArrowBack` → `ArrowLeft`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AssistantMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/drawer/PromptLibraryDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpServerEditor.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/preview/CodePreviewActivity.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/profiles/ProfileScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderOAuthAccountsDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/settings/SettingsScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ImageToolSettingsScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ToolSettingsScreens.kt`

### `ArrowDropDown` → `CaretDown`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderConnectionFields.kt`

### `ArrowDropUp` → `CaretUp`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderConnectionFields.kt`

### `ArrowForward` → `ArrowRight`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AssistantMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`

### `ArrowForwardIos` → `ChevronRight`

`legacyApp/src/main/java/org/starfall/multigateway/ui/settings/SettingsComponents.kt`

### `ArrowUpward` → `ArrowUp`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/InputBar.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`

### `AttachFile` → `Attach`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/InputBar.kt`

### `Audiotrack` → `MusicNote1`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AttachmentPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `BrokenImage` → `ImageOff`

`legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `Build` → `Wrench`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `CameraAlt` → `Camera`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`

### `ChatBubbleOutline` → `ChatBubblesQuestion?`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MessageBubble.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `Check` → `Checkmark`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelConfigDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ImageToolSettingsScreen.kt`

### `CheckCircle` → `CheckmarkCircle`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AttachmentPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `Close` → `Dismiss`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ChatScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/EntityIcon.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaViewerToolbar.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderHeadersDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/settings/IconSettingsView.kt`

### `Cloud` → `Cloud`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/RemoteMachinesSheet.kt`

### `Code` → `Code`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `Computer` → `Desktop`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/RemoteMachinesSheet.kt`

### `ContentCopy` → `Copy`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownLatexBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownRenderer.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MessageBubble.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ToolActivityCards.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`

### `Create` → `Compose`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ChatAppBar.kt`

### `Delete` → `Delete`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ConversationSummaryUi.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ItemOverflowMenu.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaViewerToolbar.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpConnectionSettings.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/profiles/ProfileScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderHeadersDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `DeleteSweep` → `DeleteLines?`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`

### `Description` → `Document`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`

### `Download` → `ArrowDownload`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ChatFilePreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/EntityIcon.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaViewerToolbar.kt`

### `Edit` → `Edit`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ItemOverflowMenu.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/profiles/ProfileScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderModelsPane.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `Error` → `ErrorCircle`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`

### `ErrorOutline` → `ErrorCircle`

`legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpServerCard.kt`

### `ExpandLess` → `ChevronUp`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AssistantMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/LiveThinkingBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ReasoningDropdown.kt`

### `ExpandMore` → `ChevronDown`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AssistantMessageCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/LiveThinkingBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ReasoningDropdown.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`

### `Extension` → `PuzzlePiece`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/QuickActionsSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpConnectionSettings.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpServerCard.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpToolsTab.kt`

### `Folder` → `Folder`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `FolderOpen` → `FolderOpen`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `FormatListBulleted` → `TextBulletList`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderModelsPane.kt`

### `Fullscreen` → `FullScreenMaximize`

`legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaPreview.kt`

### `FullscreenExit` → `FullScreenMinimize`

`legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaPreview.kt`

### `GridView` → `Grid`

`legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `Image` → `Image`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/QuickActionsSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/EntityIcon.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ToolSettingsScreens.kt`

### `InsertDriveFile` → `Document`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AttachmentPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/QuickActionsSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ToolActivityCards.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ChatFilePreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `Key` → `Key`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `Keyboard` → `Keyboard`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `KeyboardArrowDown` → `ChevronDown`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ChatScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ReasoningDropdown.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpOAuthFields.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpToolsTab.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderModelCatalogSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `KeyboardArrowRight` → `ChevronRight`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ReasoningDropdown.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpOAuthFields.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpToolsTab.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ToolSettingsScreens.kt`

### `KeyboardArrowUp` → `ChevronUp`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderModelCatalogSheet.kt`

### `Language` → `Globe`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderConnectionFields.kt`

### `Lightbulb` → `Lightbulb`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ReasoningDropdown.kt`

### `LinkOff` → `LinkDismiss`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `List` → `TextBulletList`

`legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `Lock` → `LockClosed`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `Logout` → `SignOut`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/OAuthAccountCard.kt`

### `Memory` → `DeveloperBoard`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `Menu` → `Navigation`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ChatAppBar.kt`

### `MoreHoriz` → `MoreHorizontal`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AssistantMessageCard.kt`

### `MoreVert` → `MoreVertical`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ItemOverflowMenu.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/settings/IconSettingsView.kt`

### `NetworkCheck` → `Wifi1?`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderEditorScreen.kt`

### `Pause` → `Pause`

`legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaPreview.kt`

### `PersonOff` → `REVIEW`

`legacyApp/src/main/java/org/starfall/multigateway/ui/profiles/ProfileScreen.kt`

### `PlayArrow` → `Play`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AttachmentPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/ChatFilePreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `Psychology` → `Brain?`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `RadioButtonUnchecked` → `Circle`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/AttachmentPreview.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`

### `RecordVoiceOver` → `PersonVoice`

`legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `Refresh` → `ArrowClockwise`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/preview/CodePreviewActivity.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderModelCatalogSheet.kt`

### `RestartAlt` → `ArrowCounterclockwise`

`legacyApp/src/main/java/org/starfall/multigateway/ui/settings/CodeRenderingPreferences.kt`

### `Restore` → `History`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`

### `Save` → `Save`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderOAuthAccountsDialog.kt`

### `Search` → `Search`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/settings/IconSettingsView.kt`

### `Send` → `Send`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserMessageCard.kt`

### `Settings` → `Settings`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ChatAppBar.kt`

### `Share` → `Share`

`legacyApp/src/main/java/org/starfall/multigateway/ui/components/MediaViewerToolbar.kt`

### `Stop` → `Stop`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/InputBar.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`

### `StopCircle` → `RecordStop?`

`legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelConnectionDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/speech/SpeechScreen.kt`

### `Summarize` → `TextDescription`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ConversationSummaryUi.kt`

### `Terminal` → `WindowConsole`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `Tune` → `Options`

`legacyApp/src/main/java/org/starfall/multigateway/ui/profiles/ProfileScreen.kt`

### `UnfoldLess` → `REVIEW`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `UnfoldMore` → `REVIEW`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`

### `UploadFile` → `DocumentArrowUp`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`

### `Videocam` → `Video`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/FilesActionSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ModelPickerSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/QuickActionsSheet.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/UserInputArea.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ModelEditScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/StorageScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/tools/ToolSettingsScreens.kt`

### `Visibility` → `Eye`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpConnectionSettings.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderConnectionFields.kt`

### `VisibilityOff` → `EyeOff`

`legacyApp/src/main/java/org/starfall/multigateway/ext-services/remote/RemoteMachinesScreen.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/mcp/McpConnectionSettings.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderApiKeysDialog.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/providers/ProviderConnectionFields.kt`

### `WrapText` → `TextWrap`

`legacyApp/src/main/java/org/starfall/multigateway/ui/chat/MarkdownCodeBlock.kt`, `legacyApp/src/main/java/org/starfall/multigateway/ui/chat/ToolActivityCards.kt`

