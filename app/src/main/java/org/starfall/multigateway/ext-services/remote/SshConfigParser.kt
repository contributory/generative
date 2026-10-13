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
        "stricthostkeychecking", "compression", "serveraliveinterval", "connecttimeout",
        "preferredauthentications"
    )

    fun parse(text: String): List<SshConfigHost> {
        val result = mutableListOf<SshConfigHost>()
        var aliases = emptyList<String>()
        val options = linkedMapOf<String, String>()
        var hasStartedSection = false
        var inMatchSection = false

        fun flush() {
            if (!inMatchSection && (aliases.isNotEmpty() || (!hasStartedSection && options.isNotEmpty()))) {
                result += SshConfigHost(aliases, options.toMap())
            }
            aliases = emptyList()
            options.clear()
        }

        text.lineSequence().forEach { raw ->
            val (key, value) = splitDirective(raw) ?: return@forEach
            if (key == "host") {
                flush()
                hasStartedSection = true
                inMatchSection = false
                aliases = value.split(Regex("\\s+")).filter(String::isNotBlank)
            } else if (key == "match") {
                flush()
                hasStartedSection = true
                inMatchSection = true
            } else if (!inMatchSection && (aliases.isNotEmpty() || !hasStartedSection)) {
                options[key] = value
            }
        }
        flush()
        return result
    }

    /** Returns the effective options for an alias, including global and matching wildcard blocks. */
    fun find(text: String, alias: String): SshConfigHost? {
        val matching = parse(text).filter { host ->
            host.aliases.isEmpty() || hostMatches(host.aliases, alias)
        }
        if (matching.isEmpty()) return null
        // OpenSSH keeps the first value found for each option, so earlier defaults only fill gaps.
        val options = linkedMapOf<String, String>()
        matching.forEach { host -> host.options.forEach { (key, value) -> options.putIfAbsent(key, value) } }
        return SshConfigHost(listOf(alias), options)
    }

    fun hasExplicitAlias(text: String, name: String): Boolean {
        val alias = configAlias(name)
        return parse(text).any { alias in it.aliases }
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
            if (existing != null && (hostAliases(lines[existing.first])?.size ?: 1) > 1) {
                block[0] = lines[existing.first]
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
        if (existing != null && old != null) result = removeAlias(result, previousAlias)
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
        return machines.map { machine ->
            if (machine.type !in setOf(RemoteMachineType.SSH, RemoteMachineType.GCLOUD_SHELL)) return@map machine
            val isCloudShell = machine.type == RemoteMachineType.GCLOUD_SHELL
            val host = find(text, configAlias(machine.name))
            val o = host?.options.orEmpty()
            val identityFile = o["identityfile"].orEmpty()
            val preferred = o["preferredauthentications"]?.split(',')?.map { it.trim().lowercase() }.orEmpty()
            val hasPreferred = "preferredauthentications" in o
            val authentication = when {
                hasPreferred && ("password" in preferred || "keyboard-interactive" in preferred) -> RemoteAuthentication.PASSWORD
                hasPreferred && "publickey" in preferred -> RemoteAuthentication.PRIVATE_KEY
                identityFile.isNotBlank() -> RemoteAuthentication.PRIVATE_KEY
                else -> RemoteAuthentication.PASSWORD
            }
            val secret = machine.identityPassphrase.ifBlank { machine.password }
            machine.copy(
                // Machine name, address, user and the single secret are the persisted base record.
                // Other connection options are always hydrated from the SSH config file.
                host = if (isCloudShell) "" else machine.host,
                port = if (isCloudShell) 6_000 else o["port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                username = if (isCloudShell) o["user"].orEmpty() else machine.username,
                gcloudUsername = if (isCloudShell) o["user"].orEmpty() else machine.gcloudUsername,
                authentication = authentication,
                password = if (authentication == RemoteAuthentication.PASSWORD) secret else "",
                keyboardInteractive = if (hasPreferred) "keyboard-interactive" in preferred else true,
                identityFile = identityFile,
                identityPassphrase = if (authentication == RemoteAuthentication.PRIVATE_KEY) secret else "",
                preferredAuthentications = o["preferredauthentications"].orEmpty(),
                proxyCommand = o["proxycommand"].orEmpty(),
                localCommand = o["localcommand"].orEmpty(),
                strictHostKeyChecking = o["stricthostkeychecking"]?.equals("yes", true) == true,
                compression = o["compression"]?.equals("yes", true) == true,
                keepAliveSeconds = o["serveraliveinterval"]?.toIntOrNull()?.coerceIn(0, 3_600) ?: 0,
                connectTimeoutSeconds = o["connecttimeout"]?.toIntOrNull()?.coerceIn(1, 300) ?: 15
            )
        }
    }

    /** Imports the common fields without discarding the original config file. */
    fun toMachine(host: SshConfigHost, id: String = host.aliases.first()): RemoteMachine {
        val options = host.options
        val identityFile = options["identityfile"].orEmpty()
        val preferred = options["preferredauthentications"]?.split(',')?.map { it.trim().lowercase() }.orEmpty()
        val privateKey = if ("preferredauthentications" in options) {
            "password" !in preferred && "keyboard-interactive" !in preferred && "publickey" in preferred
        } else identityFile.isNotBlank()
        return RemoteMachine(
            id = id,
            name = host.aliases.first(),
            host = options["hostname"].orEmpty().ifBlank { host.aliases.first() },
            port = options["port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
            username = options["user"].orEmpty(),
            authentication = if (privateKey) RemoteAuthentication.PRIVATE_KEY else RemoteAuthentication.PASSWORD,
            keyboardInteractive = if ("preferredauthentications" in options) "keyboard-interactive" in preferred else true,
            identityFile = identityFile,
            preferredAuthentications = options["preferredauthentications"].orEmpty(),
            proxyCommand = options["proxycommand"].orEmpty(),
            localCommand = options["localcommand"].orEmpty(),
            strictHostKeyChecking = options["stricthostkeychecking"]?.equals("yes", true) == true,
            compression = options["compression"]?.equals("yes", true) == true,
            keepAliveSeconds = options["serveraliveinterval"]?.toIntOrNull()?.coerceAtLeast(0) ?: 30,
            connectTimeoutSeconds = options["connecttimeout"]?.toIntOrNull()?.coerceIn(1, 300) ?: 15
        )
    }

    private fun renderMachine(alias: String, machine: RemoteMachine): String = buildString {
        appendLine("Host $alias")
        if (machine.type != RemoteMachineType.GCLOUD_SHELL) {
            machine.host.trim().takeIf(String::isNotBlank)?.let { appendLine("  HostName $it") }
        }
        appendLine("  Port ${if (machine.type == RemoteMachineType.GCLOUD_SHELL) 6_000 else machine.port.coerceIn(1, 65535)}")
        (if (machine.type == RemoteMachineType.GCLOUD_SHELL) machine.username.ifBlank { machine.gcloudUsername } else machine.username)
            .trim().takeIf(String::isNotBlank)?.let { appendLine("  User $it") }
        appendLine("  PreferredAuthentications ${if (machine.authentication == RemoteAuthentication.PASSWORD) {
            if (machine.keyboardInteractive) "keyboard-interactive,password" else "password"
        } else "publickey"}")
        machine.identityFile.trim().takeIf(String::isNotBlank)?.let { appendLine("  IdentityFile $it") }
        machine.proxyCommand.trim().takeIf(String::isNotBlank)?.let { appendLine("  ProxyCommand $it") }
        machine.localCommand.trim().takeIf(String::isNotBlank)?.let { appendLine("  LocalCommand $it") }
        machine.strictHostKeyChecking.takeIf { it }?.let { appendLine("  StrictHostKeyChecking yes") }
        machine.compression.takeIf { it }?.let { appendLine("  Compression yes") }
        machine.keepAliveSeconds.takeIf { it > 0 }?.let { appendLine("  ServerAliveInterval $it") }
        appendLine("  ConnectTimeout ${machine.connectTimeoutSeconds.coerceIn(1, 300)}")
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

    private fun hostMatches(patterns: List<String>, alias: String): Boolean {
        val negative = patterns.filter { it.startsWith('!') }.map { it.drop(1) }
        if (negative.any { globMatches(it, alias) }) return false
        val positive = patterns.filterNot { it.startsWith('!') }
        return positive.isEmpty() || positive.any { globMatches(it, alias) }
    }

    private fun globMatches(pattern: String, value: String): Boolean {
        val regex = buildString {
            append('^')
            pattern.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> {
                        if (char in ".\\+()[]{}^$|") append('\\')
                        append(char)
                    }
                }
            }
            append('$')
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(value)
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
