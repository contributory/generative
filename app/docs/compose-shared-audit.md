# Shared UI: native Compose audit (2026-10-29)

Scope: the entire shared Kotlin tree (286 source files before cleanup, including
generated Fluent icons), plus feature-level consumers and UI regression tests.
Remove code reproducing native Compose APIs, not distinctive app visuals or behavior.

## Replaced or removed

| Component | Obsolete behavior | Native equivalent / resolution |
| --- | --- | --- |
| shared/ui/sheets/BottomSheet | Custom drag recognizer, arbitrary height anchors, scroll/fling interception, overscroll suppression, manual measurement and resize state | Material 3 ModalBottomSheet, SheetState (Hidden / PartiallyExpanded / Expanded), native drag handle and nested scrolling. Retain safe height cap, theme, photo and decorative brush. |
| shared/ui/sheets/BottomSheetHeight.kt | Window-height calculations and synthetic drag/scroll physics | Deleted. Height and drag anchors belong to Material. |
| shared/ui/sheets/SlideSheetContent.kt | Android 13+ OnBackInvokedDispatcher registration | Native ModalBottomSheetProperties.shouldDismissOnBackPress and Compose BackHandler for nested pages. AnimatedContent remains for actual page transitions. |
| home/shared/ui/QuickActionsSheet.kt | Custom viewport height, saved manual drag height and scroll resets | Native half/full sheet anchors and remembered ScrollState. |
| shared/ui/menus/AppPopup.kt | Parameter-for-parameter wrapper around Popup | Deleted. Consumers call Compose Popup. |
| shared/ui/layout/ScreenCover.kt | Full-window PopupPositionProvider returning (0,0) | Full-window Compose Dialog, retaining the app's clickable media scrim. |
| shared/ui/layout/ResponsiveLayout.kt | Unused window-width helper | Deleted unused helper; retained used height helpers. |
| home/BottomSheetsLink.kt | Separate Boolean for Quick Actions visibility, picker-origin flag, delayed Skills-menu counter | One nullable page per sheet and direct Skills callback. |
| home/chat | ModelPicker open-request counter and retained sheet state | Foreground-scoped Compose state shared between AppBar and composer. |
| all 14 values*/strings.xml files | Obsolete manual sheet_resize action label | Removed in every language. |

Obsolete drag/height tests and popup-position tests were removed. Transition
tests focus on page identity and routing instead of arbitrary hand-authored
sheet heights; new tests cover native Dialog and single-source routing state.

## Inspected and intentionally retained

- Material-themed wrappers (Dialog, DropdownMenu, DropdownMenuItem,
  ItemSurface, ItemCard): consistent AMOLED styling, user-profile
  backgrounds, borders, interactive layouts and fixed title/action regions.
  They are not alternative modal/menu engines.
- Selection/input (PlatformTextSelection, GlyphSelectionGate,
  OutlinedTextField): Android selection action mode, exact message
  glyph hits, IME composition and explicit triple-tap Select All behavior.
- Navigation/animation (RetainedScreenContent, ScreenSlideMotion,
  SlideSheetContent's AnimatedContent): preserves live editor/composer pages,
  app-specific screen transition direction and linked sheet page transitions.
- Icons/media (SharedIcon, FluentUi, icon vectors, MediaPlaybackState,
  MediaFileCard): Fluent assets, drawable/ImageVector bridging and Media3
  playback lifecycle.
- Window integration (ConfigureBottomSheetWindow, AMOLED borders, inset
  helpers, ProfileBackground): transparent system bars and themed visuals.
- Other shared modules (models, authentication, networking, files, storage,
  terminal, media and helpers) implement domain/platform integrations rather
  than Flutter widget APIs.

## Verification before release

The sheet uses a full-height native Material 3 surface with two visible anchors:
PartiallyExpanded (the default roughly halfway up the screen) and Expanded
(immediately below the status bar). Hidden is the dismiss state, not an open
stop. The status-bar inset constrains the actual surface without custom drag
or position math. Fixed-fraction content retains its own layout fraction within
the surface, but does not remove either native anchor. The default partial
stop is intentionally Material's half-height anchor, not the former arbitrary
content-sized or per-sheet drag height.

After replacing bespoke sheet interactions with native Material 3 anchors,
check on a device: status-bar cap, IME resizing, native half/full scroll
handoff, Back on Android 13+, Quick Actions subpage transitions, and media
viewer Dialog. Do not reimplement arbitrary drag heights simply to satisfy
obsolete tests.

No Gradle run unless explicitly requested (AGENTS.md). Preserve unrelated
working-tree changes.
