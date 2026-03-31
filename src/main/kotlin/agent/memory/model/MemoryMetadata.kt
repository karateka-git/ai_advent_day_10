package agent.memory.model

import agent.memory.MemoryStrategyType

data class MemoryMetadata(
    val strategyType: MemoryStrategyType? = null,
    val compressedMessagesCount: Int = 0
)
