package agent.capability

import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus

/**
 * Дополнительные операции стратегии ветвления.
 */
interface BranchingCapability : AgentCapability {
    /**
     * Создаёт checkpoint из текущего активного состояния диалога.
     */
    fun createCheckpoint(name: String? = null): BranchCheckpointInfo

    /**
     * Создаёт новую ветку из последнего checkpoint.
     */
    fun createBranch(name: String): BranchInfo

    /**
     * Переключает активную ветку диалога.
     */
    fun switchBranch(name: String): BranchInfo

    /**
     * Возвращает текущее состояние ветвления диалога.
     */
    fun branchStatus(): BranchingStatus
}
