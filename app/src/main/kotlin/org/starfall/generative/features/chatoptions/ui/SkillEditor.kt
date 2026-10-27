package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillEditor(
    skill: AgentSkill?,
    onBack: (AgentSkill?) -> Unit
) {
    val skillId = skill?.id.orEmpty()
    var name by rememberSaveable(skillId) { mutableStateOf(skill?.name.orEmpty()) }
    var content by rememberSaveable(skillId) { mutableStateOf(skill?.content.orEmpty()) }

    fun draft(): AgentSkill = AgentSkill(
        id = skill?.id.orEmpty(),
        name = name.trim(),
        content = content.trim()
    )

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = { onBack(draft()) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .testTag("agent_skill_name_input"),
                        decorationBox = { innerTextField ->
                            if (name.isBlank()) {
                                Text(
                                    stringResource(R.string.common_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack(draft()) }) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = name.isNotBlank() && content.isNotBlank(),
                        onClick = { onBack(draft()) }
                    ) {
                        FluentIcon(Icons.Outlined.Save, stringResource(R.string.agent_skill_save))
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { padding ->
        SelectableOutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.agent_skill_content)) },
            placeholder = { Text(stringResource(R.string.agent_skill_content_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("agent_skill_content_input"),
            maxLines = Int.MAX_VALUE,
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}
