package agent.memory

import agent.memory.summarizer.LlmConversationSummarizer
import llm.core.LanguageModel

/**
 * Создаёт стратегии памяти, которые можно выбрать в рамках CLI-сессии.
 */
object MemoryStrategyFactory {
    private const val DEFAULT_RECENT_MESSAGES_COUNT = 2
    private const val DEFAULT_SUMMARY_BATCH_SIZE = 3
    private val DEFAULT_STRATEGY_TYPE = MemoryStrategyType.NO_COMPRESSION

    /**
     * Возвращает список стратегий, доступных пользователю.
     */
    fun availableOptions(): List<MemoryStrategyOption> =
        listOf(
            MemoryStrategyOption(
                type = MemoryStrategyType.NO_COMPRESSION,
                displayName = "Без сжатия",
                description = "Отправляет в модель всю историю как есть."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.SUMMARY_COMPRESSION,
                displayName = "Сжатие через summary",
                description =
                    "Когда накопится минимум 5 сообщений диалога, сворачивает старую часть пачками по 3 сообщения " +
                        "и оставляет последние 2 сообщения вне summary."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.SLIDING_WINDOW,
                displayName = "Скользящее окно",
                description = "Всегда оставляет в контексте системные сообщения и только последние 2 сообщения диалога."
            )
        )

    /**
     * Возвращает стратегию памяти по умолчанию для запуска CLI-сессии.
     */
    fun defaultOption(): MemoryStrategyOption =
        availableOptions().first { it.type == DEFAULT_STRATEGY_TYPE }

    /**
     * Создаёт экземпляр стратегии для выбранной опции.
     */
    fun create(
        strategyType: MemoryStrategyType,
        languageModel: LanguageModel
    ): MemoryStrategy =
        when (strategyType) {
            MemoryStrategyType.NO_COMPRESSION -> NoCompressionMemoryStrategy()
            MemoryStrategyType.SUMMARY_COMPRESSION -> SummaryCompressionMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT,
                summaryBatchSize = DEFAULT_SUMMARY_BATCH_SIZE,
                summarizer = LlmConversationSummarizer(languageModel)
            )
            MemoryStrategyType.SLIDING_WINDOW -> SlidingWindowMemoryStrategy(
                recentMessagesCount = DEFAULT_RECENT_MESSAGES_COUNT
            )
        }
}

/**
 * Пользовательское описание стратегии памяти, доступной для выбора.
 *
 * @property type доменный тип стратегии.
 * @property displayName имя стратегии в пользовательском интерфейсе.
 * @property description краткое описание поведения стратегии.
 */
data class MemoryStrategyOption(
    val type: MemoryStrategyType,
    val displayName: String,
    val description: String
) {
    /**
     * Устойчивый строковый идентификатор стратегии для CLI, storage и отчётов.
     */
    val id: String
        get() = type.id
}
