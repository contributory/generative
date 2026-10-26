package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponents
import org.intellij.markdown.ast.ASTNode
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade

/**
 * Incremental AST rendering with real Compose measurement. Stable nodes keep their composition slots;
 * only the unstable tail and any post-parser-limit text update. The conversation list owns laziness;
 * this renderer does not invent heights for content that has not been composed.
 */
@Composable
internal fun RenderIncrementalMarkdownDocument(
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    overflowStart: Int?,
    components: MarkdownComponents,
    modifier: Modifier
) {
    val sourceLength = source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    Column(modifier.fillMaxWidth()) {
        stableNodes.forEachIndexed { index, node ->
            key(incrementalBlockSlot("stable", index, node)) {
                StreamingMarkdownSourceBlock(node, source, stable = true, components = components)
            }
        }
        if (overflowStart == null) {
            unstableNodes.forEachIndexed { index, node ->
                key(incrementalBlockSlot("tail", index, node)) {
                    StreamingMarkdownSourceBlock(node, source, stable = false, components = components)
                }
            }
        } else {
            val start = overflowStart.coerceIn(0, sourceLength)
            val overflow = remember(source.latestSegment, source.length, start) {
                source.slice(start, sourceLength)
            }
            if (overflow.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalStreamingTextFade provides false) {
                    OverflowPlainText(overflow)
                }
            }
        }
    }
}

@Composable
private fun StreamingMarkdownSourceBlock(
    node: ASTNode,
    source: StreamTextSnapshot,
    stable: Boolean,
    components: MarkdownComponents
) {
    val sourceLength = source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val startOffset = node.startOffset.coerceIn(0, sourceLength)
    val endOffset = node.endOffset.coerceIn(startOffset, sourceLength)
    val sourceLengthKey = if (stable) 0L else source.length
    val text = remember(node, startOffset, endOffset, sourceLengthKey) {
        source.slice(startOffset, endOffset)
    }
    val shiftedNode = remember(node, startOffset) { ShiftedMarkdownAstNode(node, startOffset) }
    val fingerprint = remember(text) { markdownContentFingerprint(text) }
    CompositionLocalProvider(
        LocalStreamingTextFade provides !stable,
        LocalMarkdownContentFingerprint provides fingerprint
    ) {
        MarkdownElement(
            node = shiftedNode,
            components = components,
            content = text,
            includeSpacer = true
        )
    }
}

internal fun incrementalBlockSlot(kind: String, index: Int, node: ASTNode): String =
    // Do not include endOffset: the unstable tail grows on every token and must keep its composition slot.
    "incremental-$kind:$index:${node.type.name}:${node.startOffset}"
