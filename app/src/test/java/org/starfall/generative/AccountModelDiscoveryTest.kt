package org.starfall.generative

import org.starfall.generative.integrations.modelapis.account.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.integrations.modelapis.antigravity.AntigravityAdapter
import org.starfall.generative.shared.auth.*
import org.starfall.generative.integrations.modelapis.githubcopilot.GitHubCopilotAdapter
import org.starfall.generative.integrations.modelapis.githubcopilot.copilotDiscoveredModel
import org.starfall.generative.integrations.modelapis.openaicodex.parseCodexModelCatalog
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.files.AttachmentResolver
import org.starfall.generative.features.mcp.shared.ui.*
import org.starfall.generative.features.machines.shared.ui.*
import org.starfall.generative.integrations.remote.*
import org.starfall.generative.home.chat.*
import org.starfall.generative.integrations.modelapis.*
import org.starfall.generative.shared.files.*
import org.starfall.generative.shared.icons.*
import org.starfall.generative.shared.media.*
import org.starfall.generative.shared.networking.*
import org.starfall.generative.shared.storage.preferences.*
import java.util.concurrent.TimeUnit
import org.starfall.generative.shared.model.auth.AuthMethod
import org.starfall.generative.shared.model.auth.Authorization
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountModelDiscoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun keystore() = installTestAndroidKeyStore()
    private fun provider(type: ModelApiType, server: MockWebServer, slot: String) = ModelProvider(
        "provider", type.displayName, type, baseUrl = server.url("/").toString().trimEnd('/'),
        auth = Authorization(AuthMethod.OAUTH, value = "account:${type.name.lowercase()}:v1", oauthAccountId = slot))
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)

    @Test fun antigravityUsesValidPlatformAndLiveProjectCatalogPerAccountWithQuotaAndOpaqueIds() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"cloudaicompanionProject":"project-a"}"""))
            server.enqueue(json("""{"models":{
                "opaque-M7":{"displayName":"Premium model","model":"wire-model-a","quotaInfo":{"remainingFraction":0}},
                "internal":{"displayName":"Internal model","isInternal":true}}}"""))
            server.enqueue(json("""{"cloudaicompanionProject":{"id":"project-b"}}"""))
            server.enqueue(json("""{"models":{"tier-b":{"displayName":"Account B","quotaInfo":{"remainingFraction":0.5}}}}"""))
            server.enqueue(json("""{"models":{"refreshed":{"displayName":"New catalog"}}}"""))
            val store = AccountTokenStore(context, "antigravity")
            store.save("antigravity:a", AccountTokenState("access-a", "refresh-a"))
            store.save("antigravity:b", AccountTokenState("access-b", "refresh-b"))
            val a = provider(ModelApiType.ANTIGRAVITY, server, "antigravity:a")
            val b = provider(ModelApiType.ANTIGRAVITY, server, "antigravity:b")
            val adapter = AntigravityAdapter(context, AttachmentResolver(context))
            val catalogA = adapter.fetchModelCatalog(a)
            assertEquals(listOf("opaque-M7", "internal"), catalogA.map { it.id })
            assertEquals("Premium model", catalogA.first().displayName)
            assertEquals(0, catalogA.first().metadata["quotaInfo"]!!.jsonObject["remainingFraction"]!!.jsonPrimitive.int)
            assertEquals(listOf("tier-b"), adapter.fetchModels(b))
            assertEquals(listOf("refreshed"), adapter.fetchModels(a))
            val requests = List(5) { server.takeRequest(5, TimeUnit.SECONDS)!! }
            listOf(0, 2).forEach { index ->
                val load = requests[index]
                assertEquals("/v1internal:loadCodeAssist", load.path)
                assertEquals("PLATFORM_UNSPECIFIED", Json.parseToJsonElement(load.body.readUtf8())
                    .jsonObject["metadata"]!!.jsonObject.text("platform"))
            }
            listOf(1 to "project-a", 3 to "project-b", 4 to "project-a").forEach { (index, project) ->
                assertEquals("/v1internal:fetchAvailableModels", requests[index].path)
                assertEquals(project, Json.parseToJsonElement(requests[index].body.readUtf8()).jsonObject.text("project"))
            }
            assertEquals(listOf("Bearer access-a", "Bearer access-a", "Bearer access-b", "Bearer access-b", "Bearer access-a"),
                requests.map { it.getHeader("Authorization") })
            assertEquals("project-a", store.load("antigravity:a")!!.projectId)
            assertEquals("project-b", store.load("antigravity:b")!!.projectId)
            val selected = a.copy(config = a.config.copy(modelConfigs = mapOf("opaque-M7" to catalogA.first().configuration())))
            val wire = adapter.prepareModelProvider(selected, "opaque-M7")
            val normalized = adapter.normalizeToolRequest(selected, wire, obj("model" to str("opaque-M7")), "")
            assertEquals("wire-model-a", normalized.text("model"))
            assertEquals("project-a", normalized.text("project"))
        }
    }

    @Test fun antigravityOnboardingPollsOperationAndUsesDefaultTierAndValidMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"allowedTiers":[{"id":"other"},{"id":"free-tier","isDefault":true}]}"""))
            server.enqueue(json("""{"name":"operations/onboard-test","done":false}"""))
            server.enqueue(json("""{"done":true,"response":{"cloudaicompanionProject":{"id":"provisioned"}}}"""))
            server.enqueue(json("""{"models":{"gemini-test":{"displayName":"Gemini Test"}}}"""))
            AccountTokenStore(context, "antigravity").save("onboard", AccountTokenState("access", "refresh"))
            val adapter = AntigravityAdapter(context, AttachmentResolver(context))
            assertEquals(listOf("gemini-test"), adapter.fetchModels(provider(ModelApiType.ANTIGRAVITY, server, "onboard")))
            server.takeRequest(5, TimeUnit.SECONDS)
            val onboard = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1internal:onboardUser", onboard.path)
            val body = Json.parseToJsonElement(onboard.body.readUtf8()).jsonObject
            assertEquals("free-tier", body.text("tierId"))
            assertEquals("PLATFORM_UNSPECIFIED", body["metadata"]!!.jsonObject.text("platform"))
            val poll = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("GET", poll.method)
            assertEquals("/v1internal/operations/onboard-test", poll.path)
        }
    }

    @Test fun copilotUsesCurrentAccountEntitlementsAndPreservesMetadataThroughService() = runBlocking {
        MockWebServer().use { server ->
            val catalog = """{"data":[
                {"id":"gpt-5-test","name":"Available GPT","model_picker_enabled":true,
                 "policy":{"state":"enabled"},"supported_endpoints":["/chat/completions"],
                 "capabilities":{"limits":{"max_context_window_tokens":300000},
                     "supports":{"vision":false,"tool_calls":true,"streaming":false,"reasoning_effort":["low","high"]}}},
                {"id":"restricted","model_picker_enabled":true,"policy":{"state":"disabled"}},
                {"id":"utility","model_picker_enabled":false},
                {"id":"embedding","capabilities":{"type":"embeddings"}}]}"""
            server.enqueue(json(catalog))
            server.enqueue(json("""{"data":[{"id":"account-b","name":"B model","supported_endpoints":["/responses"]}]}"""))
            server.enqueue(json(catalog))
            val store = AccountTokenStore(context, "github_copilot")
            store.save("copilot:a", AccountTokenState("token-a", ""))
            store.save("copilot:b", AccountTokenState("token-b", ""))
            val a = provider(ModelApiType.GITHUB_COPILOT, server, "copilot:a")
            val b = provider(ModelApiType.GITHUB_COPILOT, server, "copilot:b")
            val service = AiService(context)
            val catalogA = service.fetchProviderModelCatalog(a)
            assertEquals(listOf("gpt-5-test", "restricted", "utility", "embedding"), catalogA.map { it.id })
            val model = catalogA.first()
            assertEquals("Available GPT", model.displayName)
            assertEquals(300000, model.contextWindowTokens)
            assertEquals(listOf("/chat/completions"), model.metadata["supported_endpoints"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertFalse(model.configuration().supportsVision)
            assertTrue(model.configuration().supportsThinking)
            assertEquals(false, model.configuration().supportStream)
            assertEquals(listOf("account-b"), service.fetchProviderModels(b))
            val wire = GitHubCopilotAdapter(context, AttachmentResolver(context)).prepareModelProvider(a, "gpt-5-test")
            // Explicit account routing must win even for GPT-5 names.
            assertEquals(ModelApiType.OPENAI, wire.type)
            assertEquals("token-a", wire.auth.token)
            val requests = List(3) { server.takeRequest(5, TimeUnit.SECONDS)!! }
            assertEquals(listOf("Bearer token-a", "Bearer token-b", "Bearer token-a"), requests.map { it.getHeader("Authorization") })
            assertTrue(requests.all { it.path == "/models" && it.getHeader("X-GitHub-Api-Version") == "2026-06-01" })
        }
    }

    @Test fun copilotCatalogDoesNotFilterByAccountPolicyPickerOrModelType() {
        val model = Json.parseToJsonElement("""{
            "id":"gpt-5-preview",
            "model_picker_enabled":false,
            "policy":{"state":"enabled"},
            "capabilities":{"limits":{"max_context_window_tokens":128000},"supports":{"tool_calls":true}}
        }""").jsonObject
        assertEquals("gpt-5-preview", copilotDiscoveredModel(model)?.id)

        val disabled = Json.parseToJsonElement("""{
            "id":"restricted",
            "model_picker_enabled":true,
            "policy":{"state":"disabled"}
        }""").jsonObject
        assertEquals("restricted", copilotDiscoveredModel(disabled)?.id)
        val embedding = Json.parseToJsonElement("""{"id":"embedding","capabilities":{"type":"embeddings"}}""").jsonObject
        assertEquals("embedding", copilotDiscoveredModel(embedding)?.id)
    }

    @Test fun codexCatalogUsesLiveResponseWithoutAnApplicationModelAllowlist() {
        val catalog = parseCodexModelCatalog(Json.parseToJsonElement("""{
            "models": [
                {"slug":"live-model-a","display_name":"Live model A","context_window":128000},
                {"slug":"live-model-b","display_name":"Live model B"},
                {"slug":"internal-model","visibility":"internal"},
                {"slug":"live-model-a","display_name":"Duplicate"},
                {"display_name":"Missing ID"}
            ]
        }""").jsonObject)
        assertEquals(listOf("live-model-a", "live-model-b", "internal-model"), catalog.map { it.id })
        assertEquals("Live model A", catalog.first().displayName)
        assertEquals(128000, catalog.first().contextWindowTokens)
    }

    @Test fun copilotRefreshDoesNotKeepRemovedModelsOrRouteThemUsingOldAccountMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"data":[{"id":"was-available","supported_endpoints":["/v1/messages"]}]}"""))
            server.enqueue(json("""{"data":[{"id":"now-available","supported_endpoints":["/responses"]}]}"""))
            server.enqueue(json("""{"data":[]}"""))
            AccountTokenStore(context, "github_copilot").save("refresh", AccountTokenState("token", ""))
            val p = provider(ModelApiType.GITHUB_COPILOT, server, "refresh")
            val adapter = GitHubCopilotAdapter(context, AttachmentResolver(context))
            assertEquals(listOf("was-available"), adapter.fetchModels(p))
            assertEquals(listOf("now-available"), adapter.fetchModels(p))
            val failed = runCatching { adapter.prepareModelProvider(p, "was-available") }.exceptionOrNull()
            assertTrue(failed!!.message!!.contains("unavailable for the selected account"))
        }
    }
}
