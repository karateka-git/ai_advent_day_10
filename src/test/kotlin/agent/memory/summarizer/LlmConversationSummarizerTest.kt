package agent.memory.summarizer

import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class LlmConversationSummarizerTest {
    @Test
    fun `summarize sends dedicated summary prompt to language model`() {
        val languageModel = RecordingLanguageModel("Краткое summary")
        val summarizer = LlmConversationSummarizer(languageModel)

        val summary = summarizer.summarize(
            listOf(
                ChatMessage(role = ChatRole.USER, content = "Меня зовут Илья."),
                ChatMessage(role = ChatRole.ASSISTANT, content = "Приятно познакомиться."),
                ChatMessage(role = ChatRole.USER, content = "Мне важна экономия токенов.")
            )
        )

        assertEquals("Краткое summary", summary)
        assertEquals(
            listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content =
                        "Ты делаешь краткое резюме фрагмента диалога. " +
                            "Сохраняй только важные факты, ограничения, цели пользователя, договорённости и незавершённые задачи. " +
                            "Не выдумывай факты. Пиши кратко, по-русски, в виде связного summary без лишнего вступления."
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content =
                        "Сожми следующий фрагмент диалога:\n\n" +
                            "Пользователь: Меня зовут Илья.\n" +
                            "Ассистент: Приятно познакомиться.\n" +
                            "Пользователь: Мне важна экономия токенов."
                )
            ),
            languageModel.recordedMessages
        )
    }
}

private class RecordingLanguageModel(
    private val responseContent: String
) : LanguageModel {
    var recordedMessages: List<ChatMessage> = emptyList()
        private set

    override val info = LanguageModelInfo(
        name = "RecordingLanguageModel",
        model = "recording-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
        recordedMessages = messages
        return LanguageModelResponse(content = responseContent)
    }
}
