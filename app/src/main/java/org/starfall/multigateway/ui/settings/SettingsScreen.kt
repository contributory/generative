package org.starfall.multigateway.ui.settings

import org.starfall.multigateway.ui.icons.FluentIcon

import org.starfall.multigateway.ui.components.AppAlertDialog as AlertDialog
import org.starfall.multigateway.data.service.GitHubReleaseInfo
import org.starfall.multigateway.data.service.fetchLatestGitHubRelease
import org.starfall.multigateway.ui.components.windowHeightIn

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
import org.starfall.multigateway.data.local.preferences.WordWrapMode
import org.starfall.multigateway.data.local.preferences.MessageFontFamily
import org.starfall.multigateway.data.local.preferences.CodeBlockDisplayMode
import org.starfall.multigateway.data.local.preferences.AppPreferences
import org.starfall.multigateway.data.local.preferences.AppLanguage
import org.starfall.multigateway.data.local.preferences.ScreenWakelockMode
import org.starfall.multigateway.data.local.preferences.ToolCallStrategy
import org.starfall.multigateway.R
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import org.starfall.multigateway.data.local.preferences.DEFAULT_TOOL_ROUND_LIMIT
import org.starfall.multigateway.data.local.preferences.MAX_TOOL_ROUND_LIMIT
import org.starfall.multigateway.data.local.preferences.DEFAULT_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.multigateway.data.local.preferences.MAX_TOOL_CALL_CONCURRENT_LIMIT
import org.starfall.multigateway.data.service.JsonExportKind
import org.starfall.multigateway.ui.components.SelectableOutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import org.starfall.multigateway.ui.navigation.SlideScreenContent
import org.starfall.multigateway.ui.theme.ThemePresets
import org.starfall.multigateway.ui.icons.Icons

