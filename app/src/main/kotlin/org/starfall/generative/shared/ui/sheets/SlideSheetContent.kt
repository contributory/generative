package org.starfall.generative.shared.ui.sheets

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

/**
 * Slides horizontally between a base sheet ([page] == null) and linked detail sheets while
 * smoothly expanding or contracting the sheet height to match the active content.
 */
@Composable
fun <T : Any> SlideSheetContent(
    page: T?,
    onBack: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (T?) -> Unit,
) {
    val parentActive = LocalScreenTransitionActive.current
    val basePageState = rememberSaveableStateHolder()
    val minContentHeight = LocalBottomSheetMinContentHeight.current
    val currentOnBack by rememberUpdatedState(onBack)
    val view = LocalView.current

    BackHandler(enabled = parentActive && page != null) {
        currentOnBack()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && parentActive && page != null) {
        DisposableEffect(view) {
            Api33SheetBack.register(view) { currentOnBack() }
        }
    }

    AnimatedContent(
        targetState = page,
        contentKey = { it },
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            val initial = initialState
            val target = targetState
            val transitioning = initial != target
            val direction = when {
                target == null -> AnimatedContentTransitionScope.SlideDirection.Right
                initial == null -> AnimatedContentTransitionScope.SlideDirection.Left
                target is Enum<*> && initial is Enum<*> && target.ordinal < initial.ordinal ->
                    AnimatedContentTransitionScope.SlideDirection.Right
                else -> AnimatedContentTransitionScope.SlideDirection.Left
            }
            (slideIntoContainer(direction, tween(300)) togetherWith
                slideOutOfContainer(direction, tween(300))) using
                SizeTransform(clip = transitioning) { _, _ ->
                    if (transitioning) tween(300) else snap()
                }
        },
        modifier = modifier.fillMaxWidth(),
        label = label,
    ) { targetPage ->
        CompositionLocalProvider(
            LocalScreenTransitionActive provides (parentActive && targetPage == page)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minContentHeight),
                contentAlignment = Alignment.TopStart
            ) {
                if (targetPage == null) {
                    basePageState.SaveableStateProvider("base") { content(null) }
                } else {
                    content(targetPage)
                }
            }
        }
    }
}

/**
 * Material's ModalBottomSheetDialogLayout registers its own WindowOnBackInvokedCallback on API 33+,
 * which sits above ComponentDialog.onBackPressedDispatcher. Registering after attachment ensures
 * system Back pops the sub-sheet before dismissing the modal sheet.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33SheetBack {
    fun register(view: View, onBack: () -> Unit): DisposableEffectResult {
        var registeredDispatcher: OnBackInvokedDispatcher? = null
        val callback = OnBackInvokedCallback { onBack() }
        fun attach() {
            if (registeredDispatcher != null) return
            registeredDispatcher = view.findOnBackInvokedDispatcher()?.also {
                it.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
                )
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = attach()
            override fun onViewDetachedFromWindow(v: View) {
                registeredDispatcher?.unregisterOnBackInvokedCallback(callback)
                registeredDispatcher = null
            }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) attach()
        return object : DisposableEffectResult {
            override fun dispose() {
                view.removeOnAttachStateChangeListener(listener)
                registeredDispatcher?.unregisterOnBackInvokedCallback(callback)
                registeredDispatcher = null
            }
        }
    }
}
