package agent.memory

import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ContextCompressionStats
import agent.memory.core.DefaultMemoryManager
import agent.memory.core.MemoryStrategy
import agent.memory.strategy.BranchingMemoryStrategy
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.NoCompressionMemoryStrategy
import agent.memory.strategy.SummaryCompressionMemoryStrategy
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.strategy.summary.ConversationSummarizer
import agent.storage.JsonConversationStore
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredMemoryMetadata
import agent.storage.model.StoredMessage
import agent.storage.model.StoredStrategyState
import agent.storage.model.StoredSummary
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

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
    fun `summary strategy compresses history before model request but keeps full conversation`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val lifecycleListener = RecordingAgentLifecycleListener()
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(tokenCounter = CharacterTokenCounter()),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = SummaryCompressionMemoryStrategy(
                recentMessagesCount = 2,
                summaryBatchSize = 2,
                summarizer = FixedConversationSummarizer("Сжатый фрагмент")
            ),
            lifecycleListener = lifecycleListener
        )

        manager.appendUserMessage("u1")
        manager.appendAssistantMessage("a1")
        manager.appendUserMessage("u2")
        manager.appendAssistantMessage("a2")

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2")
            ),
            manager.currentConversation()
        )

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
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            manager.currentConversation()
        )
        assertEquals(1, lifecycleListener.contextCompressionStartedCount)
        val stats = assertNotNull(lifecycleListener.lastContextCompressionStats)
        assertNotNull(stats.tokensBefore)
        assertNotNull(stats.tokensAfter)
        assertNotNull(stats.savedTokens)
        assertEquals(stats.tokensBefore - stats.tokensAfter, stats.savedTokens)
    }

    @Test
    fun `switching from summary state to no compression keeps full stored history`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        store.saveState(
            ConversationMemoryState(
                messages = listOf(
                    StoredMessage(role = "system", content = "Системное сообщение"),
                    StoredMessage(role = "user", content = "u1"),
                    StoredMessage(role = "assistant", content = "a1"),
                    StoredMessage(role = "user", content = "u2")
                ),
                strategyState = StoredStrategyState(
                    strategyType = "summary_compression",
                    summary = StoredSummary(
                        content = "Сжатый фрагмент",
                        coveredMessagesCount = 2
                    )
                ),
                metadata = StoredMemoryMetadata(
                    strategyId = "summary_compression",
                    compressedMessagesCount = 2
                )
            )
        )

        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = NoCompressionMemoryStrategy()
        )

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2")
            ),
            manager.currentConversation()
        )
    }

    @Test
    fun `non branching strategy does not expose branching capability`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = NoCompressionMemoryStrategy()
        )

        kotlin.test.assertNull(manager.capability(agent.capability.BranchingCapability::class.java))
    }

    @Test
    fun `branching strategy keeps independent histories for different branches`() {
        val tempDir = Files.createTempDirectory("memory-manager-test")
        val store = JsonConversationStore(tempDir.resolve("conversation.json"))
        val manager = DefaultMemoryManager(
            languageModel = FakeLanguageModel(),
            systemPrompt = "Системное сообщение",
            conversationStore = store,
            memoryStrategy = BranchingMemoryStrategy()
        )
        val branchingCapability = checkNotNull(
            manager.capability(agent.capability.BranchingCapability::class.java)
        )

        manager.appendUserMessage("main-u1")
        manager.appendAssistantMessage("main-a1")

        assertEquals(
            "checkpoint-1",
            branchingCapability.createCheckpoint().name
        )
        assertEquals("option-a", branchingCapability.createBranch("option-a").name)
        assertEquals("option-b", branchingCapability.createBranch("option-b").name)

        branchingCapability.switchBranch("option-a")
        manager.appendUserMessage("a-u1")
        manager.appendAssistantMessage("a-a1")
        val optionAConversation = manager.currentConversation()

        branchingCapability.switchBranch("option-b")
        manager.appendUserMessage("b-u1")
        manager.appendAssistantMessage("b-a1")
        val optionBConversation = manager.currentConversation()

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "main-u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "main-a1"),
                ChatMessage(role = ChatRole.USER, content = "a-u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a-a1")
            ),
            optionAConversation
        )
        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.SYSTEM, content = "Системное сообщение"),
                ChatMessage(role = ChatRole.USER, content = "main-u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "main-a1"),
                ChatMessage(role = ChatRole.USER, content = "b-u1"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "b-a1")
            ),
            optionBConversation
        )
        assertEquals("option-b", branchingCapability.branchStatus().activeBranchName)
    }
}

private class RecordingAgentLifecycleListener : AgentLifecycleListener {
    var contextCompressionStartedCount: Int = 0
        private set
    var lastContextCompressionStats: ContextCompressionStats? = null
        private set

    override fun onModelWarmupStarted() = Unit

    override fun onModelWarmupFinished() = Unit

    override fun onModelRequestStarted() = Unit

    override fun onModelRequestFinished() = Unit

    override fun onContextCompressionStarted() {
        contextCompressionStartedCount++
    }

    override fun onContextCompressionFinished(stats: ContextCompressionStats) {
        lastContextCompressionStats = stats
    }
}

private class FakeLanguageModel(
    override val tokenCounter: TokenCounter? = null
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}

private class CharacterTokenCounter : TokenCounter {
    override fun countText(text: String): Int = text.length
}

private class LastMessageOnlyStrategy : MemoryStrategy {
    override val type: MemoryStrategyType = MemoryStrategyType.NO_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.messages.takeLast(1)
}

private class FixedConversationSummarizer(
    private val summary: String
) : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String = summary
}

