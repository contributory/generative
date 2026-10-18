package org.starfall.multigateway.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.provider.ModelProvider

data class ToolControls(
    val servers: List<McpInfo> = emptyList(),
    val machines: List<RemoteMachine> = emptyList(),
    val settings: ToolSettings = ToolSettings(),
    val providers: List<ModelProvider> = emptyList(),
    val setSystem: (String,SystemToolConfig)->Unit = {_,_->},
    val setMcp: (String,Boolean)->Unit = {_,_->},
    val setMachines: (Boolean)->Unit = {},
    val setMachine: (String, Boolean)->Unit = {_,_->},
    val setMachineTool: (String, String, Boolean)->Unit = {_,_,_->},
    val setSendFile: (Boolean)->Unit = {}
)
val LocalToolControls = staticCompositionLocalOf { ToolControls() }
