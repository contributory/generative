package org.starfall.generative.shared.ui.sheets

import android.view.View
import android.view.Window
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.starfall.generative.shared.ui.background.LocalProfileModalImages
import org.starfall.generative.shared.ui.background.ProfileBackground
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.theme.modalScrimColor
import org.starfall.generative.shared.ui.layout.amoledSheetBorder
import org.starfall.generative.shared.ui.layout.windowHeight
import androidx.compose.ui.graphics.drawscope.DrawScope

internal val LocalBottomSheetMinContentHeight = compositionLocalOf { 0.dp }

/** Distinguishes user-resized sheets from their default content-measured opening height. */
internal val LocalBottomSheetManuallyResized = compositionLocalOf { false }

/** Tallest content the sheet can show (window cap minus drag handle and any reserved navigation inset). */
internal val LocalBottomSheetMaxContentHeight = compositionLocalOf { 0.dp }

/** Expand before scrolling upward; let downward motion at the list start reach Material. */
internal fun bottomSheetListScrollBoundary(
    expand: (Float) -> Float = { 0f }
): NestedScrollConnection = object : NestedScrollConnection {
    private var upwardFling = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) upwardFling = false
        return if (available.y < 0f && (source == NestedScrollSource.UserInput || upwardFling)) {
            Offset(0f, expand(available.y).coerceIn(available.y, 0f))
        } else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        upwardFling = available.y < 0f
        return Velocity.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && available.y < 0f) Offset(0f, available.y) else Offset.Zero
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        upwardFling = false
        return if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
    }
}

