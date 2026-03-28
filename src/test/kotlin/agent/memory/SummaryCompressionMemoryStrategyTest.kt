package agent.memory

import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.summarizer.ConversationSummarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class SummaryCompressionMemoryStrategyTest {
    @Test
    fun `returns original messages when summary is absent`() {
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
    fun `builds system summary and remaining dialog context`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            summary = ConversationSummary(
                content = "Пользователь уже рассказал о прошлой задаче.",
                coveredMessagesCount = 2
            ),
            metadata = MemoryMetadata(compressedMessagesCount = 2)
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
    }

    @Test
    fun `refreshState compresses eligible batch removes source messages and tracks compressed count`() {
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

        val refreshedState = strategy.refreshState(MemoryState(messages = messages))

        assertEquals("Пользователь: u1\nАссистент: a1", refreshedState.summary?.content)
        assertEquals(2, refreshedState.summary?.coveredMessagesCount)
        assertEquals(2, refreshedState.metadata.compressedMessagesCount)
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            refreshedState.messages
        )
    }

    @Test
    fun `refreshState rewrites existing summary instead of appending another one`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            summary = ConversationSummary(
                content = "Пользователь: u1\nАссистент: a1",
                coveredMessagesCount = 2
            ),
            metadata = MemoryMetadata(compressedMessagesCount = 2)
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(
            "Система: Предыдущее резюме: Пользователь: u1\nАссистент: a1\nПользователь: u2\nАссистент: a2",
            refreshedState.summary?.content
        )
        assertEquals(4, refreshedState.summary?.coveredMessagesCount)
        assertEquals(4, refreshedState.metadata.compressedMessagesCount)
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u3"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            refreshedState.messages
        )
    }

    @Test
    fun `refreshState does not compress when there are not enough old messages outside recent tail`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = FixedSummaryConversationSummarizer("Не должен использоваться")
        )
        val state = MemoryState(
            messages = listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "system"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            summary = ConversationSummary(
                content = "Пользователь: u1\nАссистент: a1",
                coveredMessagesCount = 2
            ),
            metadata = MemoryMetadata(compressedMessagesCount = 2)
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
