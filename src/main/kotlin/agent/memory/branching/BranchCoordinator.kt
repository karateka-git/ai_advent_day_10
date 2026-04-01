package agent.memory.branching

import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.memory.model.BranchCheckpointState
import agent.memory.model.BranchConversationState
import agent.memory.model.BranchingStrategyState
import agent.memory.model.MemoryState

/**
 * Инкапсулирует доменную логику checkpoint и веток диалога.
 */
class BranchCoordinator {
    /**
     * Создаёт checkpoint из текущей активной ветки.
     */
    fun createCheckpoint(
        state: MemoryState,
        name: String?
    ): CheckpointCreationResult {
        val branchingState = requireBranchingState(state)
        val checkpointName = normalizeBranchingName(name)
            ?: "checkpoint-${branchingState.checkpoints.size + 1}"
        require(branchingState.checkpoints.none { it.name.equals(checkpointName, ignoreCase = true) }) {
            "Checkpoint '$checkpointName' уже существует."
        }

        val updatedState = state.copy(
            strategyState = branchingState.copy(
                latestCheckpointName = checkpointName,
                checkpoints = branchingState.checkpoints + BranchCheckpointState(
                    name = checkpointName,
                    messages = state.messages
                )
            )
        )

        return CheckpointCreationResult(
            state = updatedState,
            info = BranchCheckpointInfo(
                name = checkpointName,
                sourceBranchName = branchingState.activeBranchName
            )
        )
    }

    /**
     * Создаёт новую ветку из последнего checkpoint.
     */
    fun createBranch(
        state: MemoryState,
        name: String
    ): BranchCreationResult {
        val branchingState = requireBranchingState(state)
        val branchName = normalizeRequiredBranchingName(name, "ветки")
        require(branchingState.branches.none { it.name.equals(branchName, ignoreCase = true) }) {
            "Ветка '$branchName' уже существует."
        }

        val checkpointName = branchingState.latestCheckpointName
            ?: error("Сначала создайте checkpoint командой checkpoint.")
        val checkpoint = branchingState.checkpoints.firstOrNull { it.name == checkpointName }
            ?: error("Последний checkpoint '$checkpointName' не найден.")

        val updatedState = state.copy(
            strategyState = branchingState.copy(
                branches = branchingState.branches + BranchConversationState(
                    name = branchName,
                    sourceCheckpointName = checkpoint.name,
                    messages = checkpoint.messages
                )
            )
        )

        return BranchCreationResult(
            state = updatedState,
            info = BranchInfo(
                name = branchName,
                sourceCheckpointName = checkpoint.name,
                isActive = false
            )
        )
    }

    /**
     * Переключает активную ветку.
     */
    fun switchBranch(
        state: MemoryState,
        name: String
    ): BranchSwitchResult {
        val branchingState = requireBranchingState(state)
        val branchName = normalizeRequiredBranchingName(name, "ветки")
        val branch = branchingState.branches.firstOrNull { it.name.equals(branchName, ignoreCase = true) }
            ?: error("Ветка '$branchName' не найдена.")

        val updatedState = state.copy(
            messages = branch.messages,
            strategyState = branchingState.copy(activeBranchName = branch.name)
        )

        return BranchSwitchResult(
            state = updatedState,
            info = BranchInfo(
                name = branch.name,
                sourceCheckpointName = branch.sourceCheckpointName,
                isActive = true
            )
        )
    }

    /**
     * Возвращает текущее состояние ветвления.
     */
    fun branchStatus(state: MemoryState): BranchingStatus {
        val branchingState = requireBranchingState(state)
        return BranchingStatus(
            activeBranchName = branchingState.activeBranchName,
            latestCheckpointName = branchingState.latestCheckpointName,
            branches = branchingState.branches.map { branch ->
                BranchInfo(
                    name = branch.name,
                    sourceCheckpointName = branch.sourceCheckpointName,
                    isActive = branch.name == branchingState.activeBranchName
                )
            }
        )
    }

    private fun requireBranchingState(state: MemoryState): BranchingStrategyState {
        val branchingState = state.strategyState as? BranchingStrategyState
        return branchingState
            ?: error("Состояние ветвления не инициализировано.")
    }

    private fun normalizeBranchingName(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeRequiredBranchingName(name: String, entityName: String): String =
        normalizeBranchingName(name) ?: error("Имя $entityName не может быть пустым.")
}

/**
 * Результат создания checkpoint вместе с обновлённым состоянием памяти.
 */
data class CheckpointCreationResult(
    val state: MemoryState,
    val info: BranchCheckpointInfo
)

/**
 * Результат создания ветки вместе с обновлённым состоянием памяти.
 */
data class BranchCreationResult(
    val state: MemoryState,
    val info: BranchInfo
)

/**
 * Результат переключения ветки вместе с обновлённым состоянием памяти.
 */
data class BranchSwitchResult(
    val state: MemoryState,
    val info: BranchInfo
)
