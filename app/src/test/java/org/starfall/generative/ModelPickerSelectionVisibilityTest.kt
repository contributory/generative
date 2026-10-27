package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.model.*
import org.starfall.generative.features.providers.shared.ui.*
import org.starfall.generative.shared.ui.model.*
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderConfiguration
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.model.ModelPickerItem
import org.starfall.generative.shared.ui.model.computeModelPickerItems
import org.starfall.generative.shared.ui.model.computeModelPickerTabs
import org.starfall.generative.shared.model.language.withConversationReasoning

class ModelPickerSelectionVisibilityTest {
    private val group = ProviderGroup("folder", "Work")
    private val selected = ModelProvider("selected", "Selected provider", ModelApiType.OPENAI,
        baseUrl = "", groupId = group.id,
        config = ProviderConfiguration(modelIds = listOf("alpha", "beta")))
    private val other = selected.copy(id = "other")

    @Test fun selectedModelRetainsItsProviderInFolderTabAtEveryCollapseCombination() {
        for (folderCollapsed in listOf(false, true)) {
            for (providerCollapsed in listOf(false, true)) {
                val items = computeModelPickerItems(listOf(selected, other, other.copy(id = "root", groupId = null)),
                    listOf(group), emptyMap(),
                    selected.id, "alpha",
                    collapsedGroupIds = if (folderCollapsed) setOf(group.id) else emptySet(),
                    collapsedProviderIds = if (providerCollapsed) setOf(selected.id) else emptySet(),
                    selectedFolderId = group.id)
                assertTrue(items.any { it is ModelPickerItem.Group && it.group.id == group.id })
                val providerIndex = items.indexOfFirst {
                    it is ModelPickerItem.Provider && it.provider.id == selected.id
                }
                assertTrue(providerIndex >= 0)
                val provider = items[providerIndex] as ModelPickerItem.Provider
                val model = items[providerIndex + 1] as ModelPickerItem.Model
                assertEquals(selected.id, provider.provider.id)
                assertEquals(group.id, provider.provider.groupId)
                assertEquals(0, provider.depth)
                assertTrue(model.isSelected)
                assertEquals("alpha", model.modelId)
                assertEquals(1, model.depth)
                if (providerCollapsed) {
                    assertEquals(listOf("alpha"), items.filterIsInstance<ModelPickerItem.Model>()
                        .filter { it.provider.id == selected.id }.map { it.modelId })
                }
                assertEquals(if (providerCollapsed) 6 else 7, items.size)
            }
        }
    }

    @Test fun selectingAModelDoesNotReorderProvidersInAnyTab() {
        val first = selected.copy(id = "first", sortOrder = 0)
        val last = selected.copy(id = "last", sortOrder = 1)
        val rootFirst = first.copy(id = "root-first", groupId = null)
        val rootLast = last.copy(id = "root-last", groupId = null)
        val providers = listOf(last, rootLast, first, rootFirst)

        fun providerIds(selectedId: String, folderId: String? = null, ungrouped: Boolean = false) =
            computeModelPickerItems(
                providers, listOf(group), emptyMap(), selectedId, "alpha",
                selectedFolderId = folderId, ungroupedOnly = ungrouped
            ).filterIsInstance<ModelPickerItem.Provider>().map { it.provider.id }

        assertEquals(listOf("first", "last", "root-first", "root-last"), providerIds(last.id))
        assertEquals(providerIds(first.id), providerIds(last.id))
        assertEquals(listOf("first", "last"), providerIds(last.id, folderId = group.id))
        assertEquals(listOf("root-first", "root-last"), providerIds(rootLast.id, ungrouped = true))
    }

