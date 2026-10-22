# Component and File Naming Standardization Plan

## Goal

Standardize UI component names and Kotlin source-file names so that a component's name communicates its role and expected interaction. Apply the vocabulary below consistently across the app while preserving current behavior, layout, navigation, and accessibility.

This file is the progress tracker. Resume at the first unchecked item, work from inventory and low-risk changes toward broader refactors, and keep this plan updated as decisions are made. Do not combine naming work with unrelated UI redesigns.

## Naming rules

Use PascalCase for Kotlin composables and types. A Kotlin UI source file should normally use the same name as its primary public composable/type. Keep the existing feature/domain prefix where useful (for example, `Provider...` or `Chat...`) and place files in the existing feature and `ui` package structure. Names below describe a component's role, not merely its visual appearance.

| Name | Use |
|---|---|
| `AwesomeScreen` | The primary screen for a specific feature or capability. |
| `AwesomeEditor` | A screen for editing a specific entity or configuration. It is commonly a child of `AwesomeScreen`, opened from an AppBar add button or an item's Edit action. |
| `AwesomeSettings` | A screen for configuring settings for a specific feature or entity. It is commonly opened from a settings action in the AppBar. |
| `AwesomeSheet` | Every bottom sheet. Replace the legacy, unnecessarily long `AwesomeBottomSheet` suffix with `AwesomeSheet`. |
| `AwesomeSideDrawer` | A surface that slides in from the left or right edge, covers more than half or all of the screen, and is not itself a screen. |
| `AwesomeDialog` | A dialog, and only a dialog. |
| `ContextMenu` | A compact menu of actions/options associated with an item in a list or similar collection. |
| `SideBar` | A vertical strip of details or controls at the left or right edge, occupying no more than 33% of the screen width. It is not an expanded side drawer. |
| `ToolBar` | A bar of interaction buttons belonging to a dialog, bottom sheet, or another UI component. |
| `FeatureButton` | An in-screen button that is not constrained to icon-only/narrow width and should normally show both an icon and a title. It commonly opens a dialog, sheet, or screen to configure an extension of the category it belongs to. |
| `Dropdown` | A button or highlighted section that communicates that it can be pressed to reveal more content. Use for content that is too modest for a `FeatureButton` but not important enough to remain permanently visible. |
| `IconButton` | An icon-only or space-saving action used in an AppBar, `SideBar`, `ToolBar`, or elsewhere. |
| `DropdownOptions` | A control for choosing from a predefined set of options available for the associated item/configuration. |

Replace `Awesome` with the relevant feature or entity name (for example, `ChatScreen`, `ProviderEditor`, or `ModelConfigDialog`). Do not use these role words as interchangeable suffixes: classify by behavior and presentation. In particular, a `SideBar` is narrow and remains alongside content; an `AwesomeSideDrawer` expands over content; and `AwesomeScreen` is a navigable screen rather than an overlay.

## File naming and scope

- Align each renamed UI file with its primary composable/type and the role vocabulary above (for example, `ConversationSummarySheet.kt` for a bottom-sheet component).
- Rename composables and related private UI types where needed so file and declaration names agree. Do not rename unrelated models, services, resources, or packages solely to satisfy UI terminology.
- Treat file/class renames as source changes: update imports, call sites, navigation destinations, previews, tests, and any file-tree documentation together.
- Keep helper-only files named for their actual responsibility; the suffix rules apply when the file/component is a UI component of that role, not to every file that happens to contain a related word.
- Avoid compatibility aliases unless a concrete external/API constraint requires one. This is an internal naming cleanup; public behavior and persisted data must remain unchanged.

## TODO roadmap

### 1. Inventory and classification

- [x] Search the UI source tree for screen, editor, settings, bottom-sheet, drawer, dialog, context-menu, sidebar, toolbar, feature-button, dropdown, icon-button, and predefined-options components; record current names and file paths in the rename map and inventory audit notes.
- [x] Classify each candidate by its actual behavior using the rules above. Flag ambiguous cases for review instead of inferring from the current name alone; the `CodeBlockDrawer` correction is recorded above.
- [x] Identify references beyond source declarations (navigation, previews, tests, documentation, resource names, and scripts) and group renames into small, reviewable batches; affected references were updated with each rename.

### 2. Establish the naming map

- [x] Create a concise old-name → new-name mapping for each confirmed rename, preserving useful feature/entity prefixes (see the progress log).
- [x] Resolve the initial ambiguous cases: the conversation panel is a side drawer, the terminal sessions panel is a sidebar, and the current `EditorScreen`/`SettingsScreen` components are editors/settings rather than generic screens. Continue flagging any new ambiguous cases during the full inventory.
- [x] Confirm the file naming rule for components with multiple public composables: name the file after the primary entry-point component and keep closely related implementation details together unless they warrant a focused file. Existing grouped files remain grouped when they intentionally host related dialogs/helpers.

### 3. Rename UI components and files

