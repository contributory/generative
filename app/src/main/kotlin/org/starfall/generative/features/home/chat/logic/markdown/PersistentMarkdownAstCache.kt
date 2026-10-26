package org.starfall.generative.features.home.chat.logic.markdown

import android.content.Context
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.ASTNodeBuilder
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Logical identity for a single message/block; message content and parser schema form the version key. */
data class MarkdownRenderCacheIdentity(
    val conversationId: String,
    val messageId: String,
    val blockId: String = "message"
) {
    fun forBlock(id: String): MarkdownRenderCacheIdentity = copy(blockId = id)
}

/** Cache counters deliberately contain no message text or identifiers. */
internal data class MarkdownAstCacheEpoch internal constructor(
    val global: Long,
    val conversation: Long,
    val message: Long
)

internal data class MarkdownAstCacheMetrics(
    val hits: Long,
    val misses: Long,
    val bytesRead: Long,
    val bytesWritten: Long,
    val evictions: Long,
    val corruptEntries: Long,
    val serializationNanos: Long,
    val deserializationNanos: Long
)

/**
 * A bounded, plaintext cache of immutable JetBrains Markdown AST records. The source text is never
 * serialized: AST offsets are resolved against canonical message content supplied by the caller.
 * Entries live under noBackupFilesDir, are independent per message/block/content hash, and are always
 * safe to discard because conversation storage remains the source of truth.
 */
