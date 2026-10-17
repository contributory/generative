package org.starfall.multigateway.features.settings.appsettings.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import org.starfall.multigateway.R
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import org.starfall.multigateway.features.appdata.model.JsonExportKind
import org.starfall.multigateway.features.settings.shared.SettingsSection
import org.starfall.multigateway.shared.storage.preferences.AppLanguage
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.multigateway.shared.storage.preferences.DEFAULT_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.multigateway.shared.storage.preferences.DEFAULT_TOOL_ROUND_LIMIT
import org.starfall.multigateway.shared.storage.preferences.MAX_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.multigateway.shared.storage.preferences.MAX_TOOL_ROUND_LIMIT
import org.starfall.multigateway.shared.storage.preferences.MessageFontFamily
import org.starfall.multigateway.shared.storage.preferences.ScreenWakelockMode
import org.starfall.multigateway.shared.storage.preferences.ToolCallStrategy
import org.starfall.multigateway.shared.storage.preferences.WordWrapMode
import org.starfall.multigateway.shared.theme.ThemePresets
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.layout.SlideScreenContent
import org.starfall.multigateway.shared.ui.layout.windowHeightIn

@Composable
fun PreferenceToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsView(
    appPreferences: AppPreferences,
    onThemeChange: (String) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onColorSchemeChange: (String) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit = {},
    onWordWrapModeChange: (WordWrapMode) -> Unit = {},
    onWordWrapColumnChange: (Int) -> Unit = {},
    onCodePreviewEnabledChange: (Boolean) -> Unit = {},
    onCodeBlockDisplayModeChange: (CodeBlockDisplayMode) -> Unit = {},
    onCodeBlockMaxLinesChange: (Int) -> Unit = {},
    onCodeBlockIncludeUndefinedChange: (Boolean) -> Unit = {},
    onMessageFontSizeChange: (Int) -> Unit = {},
    onMessageFontSizeCustomChange: (Boolean) -> Unit = {},
    onMessageFontFamilyChange: (MessageFontFamily) -> Unit = {}
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsSection(stringResource(R.string.settings_theme)) {
                Text(stringResource(R.string.settings_theme_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                        FilterChip(selected = appPreferences.themeMode == mode, onClick = { onThemeChange(mode) },
                            label = { Text(stringResource(when (mode) {
                                "SYSTEM" -> R.string.settings_theme_system
                                "LIGHT" -> R.string.settings_theme_light
                                else -> R.string.settings_theme_dark
                            })) }, modifier = Modifier.weight(1f))
                    }
                }
                PreferenceToggle(stringResource(R.string.settings_amoled), stringResource(R.string.settings_amoled_description), appPreferences.useAmoled, onAmoledChange)
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_color)) {
                PreferenceToggle(stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_description),
                    appPreferences.useDynamicColor, onDynamicColorChange)
                if (!appPreferences.useDynamicColor) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(stringResource(R.string.settings_color_palette), style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemePresets.forEach { preset ->
                            val name = preset.name
                            val color = preset.preview
                            val selected = appPreferences.colorSchemeName == name
                            Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
                                    if (selected) color else color.copy(alpha = 0.3f)),
                                modifier = Modifier.width(58.dp).height(56.dp)
                                    .toggleable(selected, role = Role.RadioButton, onValueChange = { onColorSchemeChange(name) })
                                    .then(Modifier.semantics { contentDescription = name.lowercase().replaceFirstChar { it.uppercase() } })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(26.dp).clip(CircleShape).background(color))
                                    if (selected) FluentIcon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_code_blocks)) {
                CodeRenderingPreferences(appPreferences, onWordWrapModeChange, onWordWrapColumnChange,
                    onCodePreviewEnabledChange, onMessageFontSizeChange, onMessageFontFamilyChange,
                    onCodeBlockDisplayModeChange, onCodeBlockMaxLinesChange, onCodeBlockIncludeUndefinedChange,
                    includeTextAppearance = false)
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_text_appearance)) {
                TextAppearancePreferences(appPreferences, onMessageFontSizeChange, onMessageFontFamilyChange, onMessageFontSizeCustomChange)
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_system_bars)) {
                PreferenceToggle(stringResource(R.string.settings_immersive_mode), stringResource(R.string.settings_immersive_mode_description),
                    appPreferences.hideStatusBar, onHideStatusBarChange)
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreferencesSettingsView(
    appPreferences: AppPreferences,
    onContinueLastConversationChange: (Boolean) -> Unit,
    onPersistSelectedModelChange: (Boolean) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onEnableVibrationChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onLatexModeChange: (String) -> Unit,
    onTtsReadCodeBlocksChange: (Boolean) -> Unit = {},
    onToolRoundLimitEnabledChange: (Boolean) -> Unit = {},
    onToolRoundLimitChange: (Int) -> Unit = {},
    onToolCallStrategyChange: (ToolCallStrategy) -> Unit = {},
    onToolCallConcurrentLimitChange: (Int) -> Unit = {},
    onScreenWakelockModeChange: (ScreenWakelockMode) -> Unit = {},
    onAppLanguageChange: (useSystem: Boolean, tag: String) -> Unit = { _, _ -> }
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsSection(stringResource(R.string.settings_language)) {
                PreferenceToggle(stringResource(R.string.use_system_language), stringResource(R.string.use_system_language_desc),
                    appPreferences.useSystemLanguage) { useSystem ->
                    // Turning the switch off starts from the language the system already uses.
                    val tag = if (useSystem) appPreferences.selectedLanguage
                        else AppLanguage.closestTo(Resources.getSystem().configuration.locales[0]).tag
                    onAppLanguageChange(useSystem, tag)
                }
                if (!appPreferences.useSystemLanguage) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        SelectableOutlinedTextField(
                            value = AppLanguage.fromTag(appPreferences.selectedLanguage).nativeName,
                            onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.app_language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("app-language")
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            AppLanguage.entries.forEach { language ->
                                DropdownMenuItem(text = { Text(language.nativeName) }, onClick = {
                                    onAppLanguageChange(false, language.tag)
                                    expanded = false
                                })
                            }
                        }
                    }
                }
            }
        }
        item {
            var wakelockExpanded by remember { mutableStateOf(false) }
            val wakelockLabels = mapOf(
                ScreenWakelockMode.EXCEPT_TOOL_CALLS to stringResource(R.string.screen_wakelock_except_tool_calls),
                ScreenWakelockMode.ALL to stringResource(R.string.screen_wakelock_all),
                ScreenWakelockMode.NONE to stringResource(R.string.screen_wakelock_none)
            )
            SettingsSection(stringResource(R.string.screen_wakelock_title)) {
                Text(stringResource(R.string.screen_wakelock_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(wakelockExpanded, { wakelockExpanded = !wakelockExpanded }) {
                    SelectableOutlinedTextField(
                        value = wakelockLabels.getValue(appPreferences.screenWakelockMode),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.screen_wakelock_title)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(wakelockExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(wakelockExpanded, { wakelockExpanded = false }) {
                        wakelockLabels.forEach { (mode, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onScreenWakelockModeChange(mode)
                                wakelockExpanded = false
                            })
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_conversations)) {
                PreferenceToggle(stringResource(R.string.settings_continue_last_chat), stringResource(R.string.settings_continue_last_chat_description),
                    appPreferences.continueLastConversation, onContinueLastConversationChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PreferenceToggle(stringResource(R.string.settings_persist_selected_model), stringResource(R.string.settings_persist_selected_model_description),
                    appPreferences.persistSelectedModel, onPersistSelectedModelChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PreferenceToggle(stringResource(R.string.settings_auto_scroll), stringResource(R.string.settings_auto_scroll_description),
                    appPreferences.autoScroll, onAutoScrollChange)
            }
        }
        item {
            var strategyExpanded by remember { mutableStateOf(false) }
            val strategyLabels = mapOf(
                ToolCallStrategy.SEQUENTIAL to stringResource(R.string.tool_call_strategy_sequential),
                ToolCallStrategy.PARALLEL to stringResource(R.string.tool_call_strategy_parallel)
            )
            SettingsSection(
                title = stringResource(R.string.tool_call_options),
                description = stringResource(R.string.tool_call_strategy_description)
            ) {
                ExposedDropdownMenuBox(strategyExpanded, { strategyExpanded = !strategyExpanded }) {
                    SelectableOutlinedTextField(
                        value = strategyLabels.getValue(appPreferences.toolCallStrategy),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.tool_call_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("tool-call-strategy")
                    )
                    ExposedDropdownMenu(strategyExpanded, { strategyExpanded = false }) {
                        strategyLabels.forEach { (strategy, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                onToolCallStrategyChange(strategy)
                                strategyExpanded = false
                            })
                        }
                    }
                }
                if (appPreferences.toolCallStrategy == ToolCallStrategy.PARALLEL) {
                    var limit by remember(appPreferences.toolCallConcurrentLimit) { mutableStateOf(appPreferences.toolCallConcurrentLimit.toString()) }
                    val parsed = limit.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_CALL_CONCURRENT_LIMIT }
                    Text(
                        stringResource(R.string.tool_call_concurrent_limit_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectableOutlinedTextField(
                        value = limit,
                        onValueChange = { text ->
                            if (text.length <= 2 && text.all(Char::isDigit)) {
                                limit = text
                                text.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_CALL_CONCURRENT_LIMIT }?.let(onToolCallConcurrentLimitChange)
                            }
                        },
                        label = { Text(stringResource(R.string.tool_call_concurrent_limit)) },
                        supportingText = { Text(stringResource(
                            if (parsed == null) R.string.tool_call_concurrent_limit_invalid else R.string.tool_call_concurrent_limit_default,
                            if (parsed == null) MAX_TOOL_CALL_CONCURRENT_LIMIT else DEFAULT_TOOL_CALL_CONCURRENT_LIMIT
                        )) },
                        isError = parsed == null, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("tool-call-concurrent-limit")
                    )
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_tool_calls)) {
                PreferenceToggle(stringResource(R.string.settings_limit_tool_rounds), stringResource(R.string.settings_limit_tool_rounds_description),
                    appPreferences.toolRoundLimitEnabled, onToolRoundLimitEnabledChange)
                if (appPreferences.toolRoundLimitEnabled) {
                    var rounds by remember(appPreferences.toolRoundLimit) { mutableStateOf(appPreferences.toolRoundLimit.toString()) }
                    val parsed = rounds.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_ROUND_LIMIT }
                    SelectableOutlinedTextField(
                        value = rounds,
                        onValueChange = { text ->
                            if (text.length <= 4 && text.all(Char::isDigit)) {
                                rounds = text
                                text.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_ROUND_LIMIT }?.let(onToolRoundLimitChange)
                            }
                        },
                        label = { Text(stringResource(R.string.settings_maximum_tool_rounds)) },
                        supportingText = { Text(stringResource(
                            if (parsed == null) R.string.settings_tool_rounds_invalid else R.string.settings_tool_rounds_default,
                            if (parsed == null) MAX_TOOL_ROUND_LIMIT else DEFAULT_TOOL_ROUND_LIMIT
                        )) },
                        isError = parsed == null, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("tool-round-limit")
                    )
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_feedback)) {
                PreferenceToggle(stringResource(R.string.settings_haptic_vibration), stringResource(R.string.settings_haptic_vibration_description),
                    appPreferences.enableVibration, onEnableVibrationChange)
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_text_to_speech)) {
                PreferenceToggle(stringResource(R.string.settings_read_code_blocks), stringResource(R.string.settings_read_code_blocks_description),
                    appPreferences.ttsReadCodeBlocks, onTtsReadCodeBlocksChange)
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_content)) {
                Text(stringResource(R.string.settings_latex_equation_rendering), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_latex_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "ON" to R.string.settings_latex_always_on,
                        "OFF" to R.string.settings_latex_disabled,
                        "AUTO" to R.string.settings_latex_auto_detect
                    ).forEach { (mode, labelRes) ->
                        FilterChip(selected = appPreferences.latexMode == mode, onClick = { onLatexModeChange(mode) },
                            label = { Text(stringResource(labelRes)) })
                    }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_developer)) {
                PreferenceToggle(stringResource(R.string.settings_debug_logs), stringResource(R.string.settings_debug_logs_description),
                    appPreferences.debugMode, onDebugModeChange)
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    appPreferences: AppPreferences,
    onThemeChange: (String) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onColorSchemeChange: (String) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit,
    onWordWrapModeChange: (WordWrapMode) -> Unit,
    onWordWrapColumnChange: (Int) -> Unit,
    onCodePreviewEnabledChange: (Boolean) -> Unit,
    onCodeBlockDisplayModeChange: (CodeBlockDisplayMode) -> Unit,
    onCodeBlockMaxLinesChange: (Int) -> Unit,
    onCodeBlockIncludeUndefinedChange: (Boolean) -> Unit,
    onMessageFontSizeChange: (Int) -> Unit,
    onMessageFontSizeCustomChange: (Boolean) -> Unit,
    onMessageFontFamilyChange: (MessageFontFamily) -> Unit,
    onContinueLastConversationChange: (Boolean) -> Unit,
    onPersistSelectedModelChange: (Boolean) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onEnableVibrationChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onLatexModeChange: (String) -> Unit,
    onTtsReadCodeBlocksChange: (Boolean) -> Unit,
    onToolRoundLimitEnabledChange: (Boolean) -> Unit,
    onToolRoundLimitChange: (Int) -> Unit,
    onToolCallStrategyChange: (ToolCallStrategy) -> Unit,
    onToolCallConcurrentLimitChange: (Int) -> Unit,
    onScreenWakelockModeChange: (ScreenWakelockMode) -> Unit,
    onAppLanguageChange: (Boolean, String) -> Unit
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pager.currentPage) {
            Tab(
                selected = pager.currentPage == 0,
                onClick = { coroutineScope.launch { pager.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.settings_tab_preferences)) }
            )
            Tab(
                selected = pager.currentPage == 1,
                onClick = { coroutineScope.launch { pager.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.settings_tab_appearances)) }
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> PreferencesSettingsView(
                    appPreferences = appPreferences,
                    onContinueLastConversationChange = onContinueLastConversationChange,
                    onPersistSelectedModelChange = onPersistSelectedModelChange,
                    onAutoScrollChange = onAutoScrollChange,
                    onEnableVibrationChange = onEnableVibrationChange,
                    onDebugModeChange = onDebugModeChange,
                    onLatexModeChange = onLatexModeChange,
                    onTtsReadCodeBlocksChange = onTtsReadCodeBlocksChange,
                    onToolRoundLimitEnabledChange = onToolRoundLimitEnabledChange,
                    onToolRoundLimitChange = onToolRoundLimitChange,
                    onToolCallStrategyChange = onToolCallStrategyChange,
                    onToolCallConcurrentLimitChange = onToolCallConcurrentLimitChange,
                    onScreenWakelockModeChange = onScreenWakelockModeChange,
                    onAppLanguageChange = onAppLanguageChange
                )
                else -> AppearanceSettingsView(
                    appPreferences = appPreferences,
                    onThemeChange = onThemeChange,
                    onAmoledChange = onAmoledChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onColorSchemeChange = onColorSchemeChange,
                    onHideStatusBarChange = onHideStatusBarChange,
                    onWordWrapModeChange = onWordWrapModeChange,
                    onWordWrapColumnChange = onWordWrapColumnChange,
                    onCodePreviewEnabledChange = onCodePreviewEnabledChange,
                    onCodeBlockDisplayModeChange = onCodeBlockDisplayModeChange,
                    onCodeBlockMaxLinesChange = onCodeBlockMaxLinesChange,
                    onCodeBlockIncludeUndefinedChange = onCodeBlockIncludeUndefinedChange,
                    onMessageFontSizeChange = onMessageFontSizeChange,
                    onMessageFontSizeCustomChange = onMessageFontSizeCustomChange,
                    onMessageFontFamilyChange = onMessageFontFamilyChange
                )
            }
        }
    }
}
