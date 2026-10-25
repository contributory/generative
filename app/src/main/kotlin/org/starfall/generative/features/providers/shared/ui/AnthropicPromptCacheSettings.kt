package org.starfall.generative.features.providers.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.AnthropicCacheTtl
import org.starfall.generative.shared.model.provider.AnthropicPromptCache

/** Prompt-cache switch plus the breakpoints and lifetime it applies; shown for Anthropic Messages providers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropicPromptCacheSettings(value: AnthropicPromptCache, onChange: (AnthropicPromptCache) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.provider_prompt_cache), Modifier.weight(1f).padding(end = 12.dp))
            Switch(value.enabled, onCheckedChange = { onChange(value.copy(enabled = it)) }, modifier = Modifier.testTag("prompt-cache-enabled"))
        }
        if (!value.enabled) return@Column
        Text(stringResource(R.string.provider_prompt_cache_ttl), style = MaterialTheme.typography.titleSmall)
        val ttls = AnthropicCacheTtl.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("prompt-cache-ttl")) {
            ttls.forEachIndexed { index, ttl ->
                SegmentedButton(
                    selected = value.ttl == ttl,
                    onClick = { onChange(value.copy(ttl = ttl)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ttls.size)
                ) {
                    Text(stringResource(when (ttl) {
                        AnthropicCacheTtl.FIVE_MINUTES -> R.string.provider_prompt_cache_ttl_5m
                        AnthropicCacheTtl.ONE_HOUR -> R.string.provider_prompt_cache_ttl_1h
                    }))
                }
            }
        }
        PromptCachePartRow(R.string.provider_prompt_cache_system, value.cacheSystem) { onChange(value.copy(cacheSystem = it)) }
        PromptCachePartRow(R.string.provider_prompt_cache_tools, value.cacheTools) { onChange(value.copy(cacheTools = it)) }
        PromptCachePartRow(R.string.provider_prompt_cache_conversation, value.cacheConversation) { onChange(value.copy(cacheConversation = it)) }
        Text(
            stringResource(R.string.provider_prompt_cache_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PromptCachePartRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}
