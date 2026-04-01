package agent.memory.persistence

import agent.memory.model.BranchCheckpointState
import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryMetadata
import agent.memory.model.MemoryState
import agent.memory.model.StickyFactsStrategyState
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.MemoryStrategyType
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class ConversationMemoryStateMapperTest {
    private val mapper = ConversationMemoryStateMapper()

    @Test
    fun `maps summary strategy state to stored and back`() {
        val runtimeState = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1")
            ),
            strategyState = SummaryStrategyState(
                summary = ConversationSummary(
                    content = "summary",
                    coveredMessagesCount = 2
                )
            ),
            metadata = MemoryMetadata(
                strategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
                compressedMessagesCount = 2
            )
        )

        val restoredState = mapper.toRuntime(mapper.toStored(runtimeState))

        assertEquals(runtimeState, restoredState)
    }

    @Test
    fun `maps sticky facts and branching strategy state to stored and back`() {
        val runtimeState = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1"),
                ChatMessage(ChatRole.ASSISTANT, "a1")
            ),
            strategyState = BranchingStrategyState(
                activeBranchName = "option-a",
                latestCheckpointName = "base",
                checkpoints = listOf(
                    BranchCheckpointState(
                        name = "base",
                        messages = listOf(
                            ChatMessage(ChatRole.SYSTEM, "system"),
                            ChatMessage(ChatRole.USER, "u1")
                        )
                    )
                ),
                branches = listOf(
                    BranchConversationState(
                        name = "main",
                        messages = listOf(
                            ChatMessage(ChatRole.SYSTEM, "system"),
                            ChatMessage(ChatRole.USER, "u1")
                        )
                    ),
                    BranchConversationState(
                        name = "option-a",
                        sourceCheckpointName = "base",
                        messages = listOf(
                            ChatMessage(ChatRole.SYSTEM, "system"),
                            ChatMessage(ChatRole.USER, "u1"),
                            ChatMessage(ChatRole.ASSISTANT, "a1")
                        )
                    )
                )
            ),
            metadata = MemoryMetadata(strategyType = MemoryStrategyType.BRANCHING)
        )

        val restoredBranchingState = mapper.toRuntime(mapper.toStored(runtimeState))

        assertEquals(runtimeState, restoredBranchingState)

        val stickyFactsState = MemoryState(
            messages = listOf(
                ChatMessage(ChatRole.SYSTEM, "system"),
                ChatMessage(ChatRole.USER, "u1")
            ),
            strategyState = StickyFactsStrategyState(
                facts = mapOf("goal" to "Собрать ТЗ"),
                coveredMessagesCount = 1
            ),
            metadata = MemoryMetadata(strategyType = MemoryStrategyType.STICKY_FACTS)
        )

        val restoredStickyFactsState = mapper.toRuntime(mapper.toStored(stickyFactsState))

        assertEquals(stickyFactsState, restoredStickyFactsState)
    }
}
