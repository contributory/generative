package org.starfall.generative

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.starfall.generative.integrations.remote.sftpChildDocumentId
import org.starfall.generative.integrations.remote.sftpMachineIdOf
import org.starfall.generative.integrations.remote.sftpRelativePathOf
import org.starfall.generative.integrations.remote.sftpRootId
import org.starfall.generative.shared.model.REMOTE_SFTP_HOME
import org.starfall.generative.shared.model.RemoteMachine

class RemoteSftpDocumentFormattingTest {
    @Test
    fun machineRootIdsRoundTripAndContainNoPathSeparators() {
        val rootId = sftpRootId("server/with:unusual characters")

        assertFalse(rootId.contains('/'))
        assertEquals("server/with:unusual characters", sftpMachineIdOf(rootId))
    }

    @Test
    fun childDocumentIdsRoundTripRelativePaths() {
        val rootId = sftpRootId("machine")
        val childId = sftpChildDocumentId(rootId, "folder/sub folder/report.txt")

        assertEquals("", sftpRelativePathOf(rootId, rootId))
        assertEquals("folder/sub folder/report.txt", sftpRelativePathOf(rootId, childId))
    }

    @Test
    fun relativePathsCannotEscapeTheConfiguredRoot() {
        val rootId = sftpRootId("machine")
        val unsafe = rootId + "/" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("../outside".toByteArray(Charsets.UTF_8))

        assertThrows(FileNotFoundException::class.java) {
            sftpRelativePathOf(rootId, unsafe)
        }
    }

    @Test
    fun newMachinesUseHomeAsTheirSftpDirectory() {
        assertEquals(REMOTE_SFTP_HOME, RemoteMachine("machine", "Machine").sftpDirectory)
    }
}
