package org.starfall.generative.features.settings.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import org.starfall.generative.shared.ui.menus.ExposedDropdownMenu
import org.starfall.generative.shared.ui.menus.DropdownMenuItem
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import org.starfall.generative.shared.ui.layout.appTabRowColor
import org.starfall.generative.shared.ui.layout.appTabRowBorder
import org.starfall.generative.R
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import org.starfall.generative.shared.model.logging.DebugLogCategory
import org.starfall.generative.shared.storage.preferences.AppLanguage
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.generative.shared.storage.preferences.DEFAULT_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.generative.shared.storage.preferences.DEFAULT_TOOL_ROUND_LIMIT
import org.starfall.generative.shared.storage.preferences.MAX_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.generative.shared.storage.preferences.MAX_TOOL_ROUND_LIMIT
import org.starfall.generative.shared.storage.preferences.McpAttachmentSettings
import org.starfall.generative.shared.storage.preferences.MessageFontFamily
import org.starfall.generative.shared.storage.preferences.ScreenWakelockMode
import org.starfall.generative.shared.storage.preferences.ToolCallStrategy
import org.starfall.generative.shared.storage.preferences.WordWrapMode
import org.starfall.generative.shared.ui.dialogs.AlertDialog
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.OutlinedTextField
import org.starfall.generative.shared.ui.layout.windowHeightIn

