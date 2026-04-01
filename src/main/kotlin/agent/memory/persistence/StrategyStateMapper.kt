package agent.memory.persistence

import agent.memory.model.BranchCheckpointState
import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.memory.model.ConversationSummary
import agent.memory.model.StickyFactsStrategyState
import agent.memory.model.StrategyState
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.MemoryStrategyType
import agent.storage.mapper.ConversationMapper
import agent.storage.model.StoredBranchCheckpoint
import agent.storage.model.StoredBranchConversation
import agent.storage.model.StoredStrategyState
import agent.storage.model.StoredSummary

/**
 * Преобразует strategy-specific runtime state в persisted state и обратно.
 */
class StrategyStateMapper(
    private val messageMapper: ConversationMapper
) {
    /**
     * Преобразует strategy-specific persisted state в runtime-state.
     */
    fun toRuntime(storedStrategyState: StoredStrategyState?): StrategyState? {
        if (storedStrategyState == null) {
            return null
        }

        return when (storedStrategyState.strategyType?.let(MemoryStrategyType::fromId)) {
            MemoryStrategyType.SUMMARY_COMPRESSION -> SummaryStrategyState(
                summary = storedStrategyState.summary?.toRuntimeSummary()
            )
            MemoryStrategyType.STICKY_FACTS -> StickyFactsStrategyState(
                facts = storedStrategyState.facts,
                coveredMessagesCount = storedStrategyState.factsCoveredMessagesCount
            )
            MemoryStrategyType.BRANCHING -> BranchingStrategyState(
                activeBranchName = storedStrategyState.activeBranchName ?: BranchingStrategyState.DEFAULT_BRANCH_NAME,
                latestCheckpointName = storedStrategyState.latestCheckpointName,
                checkpoints = storedStrategyState.checkpoints.map { checkpoint ->
                    BranchCheckpointState(
                        name = checkpoint.name,
                        messages = checkpoint.messages.map(messageMapper::fromStoredMessage)
                    )
                },
                branches = storedStrategyState.branches.map { branch ->
                    BranchConversationState(
                        name = branch.name,
                        sourceCheckpointName = branch.sourceCheckpointName,
                        messages = branch.messages.map(messageMapper::fromStoredMessage)
                    )
                }
            )
            else -> null
        }
    }

    /**
     * Преобразует runtime strategy-specific state в persisted state.
     */
    fun toStored(strategyState: StrategyState?): StoredStrategyState? =
        when (strategyState) {
            null -> null
            is StickyFactsStrategyState -> StoredStrategyState(
                strategyType = strategyState.strategyType.id,
                facts = strategyState.facts,
                factsCoveredMessagesCount = strategyState.coveredMessagesCount
            )
            is BranchingStrategyState -> StoredStrategyState(
                strategyType = strategyState.strategyType.id,
                activeBranchName = strategyState.activeBranchName,
                latestCheckpointName = strategyState.latestCheckpointName,
                checkpoints = strategyState.checkpoints.map { checkpoint ->
                    StoredBranchCheckpoint(
                        name = checkpoint.name,
                        messages = checkpoint.messages.map(messageMapper::toStoredMessage)
                    )
                },
                branches = strategyState.branches.map { branch ->
                    StoredBranchConversation(
                        name = branch.name,
                        sourceCheckpointName = branch.sourceCheckpointName,
                        messages = branch.messages.map(messageMapper::toStoredMessage)
                    )
                }
            )
            is SummaryStrategyState -> StoredStrategyState(
                strategyType = strategyState.strategyType.id,
                summary = strategyState.summary?.toStoredSummary()
            )
        }

    private fun StoredSummary.toRuntimeSummary(): ConversationSummary =
        ConversationSummary(
            content = content,
            coveredMessagesCount = coveredMessagesCount
        )

    private fun ConversationSummary.toStoredSummary(): StoredSummary =
        StoredSummary(
            content = content,
            coveredMessagesCount = coveredMessagesCount
        )
}
