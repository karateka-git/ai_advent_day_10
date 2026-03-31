package agent.memory.model

import llm.core.model.ChatMessage

/**
 * Полный in-memory снимок диалога, которым управляет слой памяти.
 */
data class MemoryState(
    val messages: List<ChatMessage> = emptyList(),
    val strategyState: StrategyState? = null,
    val metadata: MemoryMetadata = MemoryMetadata()
)
