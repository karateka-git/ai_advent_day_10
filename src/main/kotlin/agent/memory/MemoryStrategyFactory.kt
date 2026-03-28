package agent.memory

import agent.lifecycle.AgentLifecycleListener
import agent.memory.summarizer.LlmConversationSummarizer
import llm.core.LanguageModel

object MemoryStrategyFactory {
    private const val DEFAULT_RECENT_MESSAGES_COUNT = 2
    private const val DEFAULT_SUMMARY_BATCH_SIZE = 3

    fun availableOptions(): List<MemoryStrategyOption> =
        listOf(
            MemoryStrategyOption(
                id = "no_compression",
                displayName = "Без сжатия",
                description = "Отправляет в модель всю историю как есть."
            ),
            MemoryStrategyOption(
                id = "summary_compression",
                displayName = "Сжатие через summary",
                description = "Хранит краткое summary старой истории и последние сообщения без сжатия."
            )
        )

    fun create(
        strategyId: String,
        languageModel: LanguageModel,
        lifecycleListener: AgentLifecycleListener
    ): MemoryStrategy =
        when (strategyId) {
            "no_compression" -> NoCompressionMemoryStrategy()
            "summary_compression" -> SummaryCompressionMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT,
                summaryBatchSize = DEFAULT_SUMMARY_BATCH_SIZE,
                summarizer = LlmConversationSummarizer(languageModel)
            )

            else -> error("Неизвестная стратегия памяти: $strategyId")
        }
}

data class MemoryStrategyOption(
    val id: String,
    val displayName: String,
    val description: String
)
