package agent.memory

import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.model.SummaryStrategyState
import agent.memory.summarizer.ConversationSummarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class SummaryCompressionMemoryStrategyTest {
    @Test
    fun `returns original messages when summary is absent and nothing is covered`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "system"),
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1")
        )

        assertEquals(
            messages,
            strategy.effectiveContext(MemoryState(messages = messages))
        )
    }

    @Test
    fun `builds context from summary and uncovered tail without deleting history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "system"),
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
            ChatMessage(role = ChatRole.USER, content = "u3")
        )
        val state = MemoryState(
            messages = messages,
            strategyState = SummaryStrategyState(
                summary = ConversationSummary(
                    content = "Пользователь уже рассказал о прошлой задаче.",
                    coveredMessagesCount = 2
                )
            ),
            metadata = MemoryMetadata(
                strategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
                compressedMessagesCount = 2
            )
        )

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Краткое резюме предыдущего диалога:\nПользователь уже рассказал о прошлой задаче."
                ),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            strategy.effectiveContext(state)
        )
        assertEquals(messages, state.messages)
    }

    @Test
    fun `refreshState updates summary but keeps full message history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "system"),
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
            ChatMessage(role = ChatRole.USER, content = "u3")
        )

        val refreshedState = strategy.refreshState(
            MemoryState(
                messages = messages,
                metadata = MemoryMetadata(strategyType = MemoryStrategyType.SUMMARY_COMPRESSION)
            )
        )
        val refreshedSummary = (refreshedState.strategyState as? SummaryStrategyState)?.summary

        assertEquals("Пользователь: u1\nАссистент: a1", refreshedSummary?.content)
        assertEquals(2, refreshedSummary?.coveredMessagesCount)
        assertEquals(2, refreshedState.metadata.compressedMessagesCount)
        assertEquals(messages, refreshedState.messages)
    }

    @Test
    fun `refreshState rewrites existing summary and advances covered counter`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            strategyState = SummaryStrategyState(
                summary = ConversationSummary(
                    content = "Пользователь: u0\nАссистент: a0",
                    coveredMessagesCount = 2
                )
            ),
            metadata = MemoryMetadata(
                strategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
                compressedMessagesCount = 2
            )
        )

        val refreshedState = strategy.refreshState(state)
        val refreshedSummary = (refreshedState.strategyState as? SummaryStrategyState)?.summary

        assertEquals(
            "Система: Предыдущее резюме: Пользователь: u0\nАссистент: a0\nПользователь: u2\nАссистент: a2",
            refreshedSummary?.content
        )
        assertEquals(4, refreshedSummary?.coveredMessagesCount)
        assertEquals(4, refreshedState.metadata.compressedMessagesCount)
        assertEquals(state.messages, refreshedState.messages)
    }

    @Test
    fun `refreshState activates summary from current window instead of full history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            metadata = MemoryMetadata(strategyType = MemoryStrategyType.NO_COMPRESSION)
        )

        val refreshedState = strategy.refreshState(state)
        val refreshedSummary = (refreshedState.strategyState as? SummaryStrategyState)?.summary

        assertEquals("Ассистент: a2\nПользователь: u3", refreshedSummary?.content)
        assertEquals(5, refreshedSummary?.coveredMessagesCount)
        assertEquals(5, refreshedState.metadata.compressedMessagesCount)
        assertEquals(state.messages, refreshedState.messages)
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Краткое резюме предыдущего диалога:\nАссистент: a2\nПользователь: u3"
                ),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            strategy.effectiveContext(refreshedState)
        )
    }

    @Test
    fun `refreshState does not compress when there are not enough messages outside recent tail`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = FixedSummaryConversationSummarizer("Не должен использоваться")
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2")
            ),
            metadata = MemoryMetadata(strategyType = MemoryStrategyType.SUMMARY_COMPRESSION)
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(state, refreshedState)
    }
}

private class RecordingConversationSummarizer : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String =
        messages.joinToString(separator = "\n") { message ->
            "${message.role.displayName}: ${message.content}"
        }
}

private class FixedSummaryConversationSummarizer(
    private val summary: String
) : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String = summary
}
