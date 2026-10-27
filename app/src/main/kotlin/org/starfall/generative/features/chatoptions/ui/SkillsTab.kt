package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.model.DEFAULT_AGENT_SKILL_ID
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.layout.groupedItemShape
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.menus.ItemContextMenu

@Composable
internal fun SkillsTab(
    skills: List<AgentSkill>,
    onEditSkill: (AgentSkill) -> Unit,
    onDeleteSkill: (AgentSkill) -> Unit,
    onExportSkill: (AgentSkill) -> Unit = {}
) {
    var skillPendingDelete by remember { mutableStateOf<AgentSkill?>(null) }
    var viewingBundledSkill by remember { mutableStateOf<AgentSkill?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (skills.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.agent_skills_empty),
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    skills.forEachIndexed { index, skill ->
                        val bundled = skill.id == DEFAULT_AGENT_SKILL_ID
                        Surface(
                            onClick = {
                                if (bundled) viewingBundledSkill = skill else onEditSkill(skill)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = groupedItemShape(index, skills.size),
                            color = prominentContainerColor(),
                            border = prominentContainerBorder()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        skill.description.ifBlank {
                                            stringResource(
                                                R.string.agent_skill_metadata,
                                                skill.content.length,
                                                estimateSkillTokens(skill.content)
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        skill.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                ItemContextMenu(
                                    onEdit = { onEditSkill(skill) },
                                    onDelete = { skillPendingDelete = skill },
                                    deleteColor = MaterialTheme.colorScheme.error,
                                    firstActionText = stringResource(R.string.agent_skill_export),
                                    firstActionImageVector = org.starfall.generative.shared.ui.icons.Icons.Outlined.Download,
                                    onFirstAction = { onExportSkill(skill) },
                                    showEdit = !bundled,
                                    showDelete = !bundled
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewingBundledSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewingBundledSkill = null },
            title = { Text(skill.name) },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                    Text(skill.content, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingBundledSkill = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    skillPendingDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { skillPendingDelete = null },
            text = { Text(stringResource(R.string.agent_skill_delete_confirmation, skill.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSkill(skill)
                    skillPendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { skillPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun estimateSkillTokens(content: String): Int =
    if (content.isBlank()) 0 else ((content.length + 3) / 4).coerceAtLeast(1)
