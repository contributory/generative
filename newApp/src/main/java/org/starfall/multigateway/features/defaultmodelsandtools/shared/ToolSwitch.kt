package org.starfall.multigateway.features.defaultmodelsandtools.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun ToolSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min=48.dp).alpha(if (enabled) 1f else 0.38f),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Text(label,Modifier.weight(1f).padding(end=8.dp))
        Switch(checked=checked,onCheckedChange=onChange,enabled=enabled)
    }
}
