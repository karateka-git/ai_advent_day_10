package agent.memory.summarizer

import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class LlmConversationSummarizer(
    private val languageModel: LanguageModel
) : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String {
        require(messages.isNotEmpty()) {
            "Нельзя построить summary для пустого списка сообщений."
        }

        val response = languageModel.complete(
            listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = SUMMARY_SYSTEM_PROMPT
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content = buildSummaryInput(messages)
                )
            )
        )

        return response.content.trim()
    }

    private fun buildSummaryInput(messages: List<ChatMessage>): String =
        buildString {
            appendLine("Сожми следующий фрагмент диалога:")
            appendLine()
            messages.forEach { message ->
                append(message.role.displayName)
                append(": ")
                appendLine(message.content)
            }
        }.trimEnd()

    companion object {
        private const val SUMMARY_SYSTEM_PROMPT =
            "Ты делаешь краткое резюме фрагмента диалога. " +
                "Сохраняй только важные факты, ограничения, цели пользователя, договорённости и незавершённые задачи. " +
                "Не выдумывай факты. Пиши кратко, по-русски, в виде связного summary без лишнего вступления."
    }
}
