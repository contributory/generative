package org.starfall.multigateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.shared.model.REMOTE_TOOL_DISCONNECT
import org.starfall.multigateway.shared.model.REMOTE_TOOL_EXECUTE
import org.starfall.multigateway.shared.model.REMOTE_TOOL_START_MACHINE
import org.starfall.multigateway.shared.model.REMOTE_TOOL_UPDATE_IP
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.RemoteMachineType
import org.starfall.multigateway.integrations.remote.RemoteSecretRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.starfall.multigateway.integrations.remote.PasswordKeyboardInteractiveUserInfo
import org.starfall.multigateway.integrations.remote.SshConfigParser
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.remoteMachineToolEnabled

class RemoteMachineTest {
    @Test
    fun sshConfigImportsConnectionAndCommandOptions() {
        val hosts = SshConfigParser.parse(
            """
            Host staging
              HostName staging.example.com
              User deploy
              Port 2201
              IdentityFile ~/.ssh/staging
              ProxyCommand helper %h %p
              LocalCommand notify-send connected
              ServerAliveInterval 45
            """.trimIndent()
        )

        val machine = SshConfigParser.toMachine(hosts.single())
        assertEquals("staging", machine.name)
        assertEquals("staging.example.com", machine.host)
        assertEquals(2201, machine.port)
        assertEquals("deploy", machine.username)
        assertEquals("~/.ssh/staging", machine.identityFile)
        assertEquals("helper %h %p", machine.proxyCommand)
        assertEquals("notify-send connected", machine.localCommand)
        assertEquals(45, machine.keepAliveSeconds)
    }

    @Test
    fun everyMachineExposesDisconnectAndExecuteAndCloudShellAddsUpdateIp() {
        assertEquals(
            listOf(REMOTE_TOOL_DISCONNECT, REMOTE_TOOL_EXECUTE),
            RemoteMachine("ssh", "SSH").supportedToolNames()
        )
        assertEquals(
            listOf(REMOTE_TOOL_UPDATE_IP, REMOTE_TOOL_DISCONNECT, REMOTE_TOOL_EXECUTE),
            RemoteMachine("cloud", "Cloud", type = RemoteMachineType.GCLOUD_SHELL).supportedToolNames()
        )
        assertTrue(RemoteMachine("cloud", "Cloud", type = RemoteMachineType.GCLOUD_SHELL).displayHost.isNotBlank())
    }

    @Test
    fun savingAndDeletingAHostBlockPreservesOtherSshConfigEntries() {
        val machine = RemoteMachine(
            id = "one",
            name = "My server",
            host = "10.0.0.8",
            username = "dev",
            port = 2222,
            strictHostKeyChecking = true
        )
        val initial = "Host another\n  HostName other.example\n"
        val withMachine = SshConfigParser.upsertMachine(initial, machine)
        assertTrue(withMachine.contains("Host My-server"))
        assertTrue(withMachine.contains("Host another"))
        assertTrue(withMachine.contains("StrictHostKeyChecking yes"))

        val updated = SshConfigParser.upsertMachine(withMachine, machine.copy(host = "10.0.0.9"))
        assertEquals(1, Regex("Host My-server").findAll(updated).count())
        assertTrue(updated.contains("HostName 10.0.0.9"))
        assertTrue(SshConfigParser.removeMachine(updated, machine).contains("Host another"))
    }

    @Test
    fun passwordAuthenticationAnswersKeyboardInteractivePrompts() {
        val userInfo = PasswordKeyboardInteractiveUserInfo("deploy", "secret")

        assertArrayEquals(
            arrayOf("secret"),
            userInfo.promptKeyboardInteractive(
                "deploy@staging",
                "",
                "",
                arrayOf("Password for deploy@staging: "),
                booleanArrayOf(false)
            )
        )
        assertArrayEquals(
            arrayOf("deploy", "secret"),
            userInfo.promptKeyboardInteractive(
                "deploy@staging",
                "",
                "",
                arrayOf("login:", "Password:"),
                booleanArrayOf(true, false)
            )
        )
        assertArrayEquals(
            arrayOf("secret"),
            userInfo.promptKeyboardInteractive(
                "deploy@staging", "", "", arrayOf("Verification code:"), booleanArrayOf(false)
            )
        )
    }

