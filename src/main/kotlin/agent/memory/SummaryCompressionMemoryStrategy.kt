package agent.memory

import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.summarizer.ConversationSummarizer
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

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

    override val id: String = "summary_compression"

    override fun effectiveContext(state: MemoryState): List<ChatMessage> {
        if (state.summary == null) {
            return state.messages.toList()
        }

        val systemMessages = state.messages.filter { it.role == ChatRole.SYSTEM }
        val dialogMessages = state.messages.filter { it.role != ChatRole.SYSTEM }

        return systemMessages + toSummaryMessage(state.summary) + dialogMessages
    }

    override fun refreshState(state: MemoryState): MemoryState {
        var currentState = state

        while (true) {
            val systemMessages = currentState.messages.filter { it.role == ChatRole.SYSTEM }
            val dialogMessages = currentState.messages.filter { it.role != ChatRole.SYSTEM }
            val messagesEligibleForCompression = dialogMessages.dropLastSafe(recentMessagesCount)

            if (messagesEligibleForCompression.size < summaryBatchSize) {
                return currentState
            }

            val nextBatch = messagesEligibleForCompression.take(summaryBatchSize)
            val remainingDialogMessages = dialogMessages.drop(summaryBatchSize)
            val summaryContent = buildUpdatedSummary(currentState.summary, nextBatch)

            currentState = currentState.copy(
                messages = systemMessages + remainingDialogMessages,
                summary = ConversationSummary(
                    content = summaryContent,
                    coveredMessagesCount = (currentState.summary?.coveredMessagesCount ?: 0) + nextBatch.size
                ),
                metadata = currentState.metadata.copy(
                    compressedMessagesCount = currentState.metadata.compressedMessagesCount + nextBatch.size
                )
            )
        }
    }

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

    private fun toSummaryMessage(summary: ConversationSummary): List<ChatMessage> =
        listOf(
            ChatMessage(
                role = ChatRole.SYSTEM,
                content = "Краткое резюме предыдущего диалога:\n${summary.content}"
            )
        )

    private fun <T> List<T>.dropLastSafe(count: Int): List<T> =
        if (count >= size) {
            emptyList()
        } else {
            dropLast(count)
        }
}
