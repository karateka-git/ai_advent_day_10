package agent.memory.core

import agent.memory.model.MemoryState
import agent.memory.strategy.MemoryStrategyType
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
     * Формирует фактический контекст prompt, который должен быть отправлен в LLM.
     */
    fun effectiveContext(state: MemoryState): List<ChatMessage>

    /**
     * Обновляет сохранённое состояние памяти после изменения диалога, не отвечая за сам prompt.
     */
    fun refreshState(
        state: MemoryState,
        mode: MemoryStateRefreshMode = MemoryStateRefreshMode.REGULAR
    ): MemoryState = state
}

/**
 * Контекст обновления состояния памяти.
 */
enum class MemoryStateRefreshMode {
    /**
     * Обычное обновление памяти после реального изменения диалога.
     *
     * В этом режиме стратегия может пересчитывать и сохранять derived state,
     * например summary или facts.
     */
    REGULAR,

    /**
     * Предварительный пересчёт памяти для построения preview-контекста.
     *
     * Используется при оценке токенов перед запросом и не должен менять
     * фактически сохранённое состояние памяти.
     */
    PREVIEW
}


