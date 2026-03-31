package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemoryState(
    val messages: List<StoredMessage> = emptyList(),
    val summary: StoredSummary? = null,
    val strategyState: StoredStrategyState? = null,
    val metadata: StoredMemoryMetadata = StoredMemoryMetadata()
)

/**
 * Strategy-specific persisted state, расширяемый по мере появления новых стратегий памяти.
 */
@Serializable
data class StoredStrategyState(
    val strategyType: String? = null,
    val summary: StoredSummary? = null
)
