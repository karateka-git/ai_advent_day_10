package agent.memory.persistence

import agent.memory.model.MemoryState
import agent.storage.mapper.ChatMessageConversationMapper
import agent.storage.model.ConversationMemoryState

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
            strategyState = strategyStateMapper.toRuntime(storedState.strategyState)
        )

    /**
     * Преобразует runtime state в persisted state.
     */
    fun toStored(runtimeState: MemoryState): ConversationMemoryState =
        ConversationMemoryState(
            messages = runtimeState.messages.map(conversationMapper::toStoredMessage),
            strategyState = strategyStateMapper.toStored(runtimeState.strategyState)
        )
}
