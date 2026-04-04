package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemoryState(
    val messages: List<StoredMessage> = emptyList(),
    val strategyState: StoredStrategyState? = null
)

/**
 * Strategy-specific persisted state, расширяемый по мере появления новых стратегий памяти.
 */
@Serializable
data class StoredStrategyState(
    val strategyType: String? = null,
    val summary: StoredSummary? = null,
    val summaryCoveredMessagesCount: Int = 0,
    val facts: Map<String, String> = emptyMap(),
    val factsCoveredMessagesCount: Int = 0,
    val activeBranchName: String? = null,
    val latestCheckpointName: String? = null,
    val checkpoints: List<StoredBranchCheckpoint> = emptyList(),
    val branches: List<StoredBranchConversation> = emptyList()
)

@Serializable
data class StoredBranchCheckpoint(
    val name: String,
    val messages: List<StoredMessage> = emptyList()
)

@Serializable
data class StoredBranchConversation(
    val name: String,
    val sourceCheckpointName: String? = null,
    val messages: List<StoredMessage> = emptyList()
)

