package agent.capability

import agent.core.Agent
import agent.memory.core.MemoryManager

/**
 * Упрощает получение capability агента по reified-типу.
 */
inline fun <reified TCapability : AgentCapability> Agent<*>.capability(): TCapability? =
    capability(TCapability::class.java)

/**
 * Упрощает получение capability memory manager по reified-типу.
 */
inline fun <reified TCapability : AgentCapability> MemoryManager.capability(): TCapability? =
    capability(TCapability::class.java)
