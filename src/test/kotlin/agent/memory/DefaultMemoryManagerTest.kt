package agent.memory

import agent.memory.model.MemoryState
import agent.memory.summarizer.ConversationSummarizer
import agent.storage.JsonConversationStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class DefaultMemoryManagerTest {
    @Test
    fun `initializes storage with system message when history is empty`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))

        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        assertEquals(
            listOf(ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение")),
            manager.currentConversation()
        )
    }

    @Test
    fun `appendUserMessage returns updated conversation and persists it`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        val conversation = manager.appendUserMessage("Привет")

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "Привет")
            ),
            conversation
        )
        assertEquals(
            conversation,
            DefaultMemoryManager(
                languageModel = FakeLanguageModel(),
                systemPrompt = "Системное сообщение",
                conversationStore = store
            ).currentConversation()
        )
    }

    @Test
    fun `clear keeps only system message`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store
        )

        manager.appendUserMessage("Привет")
        manager.appendAssistantMessage("Здравствуйте")
        manager.clear()

        assertEquals(
            listOf(ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение")),
            manager.currentConversation()
        )
    }

    @Test
    fun `appendUserMessage returns effective context from strategy`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = LastMessageOnlyStrategy()
        )

        val conversation = manager.appendUserMessage("Привет")

        assertEquals(
            listOf(ChatMessage(role = ChatRole.USER, content = "Привет")),
            conversation
        )
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "Привет")
            ),
            manager.currentConversation()
        )
    }

    @Test
    fun `summary strategy compresses history after assistant response`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = SummaryCompressionMemoryStrategy(
                recentMessagesCount = 2,
                summaryBatchSize = 2,
                summarizer = FixedConversationSummarizer("Сжатый фрагмент")
            )
        )

        manager.appendUserMessage("u1")
        manager.appendAssistantMessage("a1")
        manager.appendUserMessage("u2")
        manager.appendAssistantMessage("a2")
        val effectiveContext = manager.appendUserMessage("u3")

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Краткое резюме предыдущего диалога:\nСжатый фрагмент"
                ),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            effectiveContext
        )
        assertEquals(4, manager.currentConversation().size)
    }
}

private class FakeLanguageModel : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}

private class LastMessageOnlyStrategy : MemoryStrategy {
    override val id: String = "last_message_only"

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.messages.takeLast(1)
}

private class FixedConversationSummarizer(
    private val summary: String
) : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String = summary
}
