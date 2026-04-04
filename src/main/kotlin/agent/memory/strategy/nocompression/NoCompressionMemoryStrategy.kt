package agent.memory.strategy.nocompression

import agent.memory.core.MemoryStrategy
import agent.memory.model.MemoryState
import agent.memory.strategy.MemoryStrategyType
import llm.core.model.ChatMessage

/**
 * Базовая стратегия, которая передаёт полную сохранённую историю без сжатия.
 *
 * Не переопределяет `refreshState`, потому что использует только список сообщений и не хранит отдельный derived state.
 */
class NoCompressionMemoryStrategy : MemoryStrategy {
    override val type: MemoryStrategyType = MemoryStrategyType.NO_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> =
        state.messages.toList()
}


