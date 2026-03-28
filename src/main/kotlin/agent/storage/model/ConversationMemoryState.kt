package agent.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemoryState(
    val messages: List<StoredMessage> = emptyList(),
    val summary: StoredSummary? = null,
    val metadata: StoredMemoryMetadata = StoredMemoryMetadata()
)
