package org.starfall.multigateway.extservices.remote

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.starfall.multigateway.data.repository.LocalWriteErrors
import java.util.UUID

class RemoteMachinesViewModel(
    private val service: RemoteMachineService
) : ViewModel() {
    val state: StateFlow<RemoteMachineState> = service.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteMachineState())

    fun saveMachine(machine: RemoteMachine) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.saveMachine(machine) }
    }

    fun deleteMachine(id: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.deleteMachine(id) }
    }

    fun reorderMachines(ids: List<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.reorderMachines(ids) }
    }

    fun saveSshConfig(text: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.saveSshConfig(text) }
    }

    fun saveOAuthClient(client: GCloudOAuthClient) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.saveOAuthClient(client) }
    }

    fun deleteOAuthClient(id: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.deleteOAuthClient(id) }
    }

    suspend fun importBinary(uri: Uri): Result<RemoteBinary> = runCatching { service.importBinary(uri) }

    fun deleteBinary(id: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { service.deleteBinary(id) }
    }

    suspend fun importIdentity(machineId: String, uri: Uri): Result<String> =
        runCatching { service.importIdentity(machineId, uri) }

    suspend fun importOAuthClientJson(uri: Uri): Result<GCloudOAuthClient> =
        runCatching { service.parseOAuthClientJson(uri) }

    suspend fun authorize(client: GCloudOAuthClient): Result<Unit> = service.authorizeGCloud(client)

    suspend fun saveOAuthClientNow(client: GCloudOAuthClient) {
        service.saveOAuthClient(client)
    }

    suspend fun refreshIp(machine: RemoteMachine): Result<RemoteMachine> = service.refreshGCloudIp(machine)

    suspend fun startMachine(machine: RemoteMachine): Result<RemoteMachine> = service.startGCloudMachine(machine)

    suspend fun openTerminal(machine: RemoteMachine, passwordOverride: String? = null): RemoteTerminal =
        service.openTerminal(machine, passwordOverride)

    fun newMachine(): RemoteMachine = RemoteMachine(
        id = UUID.randomUUID().toString(),
        name = "",
        sortOrder = state.value.machines.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
    )

    fun newOAuthClient(): GCloudOAuthClient = GCloudOAuthClient(
        id = UUID.randomUUID().toString(),
        name = "",
        clientId = ""
    )
}
