package llm.core

import java.net.http.HttpClient
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageModelFactoryTest {
    @Test
    fun `availableModels marks configured and unconfigured models`() {
        val config = Properties().apply {
            setProperty("AGENT_ID", "agent")
            setProperty("TIMEWEB_USER_TOKEN", "token")
        }

        val models = LanguageModelFactory.availableModels(config)

        assertEquals(true, models.first { it.id == "timeweb" }.isConfigured)
        assertEquals(false, models.first { it.id == "huggingface" }.isConfigured)
    }

    @Test
    fun `createDefault chooses first configured model`() {
        val config = Properties().apply {
            setProperty("AGENT_ID", "agent")
            setProperty("TIMEWEB_USER_TOKEN", "token")
            setProperty("HF_API_TOKEN", "token")
        }

        val model = LanguageModelFactory.createDefault(config, HttpClient.newHttpClient())

        assertEquals("TimewebLanguageModel", model.info.name)
    }

    @Test
    fun `create supports explicit temperature override`() {
        val config = Properties().apply {
            setProperty("AGENT_ID", "agent")
            setProperty("TIMEWEB_USER_TOKEN", "token")
            setProperty("HF_API_TOKEN", "token")
        }

        val timewebModel = LanguageModelFactory.create(
            modelId = "timeweb",
            config = config,
            httpClient = HttpClient.newHttpClient(),
            temperature = 0.0
        )
        val huggingFaceModel = LanguageModelFactory.create(
            modelId = "huggingface",
            config = config,
            httpClient = HttpClient.newHttpClient(),
            temperature = 0.0
        )

        assertEquals("TimewebLanguageModel", timewebModel.info.name)
        assertEquals("HuggingFaceLanguageModel", huggingFaceModel.info.name)
    }
}
