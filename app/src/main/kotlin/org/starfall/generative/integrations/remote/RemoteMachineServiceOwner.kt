package org.starfall.generative.integrations.remote

/**
 * Implemented by the application so system components such as the SFTP DocumentsProvider can
 * use the same app-scoped service (and therefore the same live machine state and idle sessions).
 */
interface RemoteMachineServiceOwner {
    val remoteMachineService: RemoteMachineService
}
