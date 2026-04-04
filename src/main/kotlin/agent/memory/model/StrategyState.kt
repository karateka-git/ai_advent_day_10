package agent.memory.model

import agent.memory.strategy.MemoryStrategyType
import llm.core.model.ChatMessage

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
    val summary: ConversationSummary? = null,
    val coveredMessagesCount: Int = 0
) : StrategyState

/**
 * Derived state стратегии Sticky Facts.
 *
 * @property facts ключ-значение с важными устойчивыми данными из диалога.
 * @property coveredMessagesCount количество несистемных сообщений, уже учтённых в текущем facts state.
 */
data class StickyFactsStrategyState(
    override val strategyType: MemoryStrategyType = MemoryStrategyType.STICKY_FACTS,
    val facts: Map<String, String> = emptyMap(),
    val coveredMessagesCount: Int = 0
) : StrategyState

/**
 * Checkpoint ветвящегося диалога.
 *
 * @property name имя checkpoint.
 * @property messages снимок сообщений на момент создания checkpoint.
 */
data class BranchCheckpointState(
    val name: String,
    val messages: List<ChatMessage>
)

/**
 * Независимая ветка диалога.
 *
 * @property name имя ветки.
 * @property sourceCheckpointName имя checkpoint, из которого создана ветка, если он был.
 * @property messages сообщения ветки.
 */
data class BranchConversationState(
    val name: String,
    val sourceCheckpointName: String? = null,
    val messages: List<ChatMessage>
)

/**
 * Derived state стратегии Branching.
 *
 * @property activeBranchName имя активной ветки.
 * @property latestCheckpointName имя последнего checkpoint, если он есть.
 * @property checkpoints список созданных checkpoint.
 * @property branches список веток диалога.
 */
data class BranchingStrategyState(
    override val strategyType: MemoryStrategyType = MemoryStrategyType.BRANCHING,
    val activeBranchName: String = DEFAULT_BRANCH_NAME,
    val latestCheckpointName: String? = null,
    val checkpoints: List<BranchCheckpointState> = emptyList(),
    val branches: List<BranchConversationState> = emptyList()
) : StrategyState {
    companion object {
        const val DEFAULT_BRANCH_NAME = "main"
    }
}

