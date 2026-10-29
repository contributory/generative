package org.starfall.generative.shared.ui.sheets

import android.view.View
import android.view.Window
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.hypot
import org.starfall.generative.shared.ui.background.LocalProfileModalImages
import org.starfall.generative.shared.ui.background.ProfileBackground
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.theme.modalScrimColor
import org.starfall.generative.shared.ui.layout.amoledSheetBorder
import org.starfall.generative.shared.ui.layout.modalContainerColor
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Material 3 owns drag anchors, half/full expansion, nested scrolling, flings and
 * dismiss gestures. Only app-specific styling, height caps and system bars live here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    containerBrush: Brush? = null,
    containerOverlay: (DrawScope.() -> Unit)? = null,
    /** Screen-space origin for the selected-model circular reveal. */
    containerBrushRevealOrigin: (() -> Offset?)? = null,
    containerBrushRevealProgress: (() -> Float)? = null,
    containerBrushRetractionProgress: (() -> Float?)? = null,
    navigationBarColor: Color? = null,
    /** Linked sheet pages handle Back in Compose; Material handles it for a root sheet. */
    dismissOnBack: Boolean = true,
    contentBehindNavigationBar: Boolean = false,
    /** Used by media pickers/editors that intentionally occupy a fixed screen fraction. */
    fixedHeightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Two visible Material 3 stops: PartiallyExpanded and Expanded.
    // Hidden is the normal dismiss state, not a third open height.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    )
    // Give Material a full-height surface so both anchors exist even for short content.
    // The top inset keeps Expanded immediately below (never behind) the status bar.
    val amoled = LocalAmoledMode.current
    val sheetModifier = modifier.fillMaxHeight().windowInsetsPadding(
        WindowInsets.statusBars.only(WindowInsetsSides.Top)
    ).then(
        if (amoled) Modifier.amoledSheetBorder(
            sheetState, shape, MaterialTheme.colorScheme.outlineVariant, 1.dp
        ) else Modifier
    )
    val sheetImage = LocalProfileModalImages.current.sheet
    val sheetTransparency = LocalProfileModalImages.current.sheetTransparency
    val settledBrush = remember { SettledBrush() }
    var containerOriginOnScreen by remember { mutableStateOf(Offset.Zero) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissOnBack),
        modifier = sheetModifier.testTag("app-bottom-sheet"),
        shape = shape,
        containerColor = modalContainerColor(containerColor),
        scrimColor = modalScrimColor(BottomSheetDefaults.ScrimColor),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().height(48.dp).testTag("bottom-sheet-drag-handle"),
                contentAlignment = Alignment.Center
            ) { BottomSheetDefaults.DragHandle() }
        },
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        }
    ) {
        ConfigureBottomSheetWindow(navigationBarColor ?: containerColor)
        Box(
            Modifier.fillMaxWidth()
                .then(if (fixedHeightFraction != null)
                    Modifier.fillMaxHeight(fixedHeightFraction.coerceIn(0f, 1f))
                else Modifier)
                .testTag("bottom-sheet-window")
        ) {
            ProfileBackground(sheetImage, Modifier.matchParentSize(), sheetTransparency)
            Box(Modifier.matchParentSize().onGloballyPositioned {
                containerOriginOnScreen = it.localToScreen(Offset.Zero)
            }.drawBehind {
                if (containerBrush == null) {
                    settledBrush.brush = null
                    return@drawBehind
                }
                val brushAlpha = sheetDecorationBrushAlpha(sheetImage, sheetTransparency)
                fun drawTarget() {
                    drawRect(containerBrush, alpha = brushAlpha)
                    containerOverlay?.invoke(this)
                }
                val localOrigin = containerBrushRevealOrigin?.invoke()?.let {
                    it - containerOriginOnScreen
                }
                val progress = (containerBrushRevealProgress?.invoke() ?: 1f).coerceIn(0f, 1f)
                val retraction = containerBrushRetractionProgress?.invoke()
                if (localOrigin != null && retraction != null) {
                    settledBrush.brush = null
                    val radius = hypot(size.width, size.height) * retraction.coerceIn(0f, 1f)
                    clipPath(Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(localOrigin, radius))
                    }) { drawTarget() }
                } else if (localOrigin == null || progress >= 0.52f) {
                    drawTarget()
                    settledBrush.brush = containerBrush
                } else {
                    settledBrush.brush?.takeIf { it != containerBrush }?.let {
                        drawRect(it, alpha = brushAlpha)
                    }
                    val radius = 14.dp.toPx() + hypot(size.width, size.height) *
                        (progress / 0.52f).coerceIn(0f, 1f)
                    clipPath(Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(localOrigin, radius))
                    }) { drawTarget() }
                }
            })
            Column(
                Modifier.fillMaxWidth().then(
                    if (contentBehindNavigationBar) Modifier
                    else Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    )
                ),
                content = content
            )
        }
    }
}

/** Max-effort decoration stays above a profile photo without hiding it. */
internal fun sheetDecorationBrushAlpha(photo: String?, transparency: Float): Float =
    if (!photo.isNullOrBlank() && transparency.coerceIn(0f, 1f) < 1f) 0.32f else 1f

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