/** Material owns the dialog, scrim, accessibility, insets and show/hide lifecycle. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    containerBrush: Brush? = null,
    containerOverlay: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null,
    /** Screen-space origin of a circular reveal of [containerBrush]; null means no reveal. Read while drawing. */
    containerBrushRevealOrigin: (() -> Offset?)? = null,
    /** Reveal progress in 0..1 (the circle is full at 0.52, matching the thinking bloom). Read while drawing. */
    containerBrushRevealProgress: (() -> Float)? = null,
    /** When present, collapse the outgoing brush toward its reveal origin (1 = full, 0 = gone). */
    containerBrushRetractionProgress: (() -> Float?)? = null,
    navigationBarColor: Color? = null,
    dragHandleHeight: Dp = 48.dp,
    dragHandleWidth: Dp = 32.dp,
    /** Let scrollable content draw beneath the transparent navigation bar. Callers must keep
     * their final actionable row above the system gesture area using list content padding. */
    contentBehindNavigationBar: Boolean = false,
    /** When set, the sheet is this fraction of the available height regardless of content; null wraps content. */
    fixedHeightFraction: Float? = null,
    /** Changing this key releases any manual drag height so the sheet adapts to the new page's content. */
    contentKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val windowHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    val windowBoundsHeight = rememberSheetWindowHeight(windowHeight)
    var requestedHeight by remember { mutableStateOf<Float?>(null) }
    var previousContentKey by remember { mutableStateOf(contentKey) }
    if (contentKey != previousContentKey) {
        previousContentKey = contentKey
        if (fixedHeightFraction == null) requestedHeight = null
    }
    // Read measuredHeight only in gesture/semantics callbacks. Reading it here would
    // recompose the whole sheet on every frame of a page-height animation.
    var measuredHeight by remember { mutableIntStateOf(0) }
    var containerOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    val handleCoordinates = remember { HandleCoordinates() }
    val latestRequestedHeight by rememberUpdatedState(requestedHeight)
    // The last brush that was fully on screen. While a new brush is revealed in a circle it is kept
    // underneath, otherwise the old colour would vanish and be replaced by the flat container colour.
    val settledBrush = remember { SettledBrush() }
    val resizeLabel = stringResource(R.string.sheet_resize)
    val amoled = LocalAmoledMode.current
    val sheetImage = LocalProfileModalImages.current.sheet
    val sheetTransparency = LocalProfileModalImages.current.sheetTransparency
    val sheetModifier = if (amoled) {
        modifier.amoledSheetBorder(sheetState, shape, MaterialTheme.colorScheme.outlineVariant, 1.dp)
    } else modifier
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = sheetModifier.testTag("app-bottom-sheet"),
        shape = shape,
        containerColor = if (amoled) Color.Black else containerColor.copy(alpha = 1f),
        scrimColor = modalScrimColor(BottomSheetDefaults.ScrimColor),
        dragHandle = null,
        // The surface itself is capped below the top safe inset; adding it as content
        // padding could make Material's surface extend back into the status bar.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        }
    ) {
        ConfigureBottomSheetWindow(navigationBarColor ?: containerColor)
        // Retain free-form sizing while Material owns the dialog and its lifecycle.
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("bottom-sheet-window")) {
            val density = LocalDensity.current
            val topInsetPx = WindowInsets.safeDrawing.getTop(density)
            val bounds = bottomSheetHeightBounds(windowHeight, constraints.maxHeight, topInsetPx, windowBoundsHeight)
            val scrollBoundary = remember(bounds) {
                bottomSheetListScrollBoundary { availableY ->
                    val currentHeight = (requestedHeight ?: measuredHeight.toFloat())
                        .coerceIn(bounds.minimum.toFloat(), bounds.maximum.toFloat())
                    val consumed = bounds.consumeUpwardScroll(currentHeight, availableY)
                    if (consumed < 0f) requestedHeight = currentHeight - consumed
                    consumed
                }
            }
            val bottomInsetPx = if (contentBehindNavigationBar) {
                0
            } else {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(density)
            }
            val dragHandlePx = with(density) { dragHandleHeight.roundToPx() }
            val effectiveMinSheetHeight = fixedHeightFraction?.let { fraction ->
                val initialHeight = (bounds.maximum * fraction.coerceIn(0f, 1f)).roundToInt()
                bounds.heightFor(initialHeight, requestedHeight)
            } ?: requestedHeight?.let { bounds.heightFor(0, it) } ?: bounds.minimum
            val minContentHeight = with(density) {
                (effectiveMinSheetHeight - dragHandlePx - bottomInsetPx).coerceAtLeast(0).toDp()
            }
            val maxContentHeight = with(density) {
                (bounds.maximum - dragHandlePx - bottomInsetPx).coerceAtLeast(0).toDp()
            }
            var resizeDrag by remember { mutableStateOf<ResizeDrag?>(null) }
            Layout(content = {
                Box(Modifier.fillMaxWidth().onGloballyPositioned {
                    containerOriginOnScreen = it.localToScreen(Offset.Zero)
                }) {
                    // A bottom sheet always has an opaque Material surface beneath these layers.
                    // Keep the photo below decorations; the decoration is blended so a photo
                    // remains visible when maximum reasoning effort is selected.
                    ProfileBackground(
                        sheetImage,
                        Modifier.matchParentSize(),
                        sheetTransparency
                    )
                    Box(Modifier.matchParentSize().drawBehind {
                        if (containerBrush == null) {
                            settledBrush.brush = null
                            return@drawBehind
                        }
                        val brushAlpha = sheetDecorationBrushAlpha(
                            sheetImage, sheetTransparency
                        )
                        fun drawTarget() {
                            drawRect(containerBrush, alpha = brushAlpha)
                            containerOverlay?.invoke(this)
                        }
                        val localOrigin = containerBrushRevealOrigin?.invoke()?.let { it - containerOriginOnScreen }
                        val progress = (containerBrushRevealProgress?.invoke() ?: 1f).coerceIn(0f, 1f)
                        val retraction = containerBrushRetractionProgress?.invoke()
                        if (localOrigin != null && retraction != null) {
                            // Keep the ordinary sheet surface visible underneath while the
                            // outgoing maximum-effort colour shrinks into its slider detent.
                            settledBrush.brush = null
                            val radius = hypot(size.width, size.height) * retraction.coerceIn(0f, 1f)
                            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(localOrigin, radius)) }) {
                                drawTarget()
                            }
                        } else if (localOrigin == null || progress >= 0.52f) {
                            drawTarget()
                            settledBrush.brush = containerBrush
                        } else {
                            // Keep the outgoing tint visible until the expanding reveal covers it.
                            settledBrush.brush?.takeIf { it != containerBrush }?.let {
                                drawRect(it, alpha = brushAlpha)
                            }
                            val radius = 14.dp.toPx() + hypot(size.width, size.height) *
                                (progress / 0.52f).coerceIn(0f, 1f)
                            clipPath(
                                Path().apply { addOval(androidx.compose.ui.geometry.Rect(localOrigin, radius)) }
                            ) {
                                drawTarget()
                            }
                        }
                    })
                    Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { measuredHeight = it.height }
                        // Normally reserve the navigation inset for fixed controls. Scrollable
                        // list pages opt out and provide their own bottom content padding, so
                        // rows can move behind the transparent navigation bar as they scroll.
                        .then(
                            if (contentBehindNavigationBar) Modifier
                            else Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                            )
                        )
                ) {
                    Box(Modifier.fillMaxWidth().height(dragHandleHeight).testTag("bottom-sheet-drag-handle")
                        .onGloballyPositioned { handleCoordinates.value = it }
                        .pointerInput(bounds.minimum, bounds.maximum) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val startHeight = (latestRequestedHeight ?: measuredHeight.toFloat())
                                        .coerceIn(bounds.minimum.toFloat(), bounds.maximum.toFloat())
                                    val startY = handleCoordinates.value
                                        ?.localToScreen(startOffset)
                                        ?.y
                                    if (startY != null) {
                                        resizeDrag = ResizeDrag(startHeight, startY, startOffset.y)
                                    } else {
                                        resizeDrag = ResizeDrag(startHeight, startOffset.y, startOffset.y)
                                    }
                                },
                                onDrag = { change, _ ->
                                    val drag = resizeDrag ?: return@detectDragGestures
                                    val currentY = handleCoordinates.value
                                        ?.localToScreen(change.position)
                                        ?.y
                                        ?: (drag.startY + change.position.y - drag.startOffsetY)
                                    requestedHeight = bounds.resizedHeight(
                                        drag.startHeight,
                                        currentY - drag.startY
                                    )
                                    change.consume()
                                },
                                onDragEnd = { resizeDrag = null },
                                onDragCancel = { resizeDrag = null }
                            )
                        }
                        .semantics {
                            contentDescription = resizeLabel
                            val range = (bounds.maximum - bounds.minimum).coerceAtLeast(1).toFloat()
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                ((measuredHeight - bounds.minimum) / range).coerceIn(0f, 1f), 0f..1f)
                            stateDescription = "${(measuredHeight * 100f / windowHeight.coerceAtLeast(1)).roundToInt()}%"
                            setProgress { fraction ->
                                requestedHeight = bounds.minimum + range * fraction.coerceIn(0f, 1f)
                                true
                            }
                        }, contentAlignment = Alignment.Center
                    ) { BottomSheetDefaults.DragHandle(Modifier.width(dragHandleWidth)) }
                    // Android stretch overscroll can consume edge deltas before the
                    // sheet receives them and retain a stretch between gestures.
                    // Give the list and sheet a single, continuous scroll handoff.
                    CompositionLocalProvider(
                        LocalOverscrollFactory provides null,
                        LocalBottomSheetMinContentHeight provides minContentHeight,
                        LocalBottomSheetMaxContentHeight provides maxContentHeight,
                        LocalBottomSheetManuallyResized provides (requestedHeight != null)
                    ) {
                        Column(Modifier.fillMaxWidth().nestedScroll(scrollBoundary), content = content)
                    }
                }
                }
            }) { measurables, constraints ->
                val ceiling = bounds.heightFor(bounds.maximum, requestedHeight)
                // Fixed mode is content-independent: provider/model expansion and folder-tab
                // changes cannot resize the sheet. requestedHeight changes only through the resize handle.
                val fixedHeight = fixedHeightFraction?.let { fraction ->
                    val initialHeight = (bounds.maximum * fraction.coerceIn(0f, 1f)).roundToInt()
                    bounds.heightFor(initialHeight, requestedHeight)
                }
                // The painted Column must always fill the sheet. Previously the sheet could be taller than
                // the Column (resized via the handle, or content shorter than the minimum height), leaving
                // an unpainted strip below the brush.
                val targetHeight = fixedHeight ?: requestedHeight?.let { bounds.heightFor(0, it) }
                val placeable = measurables.single().measure(
                    constraints.copy(minHeight = targetHeight ?: bounds.minimum, maxHeight = targetHeight ?: ceiling)
                )
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
        }
    }
}

