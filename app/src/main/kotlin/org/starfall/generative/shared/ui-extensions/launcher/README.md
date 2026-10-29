# Launcher-style drag and drop (Jetpack Compose)

An independent Compose adaptation of the **architecture** used in AOSP Launcher3 /
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair) (Apache-2.0). This
is original Kotlin code for Compose, **not** a copy of Launcher3's View classes.
Reference: `DragController`, `DragView`, `Workspace`, `CellLayout`,
`FolderPagedView`, and `ReorderAlgorithm` on Lawnchair's `16-dev` branch.

## Components

- `LauncherDragDropHost`: a single stable pointer owner and visual drag overlay;
  keep it mounted while folders appear/disappear. Long-press begins dragging.
- `launcherDragSource`: registers a stable-key item, its measured window bounds,
  and callbacks, without owning the gesture.
- `launcherDropTarget`: registers a rectangular hit area. Higher-priority targets
  win; equally ranked tiles win over large parent areas.
- `LauncherDragDropState`: maintains one active item, pointer-to-shadow anchor,
  target enter/over/exit, accepted drop or cancellation. The original item can be
  disposed without canceling the gesture.
- `LauncherReorderLayout`: in-memory preview across named containers. No storage
  write occurs on hover. Commit only for an accepted drop and rollback on cancel.
- `LauncherGridGeometry`: nearest-cell detection based on measured layout bounds
  and the shadow's visual center.
- `LauncherHoverEffect`: cancelable delayed reorder/folder-open trigger.
- `LauncherFolderExitEffect`: cancelable delayed close when the pointer leaves a
  folder's registered area (not when it leaves an individual tile).
- `launcherDragAutoScroll`: viewport-measured, frame-driven lazy list/grid scroll.

## Integration contract

1. Call `rememberLauncherDragDropState<T>()` and put one
   `LauncherDragDropHost(state, preview = { ... })` **outside** all swappable
   folder/workspace content. Place the host around the region where both render.
2. Give every source a **stable unique key** and one `launcherDragSource`
   modifier. Hide its original render while `state.session?.item?.key == key`;
   the host paints the moving preview. Use real data IDs, never list indices.
3. Mark workspace/folder rectangles with `launcherDropTarget`. Child tiles may
   add higher-priority targets for insertion. Use distinct target keys such as
   `workspace`, `folder:<id>`, `slot:<id>`.
4. In each source's `onDragStart`, call `layout.begin(itemId)`. During hover,
   use `layout.hover(containerKey, tileKey, after = ...)`. To avoid jitter,
   trigger this from `LauncherHoverEffect` (not every pointer tick).
5. Return `true` from the receiving target's `onDrop` only when permitted.
   In the **source** `onDragEnd`, call `layout.commit()` and persist once
   when `outcome.accepted`, otherwise call `layout.cancel()`.
6. For leaving an open folder, register its *content region* as a target and use
   `LauncherFolderExitEffect`. Only remove the folder UI, **not** the host or
   drag state. Workspace targets then continue receiving the same drag session.
7. Apply `launcherDragAutoScroll(state, lazyGridState)` to the list/grid viewport
   when necessary. Drag targets should be registered on the actual tiles.
8. Keep mutually exclusive surfaces' `enabled` flags synchronized with
   visibility; a visually covered workspace must not intercept a folder drag.

The host handles **internal touch dragging**. It deliberately does not replace
Android's platform `dragAndDropSource/Target` for exchanging `ClipData` with
other apps. It also does not persist entities, create folders, or impose a
particular grid span/occupancy policy: callers own those product-specific rules.

This directory is an independent reusable foundation. It has **not** replaced
the ongoing `ProvidersScreen` drag-and-drop implementation.

## Manual device sandbox

Open **App Menu → App Settings → Preferences → Developer → Launcher drag test**.
This navigates to a separate route (`launcher_drag_test`) with in-memory dummy
items only. Long-press a tile to reorder; tap Folder to open the overlay; move
a tile into the folder by holding over its icon; open the folder, long-press a
child, drag beyond the folder panel and hold for the delayed exit before
releasing on the workspace. Scroll near the grid edges, press Back/Cancel
during a drag to verify rollback, or tap Reset to restore the sample data.
The screen shows the active source, hovered target and outcome. No real
provider data or persistent storage is affected.