- [x] Keep primary screens on the `AwesomeScreen` pattern where their current names clearly identify a feature screen; make editor and settings roles explicit where the old `...EditorScreen`/`...SettingsScreen` names were redundant.
- [x] Rename identified bottom-sheet files and declarations from `...BottomSheet` to `...Sheet`; remaining `BottomSheet` matches are framework APIs/helper internals, not component names.
- [x] Rename identified side drawer, context-menu, sidebar, and toolbar components to their respective role names; existing dialog components already use the `Dialog` role. Continue checking the full inventory for exceptions.
- [x] Normalize identified action and configuration controls to `FeatureButton`, `Dropdown`, `IconButton`, or `DropdownOptions` where applicable; retain meaningful domain prefixes when they improve clarity. Existing expandable controls and Material icon buttons already use the appropriate roles; no speculative dropdown-options wrapper was needed.
- [x] Update all affected references as each batch is renamed; keep each batch behavior-preserving and compile-coherent.

### 4. Verify and document

- [x] Update `docs/structure.md` and other affected documentation to match the current file tree and naming vocabulary.
- [x] Search again for stale filenames, declarations, imports, and obsolete `BottomSheet` component suffixes; remaining matches are framework APIs/helpers, orchestration names, or this plan's historical terminology.
- [ ] Run the project's relevant tests and lint checks together after all rename batches are complete; fix only issues caused by this work and record unrelated failures. Attempted `./gradlew testDebugUnitTest lintDebug`, but the environment has no `java` executable and no `JAVA_HOME`.
- [x] Review the final diff for accidental behavior, navigation, resource, or formatting changes. The source changes are naming/reference updates only; `git diff --check` passes.

## Non-goals

- No visual redesign or interaction changes.
- No package/module restructuring as part of the naming pass.
- No renaming of domain models, services, database fields, or persisted values unless a separate justified migration is approved.
- No broad cleanup of strings/resources unrelated to renamed component references.

## Progress log

### Initial behavior-preserving rename batch

| Previous name | Standardized name | Reason |
|---|---|---|
| `AppBottomSheet` / `AppBottomSheet.kt` | `AppSheet` / `AppSheet.kt` | Shared bottom-sheet host; use the concise sheet term. |
| `*BottomSheet` feature components and files | Matching `*Sheet` names | All identified bottom-sheet composables now use the standard suffix. |
| `*EditorScreen` editor components and files | Matching `*Editor` names | The role is already clear; the extra `Screen` suffix was redundant. |
| `*SettingsScreen` settings components and files | Matching `*Settings` names | The role is already clear; the extra `Screen` suffix was redundant. |
| `LeftSideDrawer` / `LeftSideDrawer.kt` | `ConversationSideDrawer` / `ConversationSideDrawer.kt` | Identifies the domain and overlay/drawer role rather than its fixed side. |
| `TerminalSessionsDrawer` | `TerminalSessionsSideBar` | This is an adjacent, compact terminal panel, not an overlay drawer. |
| `ItemOverflowMenu` and provider overflow menu components | Corresponding `*ContextMenu` names | These expose actions for a specific item/group. |
| `MediaViewerToolbar` / `MediaViewerToolbar.kt` | `MediaViewerToolBar` / `MediaViewerToolBar.kt` | Matches the specified `ToolBar` terminology. |
| Private `FeatureItem` | `FeatureButton` | It is a full-width, icon-and-title feature navigation action. |
| Private `CodeBlockDrawer` | `CodeBlockDialog` | The implementation is a full-screen `Dialog`, not an edge drawer. |

Updated imports, call sites, UI tests, and `docs/structure.md` with the renames. `BottomSheet` remains in framework/API names and helper implementation details (such as Material's `ModalBottomSheet` and the internal height calculation); those are not bottom-sheet component names and are intentionally unchanged.

### Inventory audit notes

- Feature roots that already end in `Screen` remain screens; editor/settings roles are now explicit without the redundant `Screen` suffix. `RemoteMachineSettings` already uses the settings role and remains unchanged.
- `ProviderModelCatalogSheet` already follows the `Sheet` rule. `BottomSheetsLink`, `ToolSheetState`, and `BottomSheetHeight` are orchestration/state/helper names, not sheet UI components.
- `ConversationSideDrawer` overlays the chat surface; the terminal session panel is adjacent to terminal content and is a sidebar. `CodeBlockDrawer` was actually a full-screen dialog and has been corrected to `CodeBlockDialog`.
- `ReasoningDropdown` and `ProcessingDropdown` already describe expandable content. Existing Material `IconButton` controls need no app-specific wrapper rename. Current predefined-choice controls are inline/feature-specific rather than standalone components, so no speculative `DropdownOptions` abstraction is introduced.
- AppBar/top-bar components and Android platform selection toolbar names refer to distinct APIs and are not renamed to `ToolBar`.

## Status

In progress. The naming and reference updates in this pass are complete, and the source/documentation audit is clean. Resume at the unchecked verification task: rerun tests and lint in an environment with Java configured, then record the results.
