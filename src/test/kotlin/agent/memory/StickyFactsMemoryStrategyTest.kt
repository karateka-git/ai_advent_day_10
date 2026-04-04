package agent.memory

import agent.memory.core.MemoryStateRefreshMode
import agent.memory.model.MemoryState
import agent.memory.model.StickyFactsStrategyState
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.stickyfacts.ConversationFactsExtractor
import agent.memory.strategy.stickyfacts.StickyFactsMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class StickyFactsMemoryStrategyTest {
    @Test
    fun `effectiveContext includes facts block and recent dialog tail`() {
        val strategy = StickyFactsMemoryStrategy(
            recentMessagesCount = 2,
            factsBatchSize = 3,
            factsExtractor = NoOpFactsExtractor()
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1"),
                ChatMessage(ChatRole.USER, "u2"),
                ChatMessage(ChatRole.ASSISTANT, "a2")
            ),
            strategyState = StickyFactsStrategyState(
                facts = linkedMapOf(
                    "goal" to "Собрать ТЗ",
                    "budget" to "до 120 тысяч рублей"
                ),
                coveredMessagesCount = 4
            )
        )

        assertEquals(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(
                    ChatRole.SYSTEM,
                    "Важные facts из диалога:\n- budget: до 120 тысяч рублей\n- goal: Собрать ТЗ"
                ),
                ChatMessage(ChatRole.USER, "u2"),
                ChatMessage(ChatRole.ASSISTANT, "a2")
            ),
            strategy.effectiveContext(state)
        )
    }

    @Test
    fun `refreshState waits until facts batch is accumulated`() {
        val extractor = RecordingFactsExtractor(
            factsToReturn = mapOf("goal" to "Собрать ТЗ")
        )
        val strategy = StickyFactsMemoryStrategy(
            recentMessagesCount = 2,
            factsBatchSize = 3,
            factsExtractor = extractor
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1"),
                ChatMessage(ChatRole.USER, "u2")
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(0, extractor.callsCount)
        assertEquals(
            StickyFactsStrategyState(
                facts = emptyMap(),
                coveredMessagesCount = 0
            ),
            refreshedState.strategyState
        )
    }

    @Test
    fun `refreshState updates facts with only new unprocessed batch`() {
        val extractor = RecordingFactsExtractor(
            factsToReturn = mapOf(
                "goal" to "Собрать ТЗ",
                "budget" to "до 120 тысяч рублей"
            )
        )
        val strategy = StickyFactsMemoryStrategy(
            recentMessagesCount = 2,
            factsBatchSize = 2,
            factsExtractor = extractor
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u0"),
                ChatMessage(ChatRole.ASSISTANT, "a0"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1"),
                ChatMessage(ChatRole.USER, "u2"),
                ChatMessage(ChatRole.ASSISTANT, "a2"),
                ChatMessage(ChatRole.USER, "u3")
            ),
            strategyState = StickyFactsStrategyState(
                facts = mapOf("existing" to "value"),
                coveredMessagesCount = 4
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(1, extractor.callsCount)
        assertEquals(mapOf("existing" to "value"), extractor.lastExistingFacts)
        assertEquals(
            listOf(
                ChatMessage(ChatRole.USER, "u2"),
                ChatMessage(ChatRole.ASSISTANT, "a2"),
                ChatMessage(ChatRole.USER, "u3")
            ),
            extractor.lastNewMessagesBatch
        )
        assertEquals(
            StickyFactsStrategyState(
                facts = mapOf(
                    "goal" to "Собрать ТЗ",
                    "budget" to "до 120 тысяч рублей"
                ),
                coveredMessagesCount = 7
            ),
            refreshedState.strategyState
        )
    }

    @Test
    fun `refreshState resets incompatible strategy state before batch extraction`() {
        val extractor = RecordingFactsExtractor(
            factsToReturn = mapOf("goal" to "Новое значение")
        )
        val strategy = StickyFactsMemoryStrategy(
            recentMessagesCount = 2,
            factsBatchSize = 1,
            factsExtractor = extractor
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "Обнови цель.")
            ),
            strategyState = SummaryStrategyState()
        )

        strategy.refreshState(state)

        assertEquals(emptyMap(), extractor.lastExistingFacts)
        assertEquals(listOf(ChatMessage(ChatRole.USER, "Обнови цель.")), extractor.lastNewMessagesBatch)
    }

    @Test
    fun `refreshState does not call extractor in preview mode`() {
        val extractor = RecordingFactsExtractor(
            factsToReturn = mapOf("goal" to "Не должно обновиться")
        )
        val strategy = StickyFactsMemoryStrategy(
            recentMessagesCount = 2,
            factsBatchSize = 1,
            factsExtractor = extractor
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "Это только preview")
            )
        )

        val refreshedState = strategy.refreshState(state, MemoryStateRefreshMode.PREVIEW)

        assertEquals(0, extractor.callsCount)
        assertEquals(state, refreshedState)
    }
}

private class NoOpFactsExtractor : ConversationFactsExtractor {
    override fun extract(
        existingFacts: Map<String, String>,
        newMessagesBatch: List<ChatMessage>
    ): Map<String, String> = existingFacts
}

private class RecordingFactsExtractor(
    private val factsToReturn: Map<String, String>
) : ConversationFactsExtractor {
    var lastExistingFacts: Map<String, String> = emptyMap()
        private set
    var lastNewMessagesBatch: List<ChatMessage> = emptyList()
        private set
    var callsCount: Int = 0
        private set

    override fun extract(
        existingFacts: Map<String, String>,
        newMessagesBatch: List<ChatMessage>
    ): Map<String, String> {
        callsCount++
        lastExistingFacts = existingFacts
        lastNewMessagesBatch = newMessagesBatch
        return factsToReturn
    }
}