internal class PersistentMarkdownAstCache internal constructor(
    private val root: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    private data class TypeKey(val name: String, val token: Boolean)
    private data class PendingNode(val index: Int, val node: ASTNode)
    private data class MutableAstNodeRecord(
        val typeName: String,
        val token: Boolean,
        val startOffset: Int,
        val endOffset: Int,
        val composite: Boolean,
        var children: List<Int> = emptyList()
    )

    @Serializable
    private data class AstNodeRecord(
        val typeName: String,
        val token: Boolean,
        val startOffset: Int,
        val endOffset: Int,
        val composite: Boolean,
        val children: List<Int>
    )

    @Serializable
    private data class DiskEntry(
        val schemaVersion: Int,
        val parserVersion: Int,
        val contentHash: String,
        val root: Int,
        val nodes: List<AstNodeRecord>,
        val links: Map<String, String?>
    )

    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { encodeDefaults = true }
    private val globalEpoch = AtomicLong(0L)
    private val conversationEpochs = HashMap<String, Long>()
    private val messageEpochs = HashMap<String, Long>()
    private val memoryInvalidators = LinkedHashMap<Any, (String?, String?) -> Unit>()
    private val invalidatedConversations = HashSet<String>()
    private val invalidatedMessages = HashSet<String>()
    private val hitCount = AtomicLong()
    private val missCount = AtomicLong()
    @Volatile internal var beforeCommitForTest: suspend () -> Unit = {}
    private val bytesReadCount = AtomicLong()
    private val bytesWrittenCount = AtomicLong()
    private val evictionCount = AtomicLong()
    private val corruptCount = AtomicLong()
    private val serializeNanos = AtomicLong()
    private val deserializeNanos = AtomicLong()

    init {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        // Old scratch files are never valid entries. Remove them without blocking construction/UI.
        ioScope.launch { cleanupStaleTemporaryFiles() }
    }

    fun registerMemoryInvalidator(owner: Any, invalidate: (conversationId: String?, messageId: String?) -> Unit) {
        synchronized(lock) { memoryInvalidators[owner] = invalidate }
    }

    fun captureEpoch(identity: MarkdownRenderCacheIdentity): MarkdownAstCacheEpoch = synchronized(lock) {
        val conversationKey = conversationKey(identity.conversationId)
        val messageKey = messageKey(identity)
        MarkdownAstCacheEpoch(
            global = globalEpoch.get(),
            conversation = conversationEpochs[conversationKey] ?: 0L,
            message = messageEpochs[messageKey] ?: 0L
        )
    }

    suspend fun load(
        identity: MarkdownRenderCacheIdentity,
        content: String,
        capturedEpoch: MarkdownAstCacheEpoch
    ): State.Success? = withContext(Dispatchers.IO) {
        val epoch = capturedEpoch
        if (content.length > MAX_PERSISTED_SOURCE_CHARS || !isCurrent(identity, epoch)) {
            missCount.incrementAndGet()
            return@withContext null
        }
        val file = entryFile(identity, content)
        if (!file.isFile || file.length() > MAX_ENTRY_BYTES) {
            missCount.incrementAndGet()
            if (file.exists() && file.length() > MAX_ENTRY_BYTES) {
                corruptCount.incrementAndGet()
                file.delete()
            }
            return@withContext null
        }

        val startedAt = System.nanoTime()
        try {
            val bytes = file.readBytes()
            bytesReadCount.addAndGet(bytes.size.toLong())
            val entry = json.decodeFromString<DiskEntry>(String(bytes, StandardCharsets.UTF_8))
            if (entry.schemaVersion != SCHEMA_VERSION || entry.parserVersion != PARSER_VERSION ||
                entry.contentHash != sha256(content) || entry.root !in entry.nodes.indices ||
                !isCurrent(identity, epoch)
            ) {
                missCount.incrementAndGet()
                return@withContext null
            }
            val ast = reconstruct(entry, content) ?: run {
                missCount.incrementAndGet()
                return@withContext null
            }
            if (!isCurrent(identity, epoch)) {
                missCount.incrementAndGet()
                return@withContext null
            }
            val handler = ReferenceLinkHandlerImpl().also { referenceHandler ->
                entry.links.forEach { (label, destination) -> referenceHandler.store(label, destination) }
            }
            file.setLastModified(System.currentTimeMillis())
            hitCount.incrementAndGet()
            State.Success(ast, content, linksLookedUp = true, referenceLinkHandler = handler)
        } catch (_: Exception) {
            corruptCount.incrementAndGet()
            missCount.incrementAndGet()
            // Corrupt/stale cache data is a miss. Canonical message text is unaffected.
            file.delete()
            null
        } finally {
            deserializeNanos.addAndGet(System.nanoTime() - startedAt)
        }
    }

    suspend fun store(
        identity: MarkdownRenderCacheIdentity,
        content: String,
        state: State.Success,
        capturedEpoch: MarkdownAstCacheEpoch
    ): Boolean = withContext(Dispatchers.IO) {
        if (content.length > MAX_PERSISTED_SOURCE_CHARS || state.content != content ||
            !isCurrent(identity, capturedEpoch)
        ) return@withContext false
        val tree = flatten(state.node) ?: return@withContext false
        val entry = DiskEntry(
            schemaVersion = SCHEMA_VERSION,
            parserVersion = PARSER_VERSION,
            contentHash = sha256(content),
            root = 0,
            nodes = tree,
            links = collectLinks(state.node, content)
        )
        persistSerializedEntry(identity, content, capturedEpoch, serialize(entry))
    }

    private fun serialize(entry: DiskEntry): ByteArray {
        val startedAt = System.nanoTime()
        return try {
            json.encodeToString(entry).toByteArray(StandardCharsets.UTF_8)
        } finally {
            serializeNanos.addAndGet(System.nanoTime() - startedAt)
        }
    }

    private suspend fun persistSerializedEntry(
        identity: MarkdownRenderCacheIdentity,
        content: String,
        epoch: MarkdownAstCacheEpoch,
        serialized: ByteArray
    ): Boolean {
        if (serialized.size.toLong() > MAX_ENTRY_BYTES || !isCurrent(identity, epoch)) return false
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) return false

        val destination = entryFile(identity, content)
        val temporary = File(root, "${destination.name}.${java.util.UUID.randomUUID()}.tmp")
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(serialized)
                output.fd.sync()
            }
            beforeCommitForTest()
            commitTemporaryEntry(identity, epoch, destination, temporary, serialized.size)
        } catch (_: IOException) {
            false
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun commitTemporaryEntry(
        identity: MarkdownRenderCacheIdentity,
        epoch: MarkdownAstCacheEpoch,
        destination: File,
        temporary: File,
        byteCount: Int
    ): Boolean = synchronized(lock) {
        if (!isCurrentLocked(identity, epoch)) return@synchronized false
        if (destination.isFile) {
            temporary.delete()
            destination.setLastModified(System.currentTimeMillis())
            return@synchronized true
        }
        if (!temporary.renameTo(destination)) return@synchronized false
        bytesWrittenCount.addAndGet(byteCount.toLong())
        true
    }.also { committed ->
        if (committed) evictToBudget()
    }

    /** In-memory tombstone is installed synchronously before asynchronous physical cleanup starts. */
    fun invalidateMessage(conversationId: String, messageId: String) {
        val conversationKey = conversationKey(conversationId)
        val messageKey = messageKey(conversationKey, messageId)
        synchronized(lock) {
            messageEpochs[messageKey] = (messageEpochs[messageKey] ?: 0L) + 1L
            invalidatedMessages += messageKey
        }
        notifyMemoryInvalidators(conversationId, messageId)
        scheduleDeletion("$conversationKey-${sha256(messageId).take(HASH_PREFIX_CHARS)}-")
    }

    /** Marks the entire conversation unreachable before scheduling removal of its message entries. */
    fun invalidateConversation(conversationId: String) {
        val key = conversationKey(conversationId)
        synchronized(lock) {
            conversationEpochs[key] = (conversationEpochs[key] ?: 0L) + 1L
            invalidatedConversations += key
        }
        notifyMemoryInvalidators(conversationId, null)
        scheduleDeletion("$key-")
    }

    /** Clears persistent entries after all user data is deleted; epochs protect in-flight writers. */
    fun invalidateAll() {
        globalEpoch.incrementAndGet()
        synchronized(lock) {
            invalidatedConversations += ALL_CONVERSATIONS
        }
        notifyMemoryInvalidators(null, null)
        scheduleDeletion(prefix = null)
    }

    /** Re-authorizes IDs read from canonical conversation storage after a successful selection/restore. */
    fun authorizeConversation(conversationId: String, currentMessageIds: Collection<String>) {
        val conversationKey = conversationKey(conversationId)
        synchronized(lock) {
            invalidatedConversations.remove(conversationKey)
            currentMessageIds.forEach { id -> invalidatedMessages.remove(messageKey(conversationKey, id)) }
        }
    }

    fun metrics(): MarkdownAstCacheMetrics = MarkdownAstCacheMetrics(
        hits = hitCount.get(),
        misses = missCount.get(),
        bytesRead = bytesReadCount.get(),
        bytesWritten = bytesWrittenCount.get(),
        evictions = evictionCount.get(),
        corruptEntries = corruptCount.get(),
        serializationNanos = serializeNanos.get(),
        deserializationNanos = deserializeNanos.get()
    )

    /** For focused format/deletion tests; does not expose the file to UI code. */
    internal fun entryFileForTest(identity: MarkdownRenderCacheIdentity, content: String): File =
        entryFile(identity, content)

    private fun notifyMemoryInvalidators(conversationId: String?, messageId: String?) {
        val listeners = synchronized(lock) { memoryInvalidators.values.toList() }
        listeners.forEach { listener -> runCatching { listener(conversationId, messageId) } }
    }

    private fun captureKey(identity: MarkdownRenderCacheIdentity): Pair<String, String> =
        conversationKey(identity.conversationId) to messageKey(identity)

    private fun isCurrent(identity: MarkdownRenderCacheIdentity, epoch: MarkdownAstCacheEpoch): Boolean = synchronized(lock) {
        isCurrentLocked(identity, epoch)
    }

    private fun isCurrentLocked(identity: MarkdownRenderCacheIdentity, epoch: MarkdownAstCacheEpoch): Boolean {
        val (conversationKey, messageKey) = captureKey(identity)
        return epoch.global == globalEpoch.get() &&
            epoch.conversation == (conversationEpochs[conversationKey] ?: 0L) &&
            epoch.message == (messageEpochs[messageKey] ?: 0L) &&
            ALL_CONVERSATIONS !in invalidatedConversations &&
            conversationKey !in invalidatedConversations &&
            messageKey !in invalidatedMessages
    }

    private fun entryFile(identity: MarkdownRenderCacheIdentity, content: String): File {
        val conversationKey = conversationKey(identity.conversationId)
        val messageKey = sha256(identity.messageId).take(HASH_PREFIX_CHARS)
        val blockKey = sha256(identity.blockId)
        return File(root, "$conversationKey-$messageKey-$blockKey-${sha256(content)}-v$SCHEMA_VERSION.json")
    }

    private fun scheduleDeletion(prefix: String?) {
        ioScope.launch {
            var deleted = false
            for (attempt in 0 until CLEANUP_ATTEMPTS) {
                val files = root.listFiles().orEmpty().filter { file ->
                    file.isFile && (prefix == null && (file.extension == "json" || file.name.endsWith(".tmp")) ||
                        prefix != null && file.name.startsWith(prefix) &&
                        (file.extension == "json" || file.name.endsWith(".tmp")))
                }
                val failures = files.filterNot { it.delete() && !it.exists() }
                if (failures.isEmpty()) {
                    deleted = true
                    break
                }
                if (attempt + 1 < CLEANUP_ATTEMPTS) delay(CLEANUP_RETRY_DELAY_MS * (attempt + 1))
            }
            if (deleted && prefix == null) {
                synchronized(lock) { invalidatedConversations.remove(ALL_CONVERSATIONS) }
            }
            // Epoch tombstones remain in memory after cleanup. Failed physical cleanup is retried on a
            // later deletion/startup; entries remain logically unreachable for this process.
            if (!deleted) retryPendingCleanup(prefix)
        }
    }

    private fun retryPendingCleanup(prefix: String?) {
        ioScope.launch {
            delay(CLEANUP_RETRY_DELAY_MS * CLEANUP_ATTEMPTS)
            val files = root.listFiles().orEmpty().filter { file ->
                file.isFile && (prefix == null && (file.extension == "json" || file.name.endsWith(".tmp")) ||
                    prefix != null && file.name.startsWith(prefix) &&
                    (file.extension == "json" || file.name.endsWith(".tmp")))
            }
            files.forEach { it.delete() }
        }
    }

    private fun cleanupStaleTemporaryFiles() {
        val staleBefore = System.currentTimeMillis() - STALE_TEMP_AGE_MS
        root.listFiles().orEmpty().filter {
            it.isFile && it.name.endsWith(".tmp") && it.lastModified() < staleBefore
        }.forEach { it.delete() }
    }

    private fun evictToBudget() {
        val entries = root.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
            .sortedBy { it.lastModified() }
        var total = entries.sumOf { it.length() }
        for (file in entries) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                evictionCount.incrementAndGet()
            }
        }
    }

    private fun flatten(rootNode: ASTNode): List<AstNodeRecord>? {
        val records = mutableListOf<MutableAstNodeRecord?>()
        val pending = ArrayDeque<PendingNode>()
        records += null
        pending.addLast(PendingNode(0, rootNode))
        while (pending.isNotEmpty()) {
            if (records.size > MAX_PERSISTED_AST_NODES) return null
            val (index, node) = pending.removeLast()
            val type = node.type as? MarkdownElementType ?: return null
            val childIndices = ArrayList<Int>(node.children.size)
            for (child in node.children) {
                childIndices += records.size
                records += null
            }
            records[index] = MutableAstNodeRecord(
                typeName = type.name,
                token = type.isToken,
                startOffset = node.startOffset,
                endOffset = node.endOffset,
                composite = node !is LeafASTNode,
                children = childIndices
            )
            for (childIndex in childIndices.indices.reversed()) {
                pending.addLast(PendingNode(childIndices[childIndex], node.children[childIndex]))
            }
        }
        return records.map { record ->
            val actual = record ?: return null
            AstNodeRecord(actual.typeName, actual.token, actual.startOffset, actual.endOffset, actual.composite, actual.children)
        }
    }

    private fun reconstruct(entry: DiskEntry, content: String): ASTNode? {
        val typeByKey = markdownTypeRegistry()
        val builder = ASTNodeBuilder(content, CancellationToken.NonCancellable)
        val nodes = arrayOfNulls<ASTNode>(entry.nodes.size)
        for (index in entry.nodes.lastIndex downTo 0) {
            val record = entry.nodes[index]
            val type = typeByKey[TypeKey(record.typeName, record.token)] ?: return null
            val children = record.children.map { childIndex ->
                if (childIndex !in nodes.indices) return null
                nodes[childIndex] ?: return null
            }
            val node: ASTNode = if (record.composite) {
                builder.createCompositeNode(type, children)
            } else {
                if (record.startOffset < 0 || record.endOffset < record.startOffset || record.endOffset > content.length) return null
                LeafASTNode(type, record.startOffset, record.endOffset)
            }
            nodes[index] = node
        }
        return nodes.getOrNull(entry.root)
    }

    private fun collectLinks(rootNode: ASTNode, content: String): Map<String, String?> {
        val links = LinkedHashMap<String, String?>()
        val pending = ArrayDeque<ASTNode>()
        pending.addLast(rootNode)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            var linkOnly = false
            val label = when (node.type) {
                MarkdownElementTypes.LINK_DEFINITION ->
                    node.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(content)
                MarkdownElementTypes.INLINE_LINK ->
                    node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getUnescapedTextInNode(content)
                MarkdownElementTypes.AUTOLINK -> {
                    linkOnly = true
                    (node.children.firstOrNull { it.type.name == MarkdownElementTypes.AUTOLINK.name } ?: node)
                        .getUnescapedTextInNode(content)
                }
                else -> null
            }
            if (label != null) {
                val destination = if (linkOnly) label else {
                    node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getUnescapedTextInNode(content)
                }
                links[label] = destination
            }
            node.children.asReversed().forEach(pending::addLast)
        }
        return links
    }

    private fun conversationKey(conversationId: String): String = sha256(conversationId).take(HASH_PREFIX_CHARS)

    private fun messageKey(identity: MarkdownRenderCacheIdentity): String =
        messageKey(conversationKey(identity.conversationId), identity.messageId)

    private fun messageKey(conversationKey: String, messageId: String): String =
        "$conversationKey-${sha256(messageId).take(HASH_PREFIX_CHARS)}"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        const val SCHEMA_VERSION = 1
        const val PARSER_VERSION = 1
        const val MAX_PERSISTED_SOURCE_CHARS = 128_000
        const val MAX_ENTRY_BYTES = 4L * 1024L * 1024L
        const val MAX_PERSISTED_AST_NODES = 250_000
        const val DEFAULT_MAX_BYTES = 64L * 1024L * 1024L
        const val HASH_PREFIX_CHARS = 32
        const val CLEANUP_ATTEMPTS = 3
        const val CLEANUP_RETRY_DELAY_MS = 250L
        const val STALE_TEMP_AGE_MS = 24L * 60L * 60L * 1_000L
        const val ALL_CONVERSATIONS = "__all__"
        private val typeRegistry: Map<TypeKey, IElementType> by lazy { buildMarkdownTypeRegistry() }

        private fun markdownTypeRegistry(): Map<TypeKey, IElementType> = typeRegistry

        private fun buildMarkdownTypeRegistry(): Map<TypeKey, IElementType> {
            val result = HashMap<TypeKey, IElementType>()
            val typeHolders = listOf(
                MarkdownElementTypes::class.java,
                MarkdownTokenTypes::class.java,
                GFMElementTypes::class.java,
                GFMTokenTypes::class.java
            )
            for (typeHolder in typeHolders) {
                for (field in typeHolder.fields) {
                    val value = runCatching { field.get(null) as? IElementType }.getOrNull() ?: continue
                    val markdownType = value as? MarkdownElementType ?: continue
                    result[TypeKey(markdownType.name, markdownType.isToken)] = markdownType
                }
            }
            return result
        }

        @Volatile private var sharedInstance: PersistentMarkdownAstCache? = null

        fun get(context: Context): PersistentMarkdownAstCache = synchronized(this) {
            sharedInstance ?: PersistentMarkdownAstCache(
                root = File(context.applicationContext.noBackupFilesDir, "render-cache")
            ).also { sharedInstance = it }
        }

        fun invalidateMessage(conversationId: String, messageId: String) {
            sharedInstance?.invalidateMessage(conversationId, messageId)
        }

        fun invalidateConversation(conversationId: String) {
            sharedInstance?.invalidateConversation(conversationId)
        }

        fun invalidateAll() {
            sharedInstance?.invalidateAll()
        }

        fun authorizeConversation(conversationId: String, currentMessageIds: Collection<String>) {
            sharedInstance?.authorizeConversation(conversationId, currentMessageIds)
        }
    }
}
