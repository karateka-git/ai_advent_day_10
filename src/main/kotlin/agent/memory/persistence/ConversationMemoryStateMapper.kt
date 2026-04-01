package agent.memory.persistence

import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.strategy.MemoryStrategyType
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState
import agent.storage.model.StoredMemoryMetadata

/**
 * Преобразует полное runtime-состояние памяти в persisted JSON-модель и обратно.
 */
class ConversationMemoryStateMapper(
    private val conversationMapper: ChatMessageConversationMapper = ChatMessageConversationMapper(),
    private val strategyStateMapper: StrategyStateMapper = StrategyStateMapper(conversationMapper)
) {
    /**
     * Преобразует persisted state в runtime state.
     */
    fun toRuntime(storedState: ConversationMemoryState): MemoryState =
        MemoryState(
            messages = storedState.messages.map(conversationMapper::fromStoredMessage),
            strategyState = strategyStateMapper.toRuntime(storedState.strategyState),
            metadata = MemoryMetadata(
                strategyType = storedState.metadata.strategyId?.let(MemoryStrategyType::fromId),
                compressedMessagesCount = storedState.metadata.compressedMessagesCount
            )
        )

    /**
     * Преобразует runtime state в persisted state.
     */
    fun toStored(runtimeState: MemoryState): ConversationMemoryState =
        ConversationMemoryState(
            messages = runtimeState.messages.map(conversationMapper::toStoredMessage),
            strategyState = strategyStateMapper.toStored(runtimeState.strategyState),
            metadata = StoredMemoryMetadata(
                strategyId = runtimeState.metadata.strategyType?.id,
                compressedMessagesCount = runtimeState.metadata.compressedMessagesCount
            )
        )
}
