package agent.memory

import agent.memory.model.MemoryState
import llm.core.model.ChatMessage

/**
 * Определяет, как обновляется сохранённая память и как она превращается в эффективный prompt.
 */
interface MemoryStrategy {
    /**
     * Стабильный идентификатор, используемый в сохранённых метаданных и отладочном выводе.
     */
    val type: MemoryStrategyType

    /**
     * Формирует фактический контекст prompt, который должен быть отправлен в языковую модель.
     */
    fun effectiveContext(state: MemoryState): List<ChatMessage>

    /**
     * Обновляет состояние памяти после изменения диалога.
     */
    fun refreshState(state: MemoryState): MemoryState = state
}