enum class SettingsCategory(val titleRes: Int, val subtitleRes: Int, val icon: Int) {
    APP_SETTINGS(R.string.settings_category_app_settings, R.string.settings_category_app_settings_description, Icons.Outlined.Tune),
    ICONS(R.string.icon_settings_title, R.string.icon_settings_description, Icons.Outlined.Image),
    USER_DATA(R.string.settings_category_app_data, R.string.settings_category_app_data_description, Icons.Outlined.ManageHistory),
    ABOUT(R.string.settings_category_about, R.string.settings_category_about_description, Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    conversationCount: Int,
    providerCount: Int,
    onThemeChange: (String) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onColorSchemeChange: (String) -> Unit,
    onContinueLastConversationChange: (Boolean) -> Unit,
    onPersistSelectedModelChange: (Boolean) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onEnableVibrationChange: (Boolean) -> Unit,
    onHideStatusBarChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onLatexModeChange: (String) -> Unit,
    onClearAllConversations: () -> Unit,
    onResetAllData: () -> Unit,
    onBack: () -> Unit,
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToSpeech: () -> Unit = {},
    onNavigateToSystemTools: () -> Unit = {},
    onNavigateToStorage: () -> Unit = {},
    onNavigateToRemoteMachines: () -> Unit = {},
    onExportJson: (JsonExportKind) -> Unit = {},
    onImportJson: (JsonExportKind) -> Unit = {},
    onTtsReadCodeBlocksChange: (Boolean) -> Unit = {},
    onWordWrapModeChange: (WordWrapMode) -> Unit = {},
    onWordWrapColumnChange: (Int) -> Unit = {},
    onCodePreviewEnabledChange: (Boolean) -> Unit = {},
    onCodeBlockDisplayModeChange: (CodeBlockDisplayMode) -> Unit = {},
    onCodeBlockMaxLinesChange: (Int) -> Unit = {},
    onCodeBlockIncludeUndefinedChange: (Boolean) -> Unit = {},
    onMessageFontSizeChange: (Int) -> Unit = {},
    onMessageFontSizeCustomChange: (Boolean) -> Unit = {},
    onMessageFontFamilyChange: (MessageFontFamily) -> Unit = {},
    onToolRoundLimitEnabledChange: (Boolean) -> Unit = {},
    onToolRoundLimitChange: (Int) -> Unit = {},
    onToolCallStrategyChange: (ToolCallStrategy) -> Unit = {},
    onToolCallConcurrentLimitChange: (Int) -> Unit = {},
    onScreenWakelockModeChange: (ScreenWakelockMode) -> Unit = {},
    onAppLanguageChange: (useSystem: Boolean, tag: String) -> Unit = { _, _ -> }
) {
    var selectedCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedCategory?.let { stringResource(it.titleRes) } ?: stringResource(R.string.settings_general),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedCategory != null) {
                                selectedCategory = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SlideScreenContent(editor = selectedCategory, label = stringResource(R.string.settings_category)) { category ->
                when (category) {
                    null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                SettingsGroupHeading(stringResource(R.string.settings_group_configuration))
                            }
                            item { SettingsNavigationItem(stringResource(R.string.settings_default_and_tools), stringResource(R.string.settings_default_models_description), Icons.Outlined.Build, onNavigateToSystemTools) }
                            item { SettingsNavigationItem(stringResource(R.string.settings_providers), stringResource(R.string.settings_providers_description), Icons.Outlined.CloudQueue, onNavigateToProviders) }
                            item { SettingsNavigationItem(stringResource(R.string.settings_mcp_manage), stringResource(R.string.settings_mcp_manage_description), Icons.Outlined.Extension, onNavigateToMcp) }
                            item { SettingsNavigationItem(stringResource(R.string.settings_speech_services), stringResource(R.string.settings_speech_services_description), Icons.Outlined.RecordVoiceOver, onNavigateToSpeech) }
                            item { SettingsNavigationItem(stringResource(R.string.settings_remote_machines), stringResource(R.string.settings_remote_machines_description), Icons.Outlined.Computer, onNavigateToRemoteMachines) }
                            item { SettingsNavigationItem(stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_description), Icons.Outlined.FolderOpen, onNavigateToStorage) }
                            item {
                                Spacer(Modifier.height(8.dp))
                                SettingsGroupHeading(stringResource(R.string.settings_group_system))
                            }
                            items(SettingsCategory.entries.size) { index ->
                                val category = SettingsCategory.entries[index]
                                SettingsNavigationItem(
                                    title = stringResource(category.titleRes),
                                    subtitle = stringResource(category.subtitleRes),
                                    icon = category.icon,
                                    onClick = { selectedCategory = category }
                                )
                            }
                        }
                    }

                    SettingsCategory.APP_SETTINGS -> {
                        AppSettingsView(
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
                            onMessageFontFamilyChange = onMessageFontFamilyChange,
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
                    }

                    SettingsCategory.ICONS -> IconSettingsView()

                    SettingsCategory.USER_DATA -> {
                        UserDataSettingsView(
                            conversationCount = conversationCount,
                            providerCount = providerCount,
                            onClearAllConversations = onClearAllConversations,
                            onResetAllData = onResetAllData,
                            onExportJson = onExportJson,
                            onImportJson = onImportJson
                        )
                    }

                    SettingsCategory.ABOUT -> {
                        AboutSettingsView()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupHeading(title: String) {
    Text(title, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsView(
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
            SettingsSection(stringResource(R.string.tool_call_options)) {
                ExposedDropdownMenuBox(strategyExpanded, { strategyExpanded = !strategyExpanded }) {
                    SelectableOutlinedTextField(
                        value = strategyLabels.getValue(appPreferences.toolCallStrategy),
                        onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.tool_call_strategy)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("tool-call-strategy")
                    )
                    Text(
                        stringResource(R.string.tool_call_strategy_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun UserDataSettingsView(
    conversationCount: Int,
    providerCount: Int,
    onClearAllConversations: () -> Unit,
    onResetAllData: () -> Unit,
    onExportJson: (JsonExportKind) -> Unit = {},
    onImportJson: (JsonExportKind) -> Unit = {}
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsSection(stringResource(R.string.settings_data_overview)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DataStatItem(stringResource(R.string.settings_chats), conversationCount.toString())
                    DataStatItem(stringResource(R.string.settings_providers), providerCount.toString())
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_manage_data)) {
                Text(stringResource(R.string.settings_manage_data_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showClearConfirm = true }, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()) {
                    FluentIcon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_clear_all_chat_history))
                }
                OutlinedButton(onClick = { showResetConfirm = true }, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()) {
                    FluentIcon(Icons.Outlined.Warning, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_reset_all_application_data))
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_export_data)) {
                Text(
                    stringResource(R.string.settings_export_data_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExportDataButton(stringResource(R.string.settings_export_providers_json), Icons.Outlined.CloudQueue) {
                    onExportJson(JsonExportKind.PROVIDERS)
                }
                ExportDataButton(stringResource(R.string.settings_export_mcp_json), Icons.Outlined.Extension) {
                    onExportJson(JsonExportKind.MCP_SERVERS)
                }
                ExportDataButton(stringResource(R.string.settings_export_chats_json), Icons.Outlined.Chat) {
                    onExportJson(JsonExportKind.CONVERSATIONS)
                }
                Spacer(Modifier.height(8.dp))
                ExportDataButton(stringResource(R.string.settings_import_providers_json), Icons.Outlined.FileOpen) {
                    onImportJson(JsonExportKind.PROVIDERS)
                }
                ExportDataButton(stringResource(R.string.settings_import_mcp_json), Icons.Outlined.FileOpen) {
                    onImportJson(JsonExportKind.MCP_SERVERS)
                }
                ExportDataButton(stringResource(R.string.settings_import_chats_json), Icons.Outlined.FileOpen) {
                    onImportJson(JsonExportKind.CONVERSATIONS)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_all_chats_title)) },
            text = { Text(stringResource(R.string.settings_clear_all_chats_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllConversations()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.settings_factory_reset_title)) },
            text = { Text(stringResource(R.string.settings_factory_reset_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_reset_everything))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun ExportDataButton(title: String, icon: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FluentIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}

@Composable
fun DataStatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

private fun formatFileSize(context: android.content.Context, bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return context.getString(R.string.settings_file_size_mb, mb)
}

private fun compareVersions(current: String, latestTag: String): Int {
    fun parts(value: String): List<Int> = value
        .removePrefix("v")
        .split('.')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val a = parts(current)
    val b = parts(latestTag)
    val size = maxOf(a.size, b.size)
    repeat(size) { index ->
        val av = a.getOrElse(index) { 0 }
        val bv = b.getOrElse(index) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

@Composable
fun UpdateSettingsView() {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val currentVersion = packageInfo.versionName.orEmpty()
    var isChecking by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        if (refreshKey == 0) return@LaunchedEffect
        isChecking = true
        errorMessage = null
        runCatching { fetchLatestGitHubRelease() }
            .onSuccess { latestRelease = it }
            .onFailure { errorMessage = it.localizedMessage ?: context.getString(R.string.settings_update_error) }
        isChecking = false
    }

    val release = latestRelease
    val updateAvailable = release?.let { compareVersions(currentVersion, it.tagName) < 0 } == true

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_software_update), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    IconButton(
                        onClick = { refreshKey++ },
                        enabled = !isChecking,
                        modifier = Modifier.size(48.dp)
                    ) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.settings_check_for_updates),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                when {
                    isChecking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.settings_checking_releases), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    errorMessage != null -> {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.settings_try_again)) }
                    }
                    release != null -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(release.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (updateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                     text = stringResource(
                                         if (updateAvailable) R.string.settings_update_available else R.string.settings_up_to_date,
                                         release.tagName
                                     ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (updateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Release Notes Preview
                        if (release.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_release_notes),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowHeightIn(maxFraction = 0.3f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    org.starfall.multigateway.ui.chat.MarkdownRenderer(content = release.body)
                                }
                            }
                        }

                        if (updateAvailable) {
                            // APK Downloads List
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_download_apk_variants),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )

                            if (release.apkAssets.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    release.apkAssets.forEach { apkAsset ->
                                         val formattedSize = formatFileSize(context, apkAsset.sizeBytes)
                                        OutlinedButton(
                                            onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkAsset.downloadUrl)))
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    FluentIcon(
                                                        imageVector = Icons.Outlined.FileDownload,
                                                         contentDescription = stringResource(R.string.settings_download_apk),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = apkAsset.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (formattedSize.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = formattedSize,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FluentIcon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                     Text(stringResource(R.string.settings_open_github_release))
                                }
                            }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.settings_find_latest_release),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = { refreshKey++ }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            FluentIcon(Icons.Outlined.SystemUpdateAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_check_for_updates))
                        }
                    }
                }
            }
        }
}

@Composable
fun AboutSettingsView() {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName.orEmpty()
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(60.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MultiGateway", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                     Text(stringResource(R.string.settings_version, versionName, versionCode), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
             Text(stringResource(R.string.settings_universal_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UpdateSettingsView()
        SettingsNavigationItem(stringResource(R.string.settings_source_code), "starfall-org/multigateway", Icons.Outlined.Code) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/starfall-org/multigateway")))
        }
        SettingsSection(stringResource(R.string.settings_license)) {
            Text(stringResource(R.string.settings_license_name), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
