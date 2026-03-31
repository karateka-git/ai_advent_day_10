package agent.memory

import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.model.SummaryStrategyState
import agent.memory.summarizer.ConversationSummarizer
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

/**
 * Стратегия памяти, которая хранит rolling summary поверх полной истории сообщений
 * и отправляет в модель summary вместе с ещё не покрытым хвостом диалога.
 */
class SummaryCompressionMemoryStrategy(
    private val recentMessagesCount: Int,
    private val summaryBatchSize: Int,
    private val summarizer: ConversationSummarizer
) : MemoryStrategy {
    init {
        require(recentMessagesCount > 0) {
            "Количество последних сообщений должно быть больше нуля."
        }
        require(summaryBatchSize > 0) {
            "Размер пачки для summary должен быть больше нуля."
        }
    }

    override val type: MemoryStrategyType = MemoryStrategyType.SUMMARY_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> {
        val systemMessages = state.messages.filter { it.role == ChatRole.SYSTEM }
        val dialogMessages = state.messages.filter { it.role != ChatRole.SYSTEM }
        val coveredMessagesCount = coveredMessagesCount(state)
        val uncompressedTail = dialogMessages.drop(coveredMessagesCount)

        val summary = summary(state)
        if (summary == null && coveredMessagesCount == 0) {
            return state.messages.toList()
        }

        return buildList {
            addAll(systemMessages)
            summary?.let(::toSummaryMessage)?.let(::add)
            addAll(uncompressedTail)
        }
    }

    override fun refreshState(state: MemoryState): MemoryState {
        val preparedState = prepareStateForSummary(state)
        var currentState = preparedState

        while (true) {
            val dialogMessages = currentState.messages.filter { it.role != ChatRole.SYSTEM }
            val uncompressedMessages = dialogMessages.drop(coveredMessagesCount(currentState))
            val messagesEligibleForCompression = uncompressedMessages.dropLastSafe(recentMessagesCount)

            if (messagesEligibleForCompression.size < summaryBatchSize) {
                return currentState
            }

            val nextBatch = messagesEligibleForCompression.take(summaryBatchSize)
            val updatedCoveredMessagesCount = coveredMessagesCount(currentState) + nextBatch.size
            val summaryContent = buildUpdatedSummary(summary(currentState), nextBatch)

            currentState = currentState.copy(
                strategyState = SummaryStrategyState(
                    summary = ConversationSummary(
                        content = summaryContent,
                        coveredMessagesCount = updatedCoveredMessagesCount
                    )
                ),
                metadata = currentState.metadata.copy(
                    compressedMessagesCount = updatedCoveredMessagesCount
                )
            )
        }
    }

    /**
     * При первом включении summary после другой стратегии начинает свёртку
     * только с текущего окна, а не со всей накопленной истории.
     */
    private fun prepareStateForSummary(state: MemoryState): MemoryState {
        if (state.metadata.strategyType == type) {
            return state
        }

        val dialogMessages = state.messages.filter { it.role != ChatRole.SYSTEM }
        val activationWindowSize = recentMessagesCount + summaryBatchSize
        val coveredMessagesCount = (dialogMessages.size - activationWindowSize).coerceAtLeast(0)

        return state.copy(
            strategyState = null,
            metadata = state.metadata.copy(
                compressedMessagesCount = coveredMessagesCount
            )
        )
    }

    /**
     * Возвращает абсолютное количество сообщений, уже покрытых summary или пропущенных
     * при позднем включении стратегии.
     */
    private fun coveredMessagesCount(state: MemoryState): Int =
        summary(state)?.coveredMessagesCount ?: state.metadata.compressedMessagesCount

    /**
     * Возвращает strategy-specific summary state только если он совместим с текущей стратегией.
     */
    private fun summary(state: MemoryState): ConversationSummary? =
        (state.strategyState as? SummaryStrategyState)
            ?.takeIf { it.strategyType == type }
            ?.summary

    /**
     * Объединяет текущее rolling summary со следующей порцией старых несжатых сообщений.
     */
    private fun buildUpdatedSummary(
        existingSummary: ConversationSummary?,
        nextBatch: List<ChatMessage>
    ): String {
        val messagesForSummary = buildList {
            existingSummary?.let { summary ->
                add(
                    ChatMessage(
                        role = ChatRole.SYSTEM,
                        content = "Предыдущее резюме: ${summary.content}"
                    )
                )
            }
            addAll(nextBatch)
        }

        return summarizer.summarize(messagesForSummary)
    }

    /**
     * Оборачивает сохранённый текст summary в системное сообщение для effective prompt.
     */
    private fun toSummaryMessage(summary: ConversationSummary): ChatMessage =
        ChatMessage(
            role = ChatRole.SYSTEM,
            content = "Краткое резюме предыдущего диалога:\n${summary.content}"
        )

    /**
     * Безопасно отбрасывает хвост нужной длины, даже если список короче указанного количества.
     */
    private fun <T> List<T>.dropLastSafe(count: Int): List<T> =
        if (count >= size) {
            emptyList()
        } else {
            dropLast(count)
        }
}
