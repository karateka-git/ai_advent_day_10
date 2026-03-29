import agent.core.Agent
import agent.memory.MemoryStrategyOption
import llm.core.LanguageModel

/**
 * Единое состояние CLI-сессии.
 *
 * Хранит активную модель, её идентификатор, текущий агент и выбранную стратегию памяти.
 */
data class CliSessionState(
    val modelId: String,
    val languageModel: LanguageModel,
    val agent: Agent<String>,
    val memoryStrategyOption: MemoryStrategyOption
)
