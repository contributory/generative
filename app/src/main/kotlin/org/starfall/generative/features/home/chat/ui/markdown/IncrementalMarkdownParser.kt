package org.starfall.generative.features.home.chat.ui.markdown

import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.intellij.markdown.parser.StreamingMarkdownFile
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class IncrementalMarkdownMetricsSnapshot(
    val parserAppendCount: Long,
    val parserInputChars: Long,
    val unstableTailCharsReprocessed: Long,
    val maxUnstableTailChars: Int,
    val boundedTailFallbackCount: Long
)

/** Content-free counters estimate the active parser's work without retaining source or AST data. */
internal object IncrementalMarkdownMetrics {
    private val parserAppendCount = AtomicLong()
    private val parserInputChars = AtomicLong()
    private val unstableTailCharsReprocessed = AtomicLong()
    private val maxUnstableTailChars = AtomicInteger()
    private val boundedTailFallbackCount = AtomicLong()

    fun recordAppend(inputChars: Int, estimatedParserChars: Int, tailChars: Int) {
        parserAppendCount.incrementAndGet()
        parserInputChars.addAndGet(inputChars.toLong())
        unstableTailCharsReprocessed.addAndGet(estimatedParserChars.toLong())
        maxUnstableTailChars.updateAndGet { maxOf(it, tailChars) }
    }

    fun recordFallback() { boundedTailFallbackCount.incrementAndGet() }

    fun metrics() = IncrementalMarkdownMetricsSnapshot(
        parserAppendCount.get(),
        parserInputChars.get(),
        unstableTailCharsReprocessed.get(),
        maxUnstableTailChars.get(),
        boundedTailFallbackCount.get()
    )

    internal fun resetForTests() {
        parserAppendCount.set(0L)
        parserInputChars.set(0L)
        unstableTailCharsReprocessed.set(0L)
        maxUnstableTailChars.set(0)
        boundedTailFallbackCount.set(0L)
    }
}

/** An immutable render snapshot; stable nodes share structure across appends. */
internal data class IncrementalMarkdownSnapshot(
    val stableNodes: PersistentList<ASTNode> = persistentListOf(),
    val unstableNodes: List<ASTNode> = emptyList(),
    val parsedLength: Int = 0,
    val overflowStart: Int? = null,
    val revision: Long = 0L
)

internal data class IncrementalMarkdownAppendResult(
    val snapshot: IncrementalMarkdownSnapshot,
    val consumedChars: Int,
    val linkUpdates: List<Pair<String, String?>>
)

/** Connects parser cancellation to the keyed Compose effect that owns this generation. */
internal class MarkdownParserCancellationToken : CancellationToken {
    @Volatile
    var ownerJob: Job? = null

    override fun checkCancelled() {
        ownerJob?.ensureActive()
    }
}

/**
 * App-side wrapper around JetBrains Markdown's append-only parser. Stable AST rows are copied only when
 * first finalized; the parser's unstable tail is deliberately capped by the caller to bound reparsing.
 */