/** Max-effort decoration stays above a profile photo without hiding it. */
internal fun sheetDecorationBrushAlpha(photo: String?, transparency: Float): Float =
    if (!photo.isNullOrBlank() && transparency.coerceIn(0f, 1f) < 1f) 0.32f else 1f

private class HandleCoordinates { var value: LayoutCoordinates? = null }

private data class ResizeDrag(
    val startHeight: Float,
    val startY: Float,
    val startOffsetY: Float = 0f
)

/** The decor height includes system bars even on versions where screenHeightDp excludes them. */
@Composable
private fun rememberSheetWindowHeight(fallback: Int): Int {
    val root = LocalView.current.rootView
    var height by remember(root) { mutableIntStateOf(root.height) }
    DisposableEffect(root) {
        val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            height = (bottom - top).coerceAtLeast(0)
        }
        root.addOnLayoutChangeListener(listener)
        height = root.height
        onDispose { root.removeOnLayoutChangeListener(listener) }
    }
    return height.takeIf { it > 0 } ?: fallback
}

/** Plain holder written while drawing; deliberately not snapshot state, so it never triggers redraws. */
private class SettledBrush { var brush: Brush? = null }

// Dialog navigation-bar color/divider setters have no direct replacement on older Android versions.
@Suppress("DEPRECATION")
@Composable
private fun ConfigureBottomSheetWindow(navigationBarColor: Color) {
    var view: View? = LocalView.current
    var window: Window? = null
    while (view != null && window == null) {
        if (view is DialogWindowProvider) window = view.window else view = view.parent as? View
    }
    DisposableEffect(window, navigationBarColor) {
        val target = window ?: return@DisposableEffect onDispose { }
        val oldNavigationBarColor = target.navigationBarColor
        val oldDividerColor = if (android.os.Build.VERSION.SDK_INT >= 28) target.navigationBarDividerColor else null
        val oldContrast = if (android.os.Build.VERSION.SDK_INT >= 29) target.isNavigationBarContrastEnforced else null
        val controller = WindowCompat.getInsetsController(target, target.decorView)
        val oldLightNavigationBars = controller.isAppearanceLightNavigationBars
        val argb = Color.Transparent.toArgb()

        target.navigationBarColor = argb
        if (android.os.Build.VERSION.SDK_INT >= 28) target.navigationBarDividerColor = argb
        if (android.os.Build.VERSION.SDK_INT >= 29) target.isNavigationBarContrastEnforced = false
        controller.isAppearanceLightNavigationBars = navigationBarColor.luminance() > 0.5f

        onDispose {
            target.navigationBarColor = oldNavigationBarColor
            if (android.os.Build.VERSION.SDK_INT >= 28 && oldDividerColor != null) {
                target.navigationBarDividerColor = oldDividerColor
            }
            if (android.os.Build.VERSION.SDK_INT >= 29 && oldContrast != null) {
                target.isNavigationBarContrastEnforced = oldContrast
            }
            controller.isAppearanceLightNavigationBars = oldLightNavigationBars
        }
    }
}
