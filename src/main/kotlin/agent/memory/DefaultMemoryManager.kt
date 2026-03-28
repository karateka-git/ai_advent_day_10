package agent.memory

import agent.core.AgentTokenStats
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ContextCompressionStats
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.storage.JsonConversationStore
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredMemoryMetadata
import agent.storage.model.StoredSummary
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val conversationStore: JsonConversationStore = JsonConversationStore.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy(),
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener
) : MemoryManager {
    private val conversationMapper = ChatMessageConversationMapper()
    private var memoryState = loadMemoryState()

    override fun currentConversation(): List<ChatMessage> = memoryState.messages.toList()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        val effectiveConversation = effectiveConversation()
        val historyTokens = languageModel.tokenCounter?.countMessages(effectiveConversation)
        val userPromptTokens = languageModel.tokenCounter?.countText(userPrompt)
        val promptTokensLocal = languageModel.tokenCounter?.countMessages(
            effectiveConversationWithUserPrompt(userPrompt)
        )

        return AgentTokenStats(
            historyTokens = historyTokens,
            promptTokensLocal = promptTokensLocal,
            userPromptTokens = userPromptTokens
        )
    }

    override fun appendUserMessage(userPrompt: String): List<ChatMessage> {
        val stateWithUserMessage = memoryState.copy(
            messages = memoryState.messages + ChatMessage(role = ChatRole.USER, content = userPrompt)
        )
        memoryState = refreshState(stateWithUserMessage, notifyCompression = true)
        saveState()
        return effectiveConversation()
    }

    override fun appendAssistantMessage(content: String) {
        memoryState = memoryState.copy(
            messages = memoryState.messages + ChatMessage(role = ChatRole.ASSISTANT, content = content)
        )
        saveState()
    }

    override fun clear() {
        memoryState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyId = memoryStrategy.id)
        )
        saveState()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedState = JsonConversationStore(sourcePath).loadState().toMemoryState()
        require(importedState.messages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        memoryState = memoryStrategy.refreshState(
            importedState.copy(
                metadata = importedState.metadata.copy(strategyId = memoryStrategy.id)
            )
        )
        saveState()
    }

    private fun loadMemoryState(): MemoryState {
        val savedState = conversationStore.loadState().toMemoryState()
        if (savedState.messages.isNotEmpty()) {
            return memoryStrategy.refreshState(
                savedState.copy(
                    metadata = savedState.metadata.copy(strategyId = memoryStrategy.id)
                )
            )
        }

        val initialState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyId = memoryStrategy.id)
        )
        saveState(initialState)
        return initialState
    }

    private fun saveState() {
        saveState(memoryState)
    }

    private fun saveState(state: MemoryState) {
        memoryState = state.copy(
            metadata = state.metadata.copy(strategyId = memoryStrategy.id)
        )
        conversationStore.saveState(memoryState.toStoredState())
    }

    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    private fun effectiveConversation(): List<ChatMessage> =
        memoryStrategy.effectiveContext(memoryState)

    private fun effectiveConversationWithUserPrompt(userPrompt: String): List<ChatMessage> =
        memoryStrategy.effectiveContext(
            refreshState(
                memoryState.copy(
                    messages = memoryState.messages + ChatMessage(role = ChatRole.USER, content = userPrompt)
                ),
                notifyCompression = false
            )
        )

    private fun refreshState(
        state: MemoryState,
        notifyCompression: Boolean
    ): MemoryState {
        val refreshedState = memoryStrategy.refreshState(state)
        if (!notifyCompression || !compressionApplied(state, refreshedState)) {
            return refreshedState
        }

        lifecycleListener.onContextCompressionStarted()
        lifecycleListener.onContextCompressionFinished(
            ContextCompressionStats(
                tokensBefore = languageModel.tokenCounter?.countMessages(memoryStrategy.effectiveContext(state)),
                tokensAfter = languageModel.tokenCounter?.countMessages(memoryStrategy.effectiveContext(refreshedState))
            )
        )

        return refreshedState
    }

    private fun compressionApplied(previousState: MemoryState, refreshedState: MemoryState): Boolean =
        refreshedState.metadata.compressedMessagesCount > previousState.metadata.compressedMessagesCount

    private fun ConversationMemoryState.toMemoryState(): MemoryState =
        MemoryState(
            messages = messages.map(conversationMapper::fromStoredMessage),
            summary = summary?.let {
                ConversationSummary(
                    content = it.content,
                    coveredMessagesCount = it.coveredMessagesCount
                )
            },
            metadata = MemoryMetadata(
                strategyId = metadata.strategyId,
                compressedMessagesCount = metadata.compressedMessagesCount
            )
        )

    private fun MemoryState.toStoredState(): ConversationMemoryState =
        ConversationMemoryState(
            messages = messages.map(conversationMapper::toStoredMessage),
            summary = summary?.let {
                StoredSummary(
                    content = it.content,
                    coveredMessagesCount = it.coveredMessagesCount
                )
            },
            metadata = StoredMemoryMetadata(
                strategyId = metadata.strategyId,
                compressedMessagesCount = metadata.compressedMessagesCount
            )
        )
}
