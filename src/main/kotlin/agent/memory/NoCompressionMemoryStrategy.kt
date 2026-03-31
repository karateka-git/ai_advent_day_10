package agent.memory

import agent.memory.model.MemoryState
import llm.core.model.ChatMessage

/**
 * Базовая стратегия, которая передаёт полную сохранённую историю без сжатия.
 */
class NoCompressionMemoryStrategy : MemoryStrategy {
    override val type: MemoryStrategyType = MemoryStrategyType.NO_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.messages.toList()
}