@Composable
fun PreferenceToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
    onHideStatusBarChange: (Boolean) -> Unit = {},
    onWordWrapModeChange: (WordWrapMode) -> Unit = {},
    onWordWrapColumnChange: (Int) -> Unit = {},
    onCodePreviewEnabledChange: (Boolean) -> Unit = {},
    onCodeBlockDisplayModeChange: (CodeBlockDisplayMode) -> Unit = {},
    onCodeBlockMaxLinesChange: (Int) -> Unit = {},
    onCodeBlockIncludeUndefinedChange: (Boolean) -> Unit = {},
    onMessageFontSizeChange: (Int) -> Unit = {},
    onMessageFontSizeCustomChange: (Boolean) -> Unit = {},
    onMessageFontFamilyChange: (MessageFontFamily) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp)
) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsSection(stringResource(R.string.settings_theme)) {
                Text(stringResource(R.string.settings_theme_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                        FilterChip(selected = appPreferences.themeMode == mode, onClick = { onThemeChange(mode) },
                            label = { Text(stringResource(when (mode) {
                                "SYSTEM" -> R.string.common_system
                                "LIGHT" -> R.string.settings_theme_light
                                else -> R.string.settings_theme_dark
                            })) }, modifier = Modifier.weight(1f))
                    }
                }
                PreferenceToggle(stringResource(R.string.settings_amoled), stringResource(R.string.settings_amoled_description), appPreferences.useAmoled, onAmoledChange)
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
    onDebugLogCategoriesChange: (Set<DebugLogCategory>) -> Unit = {},
    onOpenLauncherDragTest: () -> Unit = {},
    onLatexModeChange: (String) -> Unit,
    onTtsReadCodeBlocksChange: (Boolean) -> Unit = {},
    onToolRoundLimitEnabledChange: (Boolean) -> Unit = {},
    onToolRoundLimitChange: (Int) -> Unit = {},
    onToolCallStrategyChange: (ToolCallStrategy) -> Unit = {},
    onToolCallConcurrentLimitChange: (Int) -> Unit = {},
    onMcpAttachmentsChange: (McpAttachmentSettings) -> Unit = {},
    onScreenWakelockModeChange: (ScreenWakelockMode) -> Unit = {},
    onAppLanguageChange: (useSystem: Boolean, tag: String) -> Unit = { _, _ -> },
    onAutoSummaryEnabledChange: (Boolean) -> Unit = {},
    onSummarySuggestionEnabledChange: (Boolean) -> Unit = {},
    onAutoSummaryPercentChange: (Int) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp)
) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    val selectedLanguage = AppLanguage.fromTag(appPreferences.selectedLanguage)
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        OutlinedTextField(
                            value = stringResource(selectedLanguage.nameRes),
                            onValueChange = {}, readOnly = true,
                            label = { Text(stringResource(R.string.app_language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).testTag("app-language")
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            AppLanguage.entries.forEach { language ->
                                DropdownMenuItem(text = {
                                    // The name in the app language leads, the native name supports it.
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            stringResource(language.nameRes),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            language.nativeName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }, onClick = {
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
                ScreenWakelockMode.EXCEPT_TOOL_CALLS to stringResource(R.string.common_auto),
                ScreenWakelockMode.ALL to stringResource(R.string.common_on),
                ScreenWakelockMode.NONE to stringResource(R.string.common_off)
            )
            SettingsSection(stringResource(R.string.screen_wakelock_title)) {
                Text(stringResource(R.string.screen_wakelock_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuBox(wakelockExpanded, { wakelockExpanded = !wakelockExpanded }) {
                    OutlinedTextField(
                        value = wakelockLabels.getValue(appPreferences.screenWakelockMode),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.screen_wakelock_title)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(wakelockExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
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
            SettingsSection(stringResource(R.string.settings_mcp_tools)) {
                Text(stringResource(R.string.tool_call_options), style = MaterialTheme.typography.titleMedium)
                var strategyExpanded by remember { mutableStateOf(false) }
                val strategyLabels = mapOf(
                    ToolCallStrategy.SEQUENTIAL to stringResource(R.string.tool_call_strategy_sequential),
                    ToolCallStrategy.PARALLEL to stringResource(R.string.tool_call_strategy_parallel)
                )
                ExposedDropdownMenuBox(strategyExpanded, { strategyExpanded = !strategyExpanded }) {
                    OutlinedTextField(
                        value = strategyLabels.getValue(appPreferences.toolCallStrategy),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.tool_call_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).testTag("tool-call-strategy")
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
                    var limit by remember(appPreferences.toolCallConcurrentLimit) {
                        mutableStateOf(appPreferences.toolCallConcurrentLimit.takeUnless {
                            it == DEFAULT_TOOL_CALL_CONCURRENT_LIMIT
                        }?.toString().orEmpty())
                    }
                    val parsed = limit.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_CALL_CONCURRENT_LIMIT }
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { text ->
                            if (text.length <= 2 && text.all(Char::isDigit)) {
                                limit = text
                                if (text.isBlank()) onToolCallConcurrentLimitChange(DEFAULT_TOOL_CALL_CONCURRENT_LIMIT)
                                else text.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_CALL_CONCURRENT_LIMIT }?.let(onToolCallConcurrentLimitChange)
                            }
                        },
                        label = { Text(stringResource(R.string.tool_call_concurrent_limit)) },
                        placeholder = { Text(stringResource(R.string.common_default_value_hint, DEFAULT_TOOL_CALL_CONCURRENT_LIMIT)) },
                        isError = limit.isNotBlank() && parsed == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("tool-call-concurrent-limit")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PreferenceToggle(stringResource(R.string.settings_limit_tool_rounds),
                    stringResource(R.string.settings_limit_tool_rounds_description),
                    appPreferences.toolRoundLimitEnabled, onToolRoundLimitEnabledChange)
                if (appPreferences.toolRoundLimitEnabled) {
                    var rounds by remember(appPreferences.toolRoundLimit) {
                        mutableStateOf(appPreferences.toolRoundLimit.takeUnless {
                            it == DEFAULT_TOOL_ROUND_LIMIT
                        }?.toString().orEmpty())
                    }
                    val parsed = rounds.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_ROUND_LIMIT }
                    OutlinedTextField(
                        value = rounds,
                        onValueChange = { text ->
                            if (text.length <= 4 && text.all(Char::isDigit)) {
                                rounds = text
                                if (text.isBlank()) onToolRoundLimitChange(DEFAULT_TOOL_ROUND_LIMIT)
                                else text.toIntOrNull()?.takeIf { it in 1..MAX_TOOL_ROUND_LIMIT }?.let(onToolRoundLimitChange)
                            }
                        },
                        label = { Text(stringResource(R.string.settings_maximum_tool_rounds)) },
                        placeholder = { Text(stringResource(R.string.common_default_value_hint, DEFAULT_TOOL_ROUND_LIMIT)) },
                        isError = rounds.isNotBlank() && parsed == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("tool-round-limit")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                McpAttachmentsPreferences(appPreferences.mcpAttachments, onMcpAttachmentsChange)
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
            DeveloperSettingsSection(appPreferences, onDebugModeChange, onDebugLogCategoriesChange, onOpenLauncherDragTest)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppSettings(
    appPreferences: AppPreferences,
    onThemeChange: (String) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
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
    onDebugLogCategoriesChange: (Set<DebugLogCategory>) -> Unit = {},
    onOpenLauncherDragTest: () -> Unit = {},
    onLatexModeChange: (String) -> Unit,
    onTtsReadCodeBlocksChange: (Boolean) -> Unit,
    onToolRoundLimitEnabledChange: (Boolean) -> Unit,
    onToolRoundLimitChange: (Int) -> Unit,
    onToolCallStrategyChange: (ToolCallStrategy) -> Unit,
    onToolCallConcurrentLimitChange: (Int) -> Unit,
    onMcpAttachmentsChange: (McpAttachmentSettings) -> Unit = {},
    onScreenWakelockModeChange: (ScreenWakelockMode) -> Unit,
    onAppLanguageChange: (Boolean, String) -> Unit,
    onAutoSummaryEnabledChange: (Boolean) -> Unit = {},
    onSummarySuggestionEnabledChange: (Boolean) -> Unit = {},
    onAutoSummaryPercentChange: (Int) -> Unit = {},
    scaffoldPadding: PaddingValues = PaddingValues()
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    // Tabs stay below the toolbar. The pages themselves scroll under the navigation bar.
    val pagePadding = scrollableScaffoldPadding(
        PaddingValues(
            start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = scaffoldPadding.calculateEndPadding(LocalLayoutDirection.current),
            bottom = scaffoldPadding.calculateBottomPadding()
        ), horizontal = 16.dp, top = 8.dp, bottom = 24.dp
    )
    Column(Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding() + 8.dp)) {
        PrimaryTabRow(
            selectedTabIndex = pager.currentPage,
            containerColor = appTabRowColor(),
            modifier = Modifier.padding(
                start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = scaffoldPadding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp
            ).then(appTabRowBorder())
        ) {
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
        Spacer(Modifier.height(12.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> PreferencesSettingsView(
                    contentPadding = pagePadding,
                    appPreferences = appPreferences,
                    onContinueLastConversationChange = onContinueLastConversationChange,
                    onPersistSelectedModelChange = onPersistSelectedModelChange,
                    onAutoScrollChange = onAutoScrollChange,
                    onEnableVibrationChange = onEnableVibrationChange,
                    onDebugModeChange = onDebugModeChange,
                    onDebugLogCategoriesChange = onDebugLogCategoriesChange,
                    onOpenLauncherDragTest = onOpenLauncherDragTest,
                    onLatexModeChange = onLatexModeChange,
                    onTtsReadCodeBlocksChange = onTtsReadCodeBlocksChange,
                    onToolRoundLimitEnabledChange = onToolRoundLimitEnabledChange,
                    onToolRoundLimitChange = onToolRoundLimitChange,
                    onToolCallStrategyChange = onToolCallStrategyChange,
                    onToolCallConcurrentLimitChange = onToolCallConcurrentLimitChange,
                    onMcpAttachmentsChange = onMcpAttachmentsChange,
                    onScreenWakelockModeChange = onScreenWakelockModeChange,
                    onAppLanguageChange = onAppLanguageChange,
                    onAutoSummaryEnabledChange = onAutoSummaryEnabledChange,
                    onSummarySuggestionEnabledChange = onSummarySuggestionEnabledChange,
                    onAutoSummaryPercentChange = onAutoSummaryPercentChange
                )
                else -> AppearanceSettingsView(
                    contentPadding = pagePadding,
                    appPreferences = appPreferences,
                    onThemeChange = onThemeChange,
                    onAmoledChange = onAmoledChange,
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
