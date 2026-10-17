package org.starfall.multigateway.ui.tools

import androidx.compose.runtime.staticCompositionLocalOf
import org.starfall.multigateway.data.model.*
import org.starfall.multigateway.data.model.RemoteMachine

data class ToolControls(
    val servers: List<McpInfo> = emptyList(),
    val machines: List<RemoteMachine> = emptyList(),
    val settings: ToolSettings = ToolSettings(),
    val providers: List<LlmProviderInfo> = emptyList(),
    val setSystem: (String,SystemToolConfig)->Unit = {_,_->},
    val setMcp: (String,Boolean)->Unit = {_,_->},
    val setMachines: (Boolean)->Unit = {},
    val setMachine: (String, Boolean)->Unit = {_,_->},
    val setMachineTool: (String, String, Boolean)->Unit = {_,_,_->},
    val setSendFile: (Boolean)->Unit = {}
)
val LocalToolControls = staticCompositionLocalOf { ToolControls() }
