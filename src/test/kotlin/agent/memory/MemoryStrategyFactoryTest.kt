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
    fun `availableOptions returns all supported strategies`() {
        assertEquals(
            listOf(
                MemoryStrategyType.NO_COMPRESSION,
                MemoryStrategyType.SUMMARY_COMPRESSION,
                MemoryStrategyType.SLIDING_WINDOW
            ),
            MemoryStrategyFactory.availableOptions().map { it.type }
        )
    }

    @Test
    fun `create returns no compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.NO_COMPRESSION,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<NoCompressionMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns summary compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<SummaryCompressionMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns sliding window strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.SLIDING_WINDOW,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<SlidingWindowMemoryStrategy>(strategy)
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