    @Test
    fun emptyPasswordAsksOnlyWhenTheServerRequestsOne() {
        val requests = mutableListOf<RemoteSecretRequest>()
        val userInfo = PasswordKeyboardInteractiveUserInfo("deploy", "") { request ->
            requests += request
            "typed"
        }
        // Nothing is asked until JSch actually invokes a prompt (a no-auth server never does).
        assertTrue(requests.isEmpty())

        assertTrue(userInfo.promptPassword("Password for deploy@host"))
        assertEquals("typed", userInfo.password)
        assertFalse(requests.single().passphrase)

        assertArrayEquals(
            arrayOf("typed"),
            userInfo.promptKeyboardInteractive("d", "", "", arrayOf("Password:"), booleanArrayOf(false))
        )
        assertEquals(2, requests.size)

        assertTrue(userInfo.promptPassphrase("Passphrase for key"))
        assertEquals("typed", userInfo.passphrase)
        assertTrue(requests.last().passphrase)
    }

    @Test
    fun emptyPasswordWithoutPromptOrWhenCancelledAuthenticationFails() {
        val silent = PasswordKeyboardInteractiveUserInfo("deploy", "")
        assertFalse(silent.promptPassword("Password:"))
        assertNull(silent.promptKeyboardInteractive("d", "", "", arrayOf("Password:"), booleanArrayOf(false)))

        val cancelled = PasswordKeyboardInteractiveUserInfo("deploy", "") { null }
        assertFalse(cancelled.promptPassword("Password:"))
        assertNull(cancelled.promptKeyboardInteractive("d", "", "", arrayOf("Password:"), booleanArrayOf(false)))
    }

    @Test
    fun savedPasswordNeverPrompts() {
        var asked = false
        val userInfo = PasswordKeyboardInteractiveUserInfo("deploy", "secret") { asked = true; "typed" }
        assertTrue(userInfo.promptPassword("Password:"))
        assertEquals("secret", userInfo.password)
        userInfo.promptKeyboardInteractive("d", "", "", arrayOf("Password:"), booleanArrayOf(false))
        assertFalse(asked)
    }

    @Test
    fun passwordMachineUsesKeyboardInteractiveByDefaultForExistingRecords() {
        assertTrue(
            RemoteMachine(
                id = "password",
                name = "Password",
                authentication = org.starfall.multigateway.shared.model.RemoteAuthentication.PASSWORD
            ).keyboardInteractive
        )
    }

    @Test
    fun machineAndPerToolSwitchesAreIndependent() {
        val machine = RemoteMachine(
            id = "cloud",
            name = "Cloud",
            type = RemoteMachineType.GCLOUD_SHELL,
            aiTools = mapOf(REMOTE_TOOL_START_MACHINE to false)
        )
        val settings = ToolSettings(enabledMachines = mapOf(machine.id to true))

        assertTrue(remoteMachineToolEnabled(settings, machine, REMOTE_TOOL_EXECUTE))
        assertTrue(!remoteMachineToolEnabled(settings, machine, REMOTE_TOOL_START_MACHINE))
        assertTrue(!remoteMachineToolEnabled(settings.copy(enabledMachines = mapOf(machine.id to false)), machine, REMOTE_TOOL_EXECUTE))
    }

    @Test
    fun renamingAMachineMovesItsBlockAndKeepsUnmanagedOptions() {
        val machine = RemoteMachine(id = "one", name = "old", host = "10.0.0.8", username = "dev")
        val initial = "Host old\n  HostName 10.0.0.8\n  ForwardAgent yes\n\nHost other\n  HostName o.example\n"
        val renamed = SshConfigParser.upsertMachine(initial, machine.copy(name = "new"), previousName = "old")
        assertTrue(renamed.contains("Host new"))
        assertTrue(!renamed.contains("Host old"))
        assertTrue(renamed.contains("ForwardAgent yes"))
        assertTrue(renamed.contains("Host other"))
    }

