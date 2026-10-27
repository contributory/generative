package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.model.agentSkillDocument
import org.starfall.generative.shared.model.parseAgentSkillDocument
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Edit and exchange one complete SKILL.md, not three disconnected form fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillEditor(
    skill: AgentSkill?,
    onBack: (AgentSkill?) -> Unit
) {
    val key = skill?.id.orEmpty()
    val starter = AgentSkill(
        id = "",
        name = stringResource(R.string.agent_skill_new_name),
        description = stringResource(R.string.agent_skill_description_template),
        content = stringResource(R.string.agent_skill_template)
    )
    var document by rememberSaveable(key) { mutableStateOf(agentSkillDocument(skill ?: starter)) }
    val parsed = parseAgentSkillDocument(document, preserveEmptyExtras = true)

    fun draft(): AgentSkill? = parsed?.copy(id = skill?.id.orEmpty())

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = { onBack(draft()) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        parsed?.name ?: stringResource(R.string.agent_skill_details),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack(draft()) }) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(enabled = parsed != null, onClick = { onBack(draft()) }) {
                        FluentIcon(Icons.Outlined.Save, stringResource(R.string.agent_skill_save))
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { padding ->
        SelectableOutlinedTextField(
            value = document,
            onValueChange = { document = it },
            label = { Text("SKILL.md") },
            supportingText = if (parsed == null) {
                { Text(stringResource(R.string.agent_skill_invalid_document)) }
            } else null,
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("agent_skill_document_input"),
            maxLines = Int.MAX_VALUE,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        )
    }
}
