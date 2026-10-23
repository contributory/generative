package org.starfall.generative.home.chat.ui.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
internal fun RenderLatexBlock(source: String, formula: String) {
    val context = LocalContext.current
    val labelColor = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = prominentContainerColor(),
        border = prominentContainerBorder(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("∑", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = labelColor))
                    Spacer(Modifier.width(6.dp))
                    Text("LaTeX Equation", style = MaterialTheme.typography.labelMedium, color = labelColor)
                }
                 IconButton(onClick = {
                     val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                     clipboard.setPrimaryClip(ClipData.newPlainText("LaTeX Formula", source))
                     Toast.makeText(context, "Formula copied", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(24.dp)) {
                    FluentIcon(Icons.Outlined.ContentCopy, "Copy Formula", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            LatexFormulaRenderer(
                formula = formula,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                displayMode = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
