package agent.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class MemoryStrategyFactoryTest {
    @Test
    fun `availableOptions returns both supported strategies`() {
        assertEquals(
            listOf("no_compression", "summary_compression"),
            MemoryStrategyFactory.availableOptions().map { it.id }
        )
    }

    @Test
    fun `create returns no compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyId = "no_compression",
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<NoCompressionMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns summary compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyId = "summary_compression",
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<SummaryCompressionMemoryStrategy>(strategy)
    }
}

private class FactoryTestLanguageModel : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
