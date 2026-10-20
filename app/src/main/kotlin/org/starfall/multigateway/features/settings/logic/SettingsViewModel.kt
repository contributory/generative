package org.starfall.multigateway.features.settings.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.starfall.multigateway.shared.model.SidebarOrganization
import org.starfall.multigateway.shared.model.logging.DebugLogCategory
import org.starfall.multigateway.shared.storage.LocalWriteErrors
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.storage.preferences.AppPreferencesRepository
import org.starfall.multigateway.shared.storage.preferences.CodeBlockDisplayMode
import org.starfall.multigateway.shared.storage.preferences.MessageFontFamily
import org.starfall.multigateway.shared.storage.preferences.ScreenWakelockMode
import org.starfall.multigateway.shared.storage.preferences.ToolCallStrategy
import org.starfall.multigateway.shared.storage.preferences.WordWrapMode

class SettingsViewModel(
    private val repository: AppPreferencesRepository
) : ViewModel() {
    val preferences = repository.appPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    fun updateSidebar(transform: (SidebarOrganization) -> SidebarOrganization) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.updateSidebar(transform) }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setThemeMode(value) }
    }
    fun setUseAmoled(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setUseAmoled(value) }
    }
    fun setUseDynamicColor(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setUseDynamicColor(value) }
    }
    fun setColorSchemeName(value: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setColorSchemeName(value) }
    }
    fun setContinueLastConversation(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setContinueLastConversation(value) }
    }
    fun setAutoScroll(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setAutoScroll(value) }
    }
    fun setTtsReadCodeBlocks(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setTtsReadCodeBlocks(value) }
    }
    fun setProvidersCollapsedSections(value: Set<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setProvidersCollapsedSections(value) }
    }
    fun setModelPickerCollapsedGroups(value: Set<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setModelPickerCollapsedGroups(value) }
    }
    fun setModelPickerCollapsedProviders(value: Set<String>) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setModelPickerCollapsedProviders(value) }
    }
    fun setModelPickerSelectedTab(value: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setModelPickerSelectedTab(value) }
    }
    fun setEnableVibration(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setEnableVibration(value) }
    }
    fun setHideStatusBar(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setHideStatusBar(value) }
    }
    fun setDebugMode(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setDebugMode(value) }
    }
    fun setDebugLogCategories(value: Set<DebugLogCategory>) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setDebugLogCategories(value) }
    }
    fun setLatexMode(value: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setLatexMode(value) }
    }
    fun setWordWrapMode(value: WordWrapMode) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setWordWrapMode(value) }
    }
    fun setWordWrapColumn(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setWordWrapColumn(value) }
    }
    fun setCodePreviewEnabled(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setCodePreviewEnabled(value) }
    }
    fun setCodeBlockDisplayMode(value: CodeBlockDisplayMode) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setCodeBlockDisplayMode(value) }
    }
    fun setCodeBlockMaxLines(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setCodeBlockMaxLines(value) }
    }
    fun setCodeBlockIncludeUndefined(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setCodeBlockIncludeUndefined(value) }
    }
    fun setMessageFontSize(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setMessageFontSize(value) }
    }
    fun setMessageFontSizeCustom(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setMessageFontSizeCustom(value) }
    }
    fun setToolRoundLimitEnabled(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setToolRoundLimitEnabled(value) }
    }
    fun setToolRoundLimit(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setToolRoundLimit(value) }
    }
    fun setToolCallStrategy(value: ToolCallStrategy) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setToolCallStrategy(value) }
    }
    fun setToolCallConcurrentLimit(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setToolCallConcurrentLimit(value) }
    }
    fun setAutoSummaryEnabled(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setAutoSummaryEnabled(value) }
    }
    fun setSummarySuggestionEnabled(value: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setSummarySuggestionEnabled(value) }
    }
    fun setAutoSummaryPercent(value: Int) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setAutoSummaryPercent(value) }
    }
    fun setScreenWakelockMode(value: ScreenWakelockMode) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setScreenWakelockMode(value) }
    }
    fun setAppLanguage(useSystem: Boolean, tag: String) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setAppLanguage(useSystem, tag) }
    }
    fun setMessageFontFamily(value: MessageFontFamily) {
        viewModelScope.launch(LocalWriteErrors.handler) { repository.setMessageFontFamily(value) }
    }

}
