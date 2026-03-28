package agent.memory.model

import llm.core.model.ChatMessage

data class MemoryState(
    val messages: List<ChatMessage> = emptyList(),
    val summary: ConversationSummary? = null,
    val metadata: MemoryMetadata = MemoryMetadata()
)
