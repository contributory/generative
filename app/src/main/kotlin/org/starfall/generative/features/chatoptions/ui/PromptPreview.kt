package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptPreview(
    prompt: SavedPrompt,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        prompt.name.ifBlank { stringResource(R.string.prompt_preview_title) },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        FluentIcon(
                            Icons.Outlined.Edit,
                            stringResource(R.string.prompt_preview_edit)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.prompt_preview_description),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                PromptPreviewMessage(prompt.role, prompt.content)
            }
        }
    }
}

@Composable
private fun PromptPreviewMessage(role: ChatRole, content: String) {
    val isUser = role == ChatRole.USER
    val bubbleColor = when (role) {
        ChatRole.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
        ChatRole.USER -> MaterialTheme.colorScheme.primaryContainer
        ChatRole.MODEL -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (role) {
        ChatRole.SYSTEM -> MaterialTheme.colorScheme.onTertiaryContainer
        ChatRole.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        ChatRole.MODEL -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            promptRoleLabel(role),
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (role == ChatRole.MODEL) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(if (role == ChatRole.SYSTEM) 1f else 0.86f),
                shape = MaterialTheme.shapes.large,
                color = bubbleColor
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (content.isBlank()) {
                        Text(
                            stringResource(R.string.prompt_preview_empty),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                content,
                                color = contentColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