    @Test
    fun deletingIsCaseInsensitiveOnKeywordAndOnlyDropsOwnAliasFromSharedBlocks() {
        val machine = RemoteMachine(id = "one", name = "box")
        val lowercase = "host box\n  HostName 1.1.1.1\n\nHost keep\n  HostName k\n"
        val removed = SshConfigParser.removeMachine(lowercase, machine)
        assertTrue(!removed.contains("box"))
        assertTrue(removed.contains("Host keep"))
        val shared = SshConfigParser.removeMachine("Host box other\n  HostName 1.1.1.1\n", machine)
        assertTrue(shared.contains("Host other"))
        assertTrue(!shared.contains("box"))
    }

    @Test
    fun editingSshConfigIsAppliedToMatchingMachinesAndKeepsSecrets() {
        val machine = RemoteMachine(id = "one", name = "box", host = "1.1.1.1", username = "dev", password = "secret")
        val edited = "Host box\n  HostName 2.2.2.2\n  Port 2200\n  User root\n  Compression yes\n"
        val synced = SshConfigParser.syncMachines(listOf(machine), edited).single()
        assertEquals("2.2.2.2", synced.host)
        assertEquals(2200, synced.port)
        assertEquals("root", synced.username)
        assertTrue(synced.compression)
        assertEquals(0, synced.keepAliveSeconds)
        assertEquals("secret", synced.password)
    }

    @Test
    fun publicKeyNormalizationKeepsAlgorithmAndDataAndRejectsMismatches() {
        // blob = length-prefixed "ssh-rsa" followed by filler
        val blob = java.util.Base64.getEncoder().encodeToString(
            byteArrayOf(0, 0, 0, 7) + "ssh-rsa".toByteArray() + byteArrayOf(1, 2, 3)
        )
        assertEquals("ssh-rsa $blob", org.starfall.multigateway.integrations.remote.normalizeOpenSshPublicKey("# note\nssh-rsa $blob user@host\n"))
        assertTrue(runCatching { org.starfall.multigateway.integrations.remote.normalizeOpenSshPublicKey("ecdsa-sha2-nistp256 $blob") }.isFailure)
        assertTrue(runCatching { org.starfall.multigateway.integrations.remote.normalizeOpenSshPublicKey("-----BEGIN OPENSSH PRIVATE KEY-----") }.isFailure)
        assertTrue(runCatching { org.starfall.multigateway.integrations.remote.normalizeOpenSshPublicKey("") }.isFailure)
    }

    @Test
    fun importedBinariesAreStartedThroughTheLinkerOrInterpreterOnAndroid10Plus() {
        fun elf(bits: Int, type: Int) = ByteArray(64).also {
            it[0] = 0x7f; it[1] = 'E'.code.toByte(); it[2] = 'L'.code.toByte(); it[3] = 'F'.code.toByte()
            it[4] = bits.toByte(); it[5] = 1; it[16] = type.toByte()
        }
        fun run(header: ByteArray, sdk: Int, target: Int = 36, exists: (String) -> Boolean = { true }): List<String> =
            org.starfall.multigateway.integrations.remote.binaryLauncher(header, sdk, target, exists)
        assertEquals(listOf("/system/bin/linker64"), run(elf(2, 3), 34))
        assertEquals(listOf("/system/bin/linker"), run(elf(1, 3), 34))
        assertEquals(emptyList<String>(), run(elf(2, 3), 28))
        assertEquals(emptyList<String>(), run(elf(2, 3), 34, target = 28))
        assertEquals(emptyList<String>(), run(elf(2, 3), 34) { false })
        assertEquals(listOf("/system/bin/sh"), run("#!/usr/bin/env bash\necho".toByteArray(), 34) { false })
        assertEquals(listOf("/bin/sh", "-e"), run("#!/bin/sh -e\n".toByteArray(), 34))
    }
}
