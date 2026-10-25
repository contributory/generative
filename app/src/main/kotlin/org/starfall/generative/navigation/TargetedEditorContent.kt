package org.starfall.generative.navigation

import org.starfall.generative.shared.ui.GenerativeLoadingIndicator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

/** Only unsaved drafts bypass loading. Existing metadata must go through [load]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> TargetedEditorContent(
    recordId: String,
    title: String,
    loadingMessage: String,
    onBack: () -> Unit,
    load: suspend () -> T,
    draft: T? = null,
    content: @Composable (T) -> Unit,
) {
    val isDraft = draft != null
    var record by remember(recordId, isDraft) { mutableStateOf(draft) }
    var failure by remember(recordId, isDraft) { mutableStateOf<String?>(null) }
    var attempt by remember(recordId, isDraft) { mutableIntStateOf(0) }
    val loadFailureFallback = stringResource(R.string.editor_load_failed_fallback)
    LaunchedEffect(recordId, isDraft, attempt) {
        if (isDraft) return@LaunchedEffect
        record = null
        failure = null
        try {
            record = load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message?.takeIf { it.isNotBlank() }
                ?: loadFailureFallback
        }
    }

    val loaded = record
    if (loaded != null) {
        content(loaded)
        return
    }

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            val error = failure
            if (error == null) {
                GenerativeLoadingIndicator()
                Text(loadingMessage, textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.editor_open_failed), style = MaterialTheme.typography.titleMedium)
                Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.editor_load_retry_hint), textAlign = TextAlign.Center)
                Button(onClick = { failure = null; attempt++ }) { Text(stringResource(R.string.common_retry)) }
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        }
    }
}
