package org.starfall.multigateway.extservices.remote

/** A small OpenSSH config parser used for importing aliases and for editor validation. */
data class SshConfigHost(
    val aliases: List<String>,
    val options: Map<String, String>
)

object SshConfigParser {
    /** Options that are generated from (and synced back to) a saved Remote Machine. */
    private val managedKeys = setOf(
        "hostname", "port", "user", "identityfile", "proxycommand", "localcommand",
        "stricthostkeychecking", "compression", "serveraliveinterval"
    )

    fun parse(text: String): List<SshConfigHost> {
        val result = mutableListOf<SshConfigHost>()
        var aliases = emptyList<String>()
        val options = linkedMapOf<String, String>()

        fun flush() {
            if (aliases.isNotEmpty()) result += SshConfigHost(aliases, options.toMap())
            aliases = emptyList()
            options.clear()
        }

        text.lineSequence().forEach { raw ->
            val (key, value) = splitDirective(raw) ?: return@forEach
            if (key == "host") {
                flush()
                aliases = value.split(Regex("\\s+")).filter(String::isNotBlank)
            } else if (key == "match") {
                flush()
            } else if (aliases.isNotEmpty()) {
                options[key] = value
            }
        }
        flush()
        return result
    }

    fun find(text: String, alias: String): SshConfigHost? = parse(text).firstOrNull {
        alias in it.aliases
    }

    /**
     * Inserts or replaces the block generated for a saved Remote Machine. Options the app does not
     * manage (ProxyJump, ForwardAgent, comments...) are kept. Pass [previousName] when the machine
     * was renamed so the old block is moved instead of being left behind.
     */
    fun upsertMachine(text: String, machine: RemoteMachine, previousName: String? = null): String {
        val alias = configAlias(machine.name)
        val previousAlias = previousName?.let(::configAlias)?.takeIf { it != alias }
        val lines = normalized(text)
        val existing = findBlock(lines, alias)
        val old = previousAlias?.let { findBlock(lines, it) }
        val target = existing ?: old

        val block = renderMachine(alias, machine).trimEnd().lines().toMutableList()
        if (target != null) {
            val extras = lines.subList(target.first + 1, target.last + 1).filter { line ->
                val directive = splitDirective(line)
                if (directive == null) line.isNotBlank() else directive.first !in managedKeys
            }
            // Keep a multi-alias header such as "Host a b" untouched when updating the existing block.
            if (target == existing && (hostAliases(lines[target.first])?.size ?: 1) > 1) {
                block[0] = lines[target.first]
            }
            block += extras
            if (target.last < lines.lastIndex) block += ""
            lines.subList(target.first, target.last + 1).clear()
            lines.addAll(target.first, block)
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += block
        }
        var result = finish(lines)
        // Both the old and the new alias existed: the old block is now stale.
        if (existing != null && old != null && previousAlias != null) {
            result = removeAlias(result, previousAlias)
        }
        return result
    }

    /** Removes only the generated block belonging to [machine], leaving other config entries intact. */
    fun removeMachine(text: String, machine: RemoteMachine): String =
        removeAlias(text, configAlias(machine.name))

    fun removeAlias(text: String, alias: String): String {
        val lines = normalized(text)
        val range = findBlock(lines, alias) ?: return text
        val aliases = hostAliases(lines[range.first]).orEmpty()
        if (aliases.size > 1) {
            // Shared block ("Host a b"): only drop this alias.
            lines[range.first] = "Host " + aliases.filterNot { it == alias }.joinToString(" ")
        } else {
            lines.subList(range.first, range.last + 1).clear()
        }
        return finish(lines).let { if (it.isBlank()) "" else it }
    }