    @Test fun sectionsAndTabsMatchTheVisibleProvidersInOneList() {
        val second = ProviderGroup("second", "Second", sortOrder = 2)
        val first = group.copy(sortOrder = 1)
        val firstProvider = selected.copy(id = "first", groupId = first.id)
        val secondProvider = selected.copy(id = "second", groupId = second.id)
        val ungrouped = selected.copy(id = "root", groupId = "missing")
        val providers = listOf(ungrouped, secondProvider, firstProvider)

        fun items(query: String = "") = computeModelPickerItems(
            providers, listOf(second, first), emptyMap(), "second", "alpha", query = query
        )

        val rows = items()
        assertEquals(listOf("first", "second", "root"),
            rows.filterIsInstance<ModelPickerItem.Provider>().map { it.provider.id })
        assertEquals(listOf("folder_folder", "folder_second", "ungrouped"),
            computeModelPickerTabs(rows, "Ungrouped").map { it.key })
        assertEquals(listOf("folder_second"),
            computeModelPickerTabs(items("Second"), "Ungrouped").map { it.key })
        assertTrue(computeModelPickerTabs(items("no match"), "Ungrouped").isEmpty())
    }

    @Test fun collapsedRootProviderRetainsOnlySelectedModelAndExpansionRestoresOthers() {
        val root = selected.copy(groupId = null)
        val items = computeModelPickerItems(listOf(root), emptyList(), emptyMap(), root.id, "beta",
            collapsedProviderIds = setOf(root.id))
        assertTrue(items[0] is ModelPickerItem.Ungrouped)
        assertEquals(0, (items[1] as ModelPickerItem.Provider).depth)
        assertEquals("beta", (items[2] as ModelPickerItem.Model).modelId)
        assertEquals(1, (items[2] as ModelPickerItem.Model).depth)
        assertEquals(3, items.size)
        val expanded = computeModelPickerItems(listOf(root), emptyList(), emptyMap(), root.id, "beta")
        assertEquals(listOf("alpha", "beta"), expanded.filterIsInstance<ModelPickerItem.Model>().map { it.modelId })
    }

    @Test fun offIsSeparateFromDefaultAndDoesNotRemoveThinkingCapability() {
        assertFalse(reasoningEffortEnabled("none"))
        assertFalse(reasoningEffortEnabled(" OFF "))
        assertTrue(reasoningEffortEnabled(null))
        val options = reasoningEffortOptions()
        assertEquals("none", options.first().effort)
        assertEquals("None", options.first().label)
        assertTrue(options.any { it.effort == null })
        assertEquals(1, reasoningEffortIndex(null))
        val model = ModelConfiguration(supportsThinking = true, reasoningEffort = "high")
        assertTrue(model.withConversationReasoning("none").supportsThinking)
        assertEquals("high", model.reasoningEffort)
        assertEquals(model, model.withConversationReasoning(null))
    }

    @Test fun sameNamedModelUnderAnotherProviderIsNotMarkedSelected() {
        val items = computeModelPickerItems(listOf(selected, other), listOf(group), emptyMap(), selected.id, "alpha")
        val marked = items.filterIsInstance<ModelPickerItem.Model>().filter { it.isSelected }
        assertEquals(listOf(selected.id to "alpha"), marked.map { it.provider.id to it.modelId })
    }

    @Test fun sameDisplayNameWithinProviderDoesNotMarkSiblingModelSelected() {
        val provider = selected.copy(
            config = ProviderConfiguration(
                modelIds = listOf("alpha", "beta"),
                modelConfigs = mapOf(
                    "alpha" to ModelConfiguration(displayName = "alpha"),
                    "beta" to ModelConfiguration(displayName = "alpha")
                )
            )
        )
        val items = computeModelPickerItems(listOf(provider), listOf(group), emptyMap(), provider.id, "alpha")
        val marked = items.filterIsInstance<ModelPickerItem.Model>().filter { it.isSelected }

        assertEquals(listOf(provider.id to "alpha"), marked.map { it.provider.id to it.modelId })
    }

    @Test fun selectionDoesNotLeakToSameNamedModelsWhenSelectedProviderIsNotVisible() {
        val elsewhere = other.copy(groupId = null)
        val items = computeModelPickerItems(listOf(selected, elsewhere), listOf(group), emptyMap(), selected.id, "alpha",
            ungroupedOnly = true)
        assertTrue(items.filterIsInstance<ModelPickerItem.Model>().isNotEmpty())
        assertTrue(items.filterIsInstance<ModelPickerItem.Model>().none { it.isSelected })
    }
}
