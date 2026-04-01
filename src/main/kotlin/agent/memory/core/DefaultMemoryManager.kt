package agent.memory.core

import agent.capability.AgentCapability
import agent.capability.BranchingCapability
import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.ContextCompressionStats
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.branching.BranchCoordinator
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.persistence.ConversationMemoryStateMapper
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.NoCompressionMemoryStrategy
import agent.storage.JsonConversationStore
import java.nio.file.Path
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Базовый in-memory менеджер диалога, используемый агентом.
 *
 * Хранит текущее состояние памяти, делегирует подготовку prompt в [MemoryStrategy], сохраняет
 * состояние на диск и сообщает статистику сжатия через [AgentLifecycleListener].
 */
class DefaultMemoryManager(
    private val languageModel: LanguageModel,
    private val systemPrompt: String,
    private val conversationStore: JsonConversationStore = JsonConversationStore.forLanguageModel(languageModel),
    private val memoryStrategy: MemoryStrategy = NoCompressionMemoryStrategy(),
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener,
    private val stateMapper: ConversationMemoryStateMapper = ConversationMemoryStateMapper(),
    private val branchCoordinator: BranchCoordinator = BranchCoordinator()
) : MemoryManager {
    private var memoryState = loadMemoryState()

    private val branchingCapability = object : BranchingCapability {
        override fun createCheckpoint(name: String?): BranchCheckpointInfo =
            this@DefaultMemoryManager.createCheckpoint(name)

        override fun createBranch(name: String): BranchInfo =
            this@DefaultMemoryManager.createBranch(name)

        override fun switchBranch(name: String): BranchInfo =
            this@DefaultMemoryManager.switchBranch(name)

        override fun branchStatus(): BranchingStatus =
            this@DefaultMemoryManager.branchStatus()
    }

    override fun currentConversation(): List<ChatMessage> = memoryState.messages.toList()

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? =
        branchingCapability
            .takeIf { memoryStrategy.type == MemoryStrategyType.BRANCHING && capabilityType.isInstance(it) }
            ?.let(capabilityType::cast)

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
        val updatedState = memoryState.copy(
            messages = memoryState.messages + ChatMessage(role = ChatRole.ASSISTANT, content = content)
        )
        memoryState =
            if (memoryStrategy.type == MemoryStrategyType.BRANCHING) {
                synchronizeStrategyId(memoryStrategy.refreshState(updatedState, MemoryStateRefreshMode.REGULAR))
            } else {
                updatedState
            }
        saveState()
    }

    override fun clear() {
        memoryState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyType = memoryStrategy.type)
        )
        saveState()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        val importedState = stateMapper.toRuntime(JsonConversationStore(sourcePath).loadState())
        require(importedState.messages.isNotEmpty()) {
            "Файл истории $sourcePath пустой или не содержит сообщений."
        }

        memoryState = synchronizeStrategyId(
            memoryStrategy.refreshState(importedState, MemoryStateRefreshMode.REGULAR)
        )
        saveState()
    }

    /**
     * Загружает сохранённое состояние памяти с диска или создаёт новое с системным сообщением.
     */
    private fun loadMemoryState(): MemoryState {
        val savedState = stateMapper.toRuntime(conversationStore.loadState())
        if (savedState.messages.isNotEmpty()) {
            return synchronizeStrategyId(
                memoryStrategy.refreshState(savedState, MemoryStateRefreshMode.REGULAR)
            )
        }

        val initialState = MemoryState(
            messages = listOf(createSystemMessage()),
            metadata = MemoryMetadata(strategyType = memoryStrategy.type)
        )
        saveState(initialState)
        return initialState
    }

    private fun saveState() {
        saveState(memoryState)
    }

    /**
     * Сохраняет текущее состояние памяти, синхронизируя идентификатор активной стратегии.
     */
    private fun saveState(state: MemoryState) {
        memoryState = synchronizeStrategyId(state)
        conversationStore.saveState(stateMapper.toStored(memoryState))
    }

    /**
     * Синхронизирует metadata с текущей активной стратегией после того,
     * как стратегия уже обработала входное состояние.
     */
    private fun synchronizeStrategyId(state: MemoryState): MemoryState =
        state.copy(
            metadata = state.metadata.copy(strategyType = memoryStrategy.type)
        )

    /**
     * Формирует базовое системное сообщение для нового или очищенного диалога.
     */
    private fun createSystemMessage(): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = systemPrompt
        )

    /**
     * Возвращает эффективный контекст для текущего состояния согласно активной стратегии.
     */
    private fun effectiveConversation(): List<ChatMessage> =
        memoryStrategy.effectiveContext(memoryState)

    /**
     * Строит предварительный эффективный контекст для гипотетического следующего сообщения.
     */
    private fun effectiveConversationWithUserPrompt(userPrompt: String): List<ChatMessage> =
        memoryStrategy.effectiveContext(
            refreshState(
                memoryState.copy(
                    messages = memoryState.messages + ChatMessage(role = ChatRole.USER, content = userPrompt)
                ),
                notifyCompression = false,
                mode = MemoryStateRefreshMode.PREVIEW
            )
        )

    /**
     * Применяет стратегию памяти к переданному состоянию и при необходимости сообщает статистику
     * сжатия.
     */
    private fun refreshState(
        state: MemoryState,
        notifyCompression: Boolean,
        mode: MemoryStateRefreshMode = MemoryStateRefreshMode.REGULAR
    ): MemoryState {
        val refreshedState = synchronizeStrategyId(memoryStrategy.refreshState(state, mode))
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

    /**
     * Определяет, изменилось ли покрытие истории rolling summary на последнем проходе.
     */
    private fun compressionApplied(previousState: MemoryState, refreshedState: MemoryState): Boolean =
        refreshedState.metadata.compressedMessagesCount > previousState.metadata.compressedMessagesCount

    private fun createCheckpoint(name: String?): BranchCheckpointInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.createCheckpoint(memoryState, name)
        memoryState = result.state
        saveState()
        return result.info
    }

    private fun createBranch(name: String): BranchInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.createBranch(memoryState, name)
        memoryState = result.state
        saveState()
        return result.info
    }

    private fun switchBranch(name: String): BranchInfo {
        requireBranchingEnabled()
        val result = branchCoordinator.switchBranch(memoryState, name)
        memoryState = result.state
        saveState()
        return result.info
    }

    private fun branchStatus(): BranchingStatus {
        requireBranchingEnabled()
        return branchCoordinator.branchStatus(memoryState)
    }

    private fun requireBranchingEnabled() {
        require(memoryStrategy.type == MemoryStrategyType.BRANCHING) {
            "Команды ветвления доступны только для стратегии Branching."
        }
    }
}
