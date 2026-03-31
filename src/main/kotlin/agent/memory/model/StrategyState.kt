package agent.memory.model

import agent.memory.MemoryStrategyType

/**
 * Базовый контракт для strategy-specific derived state, который хранится рядом
 * с полной историей сообщений, но относится только к конкретной стратегии памяти.
 */
sealed interface StrategyState {
    /**
     * Тип стратегии, к которому относится это состояние.
     */
    val strategyType: MemoryStrategyType
}

/**
 * Derived state стратегии rolling summary.
 *
 * @property summary текущее накопленное резюме истории.
 */
data class SummaryStrategyState(
    override val strategyType: MemoryStrategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
    val summary: ConversationSummary? = null
) : StrategyState
