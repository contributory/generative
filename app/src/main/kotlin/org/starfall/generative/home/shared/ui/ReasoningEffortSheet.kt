package org.starfall.generative.home.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.DEFAULT_REASONING_EFFORTS
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.model.ReasoningEffortControl
import org.starfall.generative.shared.ui.model.reasoningEffortOptions
import org.starfall.generative.shared.ui.model.reasoningEffortIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningEffortSheet(
    currentEffort: String?,
    onApply: (String?) -> Unit,
    onDismiss: () -> Unit,
    providerType: ModelApiType? = null,
    modelId: String = "",
    maxTokens: Int = Int.MAX_VALUE,
    supportedEfforts: List<String> = DEFAULT_REASONING_EFFORTS
) {
    var effort by remember(currentEffort) { mutableStateOf(currentEffort) }
    val options = remember(supportedEfforts) {
        reasoningEffortOptions(supportedEfforts)
    }

    AppSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reasoning Effort", style = MaterialTheme.typography.titleLarge)
            Text(
                "Controls the reasoning effort for this conversation. This overrides the model setting only for this chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                options[reasoningEffortIndex(effort, options)].label,
                style = MaterialTheme.typography.headlineSmall
            )
            ReasoningEffortControl(effort = effort, onEffortChange = { effort = it }, options = options)
            Button(
                onClick = {
                    onApply(effort)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }
        }
    }
}
