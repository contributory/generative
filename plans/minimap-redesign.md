# Minimap redesign: conventional list scrollbar

The turn-grid navigator still produced wrong positions and unreliable swipes, so it was replaced from scratch rather than patched. The minimap is now a conventional, auto-appearing list scrollbar.

## Behavior

- **Show:** automatically while the chat list scrolls (finger or programmatic) and while the thumb is held; it fades after 1.5 idle seconds. There is no edge strip and no open/dismiss gesture, so the scrollbar imposes no horizontal-swipe restrictions: edge swipes keep their normal drawer/pager behavior. It never shows for an empty conversation and hides when the drawer opens or the conversation changes.
- **Rail:** a thin thumb between the app bar and the composer (minimum 48dp, widens while held). Only the thumb takes touches, and only vertical drags past touch slop; taps and horizontal swipes pass through. Dragging the thumb scrolls the list; while held, a small "n / total" turn counter appears beside it.

## Model

Position is measured in turn units from the lazy list's native visible items, clipped to the readable viewport (`minimapVisibleSpan`): fractional start/end such as `2.25`. `minimapThumb` turns that into progress and thumb height; `minimapPosition` is its inverse for dragging. There are no pixel-height estimates for off-screen content, so streaming growth never moves the thumb on its own.

While a finger holds the thumb, the drawn position and height come from the finger (frozen at gesture start) so the thumb does not jitter as the layout catches up. A thumb at the bottom always means the end of the latest content.

## Scrolling and following

Dragging calls `listState.scrollToItem(turn, fraction * size)` directly; the fraction is used only when the turn is on screen (its height is measured), otherwise the turn's start. Starting or reversing a scrub calls `onScrub(towardsEnd)`, which is `ChatMessageListState.manualScroll`: it cancels any in-flight request, stops following, and lets following resume once the latest content end is visible again.

## Verification

`ConversationMinimapLayoutTest` covers the geometry (pure Kotlin). `ConversationMinimapSideBarTest` and `HomePagerGestureUiTest` cover scroll-driven appearance and idle fade, the rail staying between the app bar and the composer, thumb drag, horizontal swipes/taps passing through the thumb, and the real `ChatMessageList`. Run them with:

`./gradlew :app:testDebugUnitTest --tests '*ConversationMinimap*' --tests '*HomePagerGestureUiTest'`
