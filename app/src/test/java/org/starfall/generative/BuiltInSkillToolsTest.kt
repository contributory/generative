package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.tool.SearchableTool
import org.starfall.generative.features.home.chat.logic.tool.searchToolCatalog
import org.starfall.generative.features.home.chat.ui.input.SkillPromptAttachment
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BuiltInSkillToolsTest {
    @Test fun looseSearchMatchesBothPartialDirectionsAndOnlyReturnsNamesAndStatuses() {
        val catalog = listOf(
            SearchableTool("generate_image", false),
            SearchableTool("search_tools", true),
            SearchableTool("create_skill", false)
        )
        val names = searchToolCatalog("image and search", catalog)
        assertEquals(listOf("generate_image", "search_tools"), names.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        val backward = searchToolCatalog("create skill please", catalog)
        assertEquals("create_skill", backward.first().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(false, names.first().jsonObject.getValue("enabled").jsonPrimitive.content.toBoolean())
        assertEquals(setOf("name", "enabled"), names.first().jsonObject.keys)
    }

    @Test fun selectedContentProvenanceEndsWhenItsLastCharacterIsRemoved() {
        val selected = "Create a reusable skill"
        val initial = "$selected\n\nextra"
        val attachment = SkillPromptAttachment(0, selected.length)
        assertNotNull(attachment.afterEdit(initial, "$selected\n\nextra!"))
        assertNull(attachment.afterEdit(initial, "extra"))
        assertNull(attachment.afterEdit(initial, ""))
        assertNotNull(attachment.afterEdit(initial, "Create a reusable skil\n\nextra"))
    }
}
