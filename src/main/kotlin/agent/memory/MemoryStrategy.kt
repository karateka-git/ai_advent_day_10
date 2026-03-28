package agent.memory

import agent.memory.model.MemoryState
import llm.core.model.ChatMessage

interface MemoryStrategy {
    /**
     * Идентификатор стратегии памяти для сохранения и отладки состояния.
     */
    val id: String

    /**
     * Формирует эффективный контекст диалога, который должен быть отправлен в языковую модель.
     *
     * @param state текущее состояние памяти, включая сообщения и summary
     * @return сообщения, которые нужно использовать как контекст запроса
     */
    fun effectiveContext(state: MemoryState): List<ChatMessage>

    /**
     * Обновляет состояние памяти после изменения диалога.
     *
     * @param state текущее состояние памяти
     * @return обновлённое состояние памяти
     */
    fun refreshState(state: MemoryState): MemoryState = state
}