    /**
     * Applies edits made in the SSH config text to the saved SSH machines with a matching Host
     * alias. Secrets and app-only settings are kept; options removed from the block reset to defaults.
     */
    fun syncMachines(machines: List<RemoteMachine>, text: String): List<RemoteMachine> {
        val hosts = parse(text)
        return machines.map { machine ->
            if (machine.type != RemoteMachineType.SSH) return@map machine
            val alias = configAlias(machine.name)
            val host = hosts.firstOrNull { alias in it.aliases } ?: return@map machine
            val o = host.options
            machine.copy(
                host = o["hostname"].orEmpty(),
                port = o["port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                username = o["user"].orEmpty(),
                identityFile = o["identityfile"].orEmpty(),
                proxyCommand = o["proxycommand"].orEmpty(),
                localCommand = o["localcommand"].orEmpty(),
                strictHostKeyChecking = o["stricthostkeychecking"]?.equals("yes", true) == true,
                compression = o["compression"]?.equals("yes", true) == true,
                keepAliveSeconds = o["serveraliveinterval"]?.toIntOrNull()?.coerceIn(0, 3_600) ?: 0
            )
        }
    }

    /** Imports the common fields without discarding the original config file. */
    fun toMachine(host: SshConfigHost, id: String = host.aliases.first()): RemoteMachine {
        val options = host.options
        return RemoteMachine(
            id = id,
            name = host.aliases.first(),
            host = options["hostname"].orEmpty(),
            port = options["port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
            username = options["user"].orEmpty(),
            identityFile = options["identityfile"].orEmpty(),
            proxyCommand = options["proxycommand"].orEmpty(),
            localCommand = options["localcommand"].orEmpty(),
            strictHostKeyChecking = options["stricthostkeychecking"]?.equals("yes", true) == true,
            compression = options["compression"]?.equals("yes", true) == true,
            keepAliveSeconds = options["serveraliveinterval"]?.toIntOrNull()?.coerceAtLeast(0) ?: 30
        )
    }

    private fun renderMachine(alias: String, machine: RemoteMachine): String = buildString {
        appendLine("Host $alias")
        machine.host.trim().takeIf(String::isNotBlank)?.let { appendLine("  HostName $it") }
        appendLine("  Port ${machine.port.coerceIn(1, 65535)}")
        machine.username.trim().takeIf(String::isNotBlank)?.let { appendLine("  User $it") }
        machine.identityFile.trim().takeIf(String::isNotBlank)?.let { appendLine("  IdentityFile $it") }
        machine.proxyCommand.trim().takeIf(String::isNotBlank)?.let { appendLine("  ProxyCommand $it") }
        machine.localCommand.trim().takeIf(String::isNotBlank)?.let { appendLine("  LocalCommand $it") }
        machine.strictHostKeyChecking.takeIf { it }?.let { appendLine("  StrictHostKeyChecking yes") }
        machine.compression.takeIf { it }?.let { appendLine("  Compression yes") }
        machine.keepAliveSeconds.takeIf { it > 0 }?.let { appendLine("  ServerAliveInterval $it") }
    }

    /** Splits "Key value", "Key=value" or "Key = value" into a lowercase key and a clean value. */
    private fun splitDirective(raw: String): Pair<String, String>? {
        val line = raw.trim()
        if (line.isBlank() || line.startsWith('#')) return null
        val split = line.indexOfFirst { it == ' ' || it == '\t' || it == '=' }
        if (split <= 0) return null
        val key = line.substring(0, split).lowercase()
        val value = line.substring(split).trim().trimStart('=').trim().removeSurrounding("\"")
        return key to value
    }

    private fun hostAliases(line: String): List<String>? {
        val (key, value) = splitDirective(line) ?: return null
        if (key != "host") return null
        return value.split(Regex("\\s+")).filter(String::isNotBlank)
    }

    private fun findBlock(lines: List<String>, alias: String): IntRange? {
        val start = lines.indexOfFirst { hostAliases(it)?.contains(alias) == true }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { index ->
            splitDirective(lines[index])?.first.let { it == "host" || it == "match" }
        } ?: lines.size
        return start until end
    }

    private fun normalized(text: String): MutableList<String> =
        text.replace("\r\n", "\n").trimEnd('\n').lines().toMutableList().also {
            if (it.size == 1 && it[0].isBlank()) it.clear()
        }

    private fun finish(lines: List<String>): String =
        lines.joinToString("\n").trimEnd().let { if (it.isBlank()) "" else "$it\n" }

    private fun configAlias(name: String): String = name.trim()
        .replace(Regex("[^A-Za-z0-9_.@-]+"), "-")
        .trim('-')
        .ifBlank { "machine" }
}
