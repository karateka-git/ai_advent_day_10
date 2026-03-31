package agent.memory

import agent.memory.model.MemoryState
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Стратегия памяти, которая оставляет в effective prompt только системные сообщения
 * и последние сообщения диалога из полной истории.
 */
class SlidingWindowMemoryStrategy(
    private val recentMessagesCount: Int
) : MemoryStrategy {
    init {
        require(recentMessagesCount > 0) {
            "Количество последних сообщений должно быть больше нуля."
        }
    }

    override val type: MemoryStrategyType = MemoryStrategyType.SLIDING_WINDOW

    override fun effectiveContext(state: MemoryState): List<ChatMessage> {
        val systemMessages = state.messages.filter { it.role == ChatRole.SYSTEM }
        val dialogMessages = state.messages.filter { it.role != ChatRole.SYSTEM }

        return systemMessages + dialogMessages.takeLast(recentMessagesCount)
    }
}
