package org.starfall.multigateway.shared.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.starfall.multigateway.shared.model.logging.DebugLogCategory
import org.starfall.multigateway.shared.model.logging.DebugLogLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

data class DebugLogEntry(
    val id: Long,
    val timeMillis: Long,
    val category: DebugLogCategory,
    val level: DebugLogLevel,
    val tag: String,
    val message: String
) {
    val time: String get() = TIME_FORMAT.format(Instant.ofEpochMilli(timeMillis))

    /** Plain text used for the copy buttons. */
    fun format(): String = "[$time] [${category.name}] [${level.name}] $tag\n$message"

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}

/**
 * In-memory, bounded log shown by the floating debug panel. Nothing is recorded while debug logs are
 * off or the entry's category is not in scope, and the message lambda is then never evaluated.
 */
object DebugLog {
    const val MAX_ENTRIES = 500
    const val MAX_MESSAGE_CHARS = 4_000
    const val MAX_EXPORT_CHARS = 500_000

    private val nextId = AtomicLong()
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    @Volatile private var scopes: Set<DebugLogCategory> = emptySet()

    /** Applies the user's settings. Turning debug logs off also drops what was captured. */
    fun configure(enabled: Boolean, categories: Set<DebugLogCategory>) {
        scopes = if (enabled) categories.toSet() else emptySet()
        if (!enabled) clear()
    }

    fun isEnabled(category: DebugLogCategory): Boolean = category in scopes

    fun log(
        category: DebugLogCategory,
        tag: String,
        level: DebugLogLevel = DebugLogLevel.INFO,
        message: () -> String
    ) {
        if (category !in scopes) return
        val text = try { message() } catch (e: Exception) { "(log message unavailable: ${e.message})" }
        val safe = DebugLogRedactor.redact(text).let {
            if (it.length <= MAX_MESSAGE_CHARS) it
            else it.take(MAX_MESSAGE_CHARS) + "\n… (${it.length - MAX_MESSAGE_CHARS} more characters)"
        }
        val entry = DebugLogEntry(nextId.incrementAndGet(), System.currentTimeMillis(), category, level, tag, safe)
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }

    fun error(category: DebugLogCategory, tag: String, message: String, error: Throwable? = null) =
        log(category, tag, DebugLogLevel.ERROR) {
            if (error == null) message else "$message: ${error::class.java.simpleName}: ${error.message}"
        }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Newest entries that fit the clipboard limit, oldest first. */
    fun exportAll(entries: List<DebugLogEntry> = _entries.value): String {
        val picked = ArrayDeque<String>()
        var size = 0
        for (entry in entries.asReversed()) {
            val text = entry.format()
            if (picked.isNotEmpty() && size + text.length > MAX_EXPORT_CHARS) break
            picked.addFirst(text)
            size += text.length + 2
        }
        return picked.joinToString("\n\n")
    }
}
