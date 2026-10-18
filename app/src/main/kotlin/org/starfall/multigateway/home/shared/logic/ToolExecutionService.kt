package org.starfall.multigateway.home.shared.logic

import kotlinx.serialization.json.JsonObject
import org.starfall.multigateway.integrations.mcp.McpService
import org.starfall.multigateway.integrations.mcp.McpSession
import org.starfall.multigateway.integrations.remote.RemoteMachineService
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.ToolDefinition

/** Owns calls into external MCP and Remote Machine services for the chat tool loop. */
internal class ToolExecutionService(
    private val mcpService: McpService,
    private val remoteMachineService: RemoteMachineService?
) {
    fun configuredMachines(): List<RemoteMachine> = remoteMachineService?.state?.value?.machines.orEmpty()

    suspend fun connect(server: McpInfo): McpSession = mcpService.session(server)

    suspend fun discoverTools(session: McpSession): List<ToolDefinition> = session.tools()

    suspend fun callMcpTool(
        session: McpSession,
        name: String,
        arguments: JsonObject,
        retainLongTextInMemory: Boolean
    ): JsonObject = session.call(name, arguments, retainLongTextInMemory)

    suspend fun executeRemoteTool(
        machine: RemoteMachine,
        name: String,
        arguments: JsonObject
    ): JsonObject = remoteMachineService?.executeTool(machine, name, arguments)
        ?: error("Remote Machine service is unavailable")

    fun disconnectRemoteMachines() {
        remoteMachineService?.disconnectAllWhenIdle()
    }
}