internal class IncrementalMarkdownParser(
    private val cancellationToken: CancellationToken
) {
    val file: StreamingMarkdownFile = EmptyStreamingMarkdownFile(GFMFlavourDescriptor(), cancellationToken)
    val referenceLinkHandler = ReferenceLinkHandlerImpl()

    private var stableNodes: PersistentList<ASTNode> = persistentListOf()
    private var stableNodeCount = 0
    private var unstableNodes: List<ASTNode> = emptyList()
    private var overflowStart: Int? = null
    private var revision = 0L

    fun append(
        text: String,
        source: StreamTextSnapshot,
        maxUnstableTailChars: Int,
        maxParserAppendChars: Int
    ): IncrementalMarkdownAppendResult {
        require(maxUnstableTailChars > 0)
        require(maxParserAppendChars > 0)
        cancellationToken.checkCancelled()
        var consumed = 0
        val linkUpdates = LinkedHashMap<String, String?>()

        while (consumed < text.length && overflowStart == null) {
            cancellationToken.checkCancelled()
            val currentTailStart = unstableNodes.firstOrNull()?.startOffset ?: file.endOffset
            val currentTailLength = (file.endOffset - currentTailStart).coerceAtLeast(0)
            val remainingTailBudget = maxUnstableTailChars - currentTailLength
            if (remainingTailBudget <= 0) {
                overflowStart = currentTailStart
                IncrementalMarkdownMetrics.recordFallback()
                break
            }

            val appendLimit = minOf(maxParserAppendChars, remainingTailBudget, text.length - consumed)
            val end = safeAppendEnd(text, consumed, appendLimit)
            if (end <= consumed) {
                // Do not split a surrogate pair merely to fill the last unit of the parser budget.
                overflowStart = currentTailStart
                IncrementalMarkdownMetrics.recordFallback()
                break
            }
            val chunk = text.substring(consumed, end)
            val estimatedParserChars = currentTailLength + chunk.length
            file.append(chunk)
            consumed = end

            val newStable = ArrayList<ASTNode>()
            val allStable = file.stableChildren
            while (stableNodeCount < allStable.size) {
                newStable += allStable[stableNodeCount]
                stableNodeCount++
            }
            if (newStable.isNotEmpty()) {
                stableNodes = stableNodes.addingAll(newStable)
                newStable.forEach { collectLinkUpdates(it, source, linkUpdates) }
            }
            unstableNodes = file.unstableTail.toList()
            unstableNodes.forEach { collectLinkUpdates(it, source, linkUpdates) }

            val tailStart = unstableNodes.firstOrNull()?.startOffset ?: file.endOffset
            val tailLength = (file.endOffset - tailStart).coerceAtLeast(0)
            IncrementalMarkdownMetrics.recordAppend(chunk.length, estimatedParserChars, tailLength)
            if (tailLength >= maxUnstableTailChars) {
                overflowStart = tailStart
                IncrementalMarkdownMetrics.recordFallback()
            }
        }

        revision++
        return IncrementalMarkdownAppendResult(
            snapshot = IncrementalMarkdownSnapshot(
                stableNodes = stableNodes,
                unstableNodes = unstableNodes,
                parsedLength = file.endOffset,
                overflowStart = overflowStart,
                revision = revision
            ),
            consumedChars = consumed,
            linkUpdates = linkUpdates.entries.map { it.key to it.value }
        )
    }

    fun currentSnapshot(): IncrementalMarkdownSnapshot = IncrementalMarkdownSnapshot(
        stableNodes = stableNodes,
        unstableNodes = unstableNodes,
        parsedLength = file.endOffset,
        overflowStart = overflowStart,
        revision = revision
    )

    private fun safeAppendEnd(text: String, start: Int, limit: Int): Int {
        var end = (start + limit).coerceAtMost(text.length)
        if (end < text.length && end > start && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
            end--
        }
        return end
    }

    private fun collectLinkUpdates(
        root: ASTNode,
        source: StreamTextSnapshot,
        updates: MutableMap<String, String?>
    ) {
        fun unescaped(node: ASTNode): String {
            val start = node.startOffset.coerceIn(0, source.length.toInt())
            val end = node.endOffset.coerceIn(start, source.length.toInt())
            val text = source.slice(start, end)
            return ShiftedMarkdownAstNode(node, start).getUnescapedTextInNode(text)
        }

        fun visit(node: ASTNode) {
            var linkOnly = false
            val label = when (node.type) {
                MarkdownElementTypes.LINK_DEFINITION -> node.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.let(::unescaped)
                MarkdownElementTypes.INLINE_LINK -> node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.let(::unescaped)
                MarkdownElementTypes.AUTOLINK -> {
                    linkOnly = true
                    unescaped(node.children.firstOrNull { it.type.name == MarkdownElementTypes.AUTOLINK.name } ?: node)
                }
                else -> null
            }
            if (label != null) {
                val destination = if (linkOnly) label else {
                    node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.let(::unescaped)
                }
                updates[label] = destination
            }
            node.children.forEach(::visit)
        }
        visit(root)
    }
}

/** Shift a Markdown subtree's source offsets into a per-block source string. */
internal class ShiftedMarkdownAstNode(
    private val source: ASTNode,
    private val offset: Int,
    private val parentNode: ASTNode? = null
) : ASTNode {
    override val type get() = source.type
    override val startOffset: Int get() = (source.startOffset - offset).coerceAtLeast(0)
    override val endOffset: Int get() = (source.endOffset - offset).coerceAtLeast(startOffset)
    override val parent: ASTNode? get() = parentNode
    override val children: List<ASTNode> by lazy(LazyThreadSafetyMode.NONE) {
        source.children.map { ShiftedMarkdownAstNode(it, offset, this) }
    }
}
