package agent.memory.summarizer

import kotlin.test.Test
import kotlin.test.assertContains
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
        val firstUserMessage = "Меня зовут Илья."
        val assistantMessage = "Приятно познакомиться."
        val secondUserMessage = "Мне важна экономия токенов."

        val summary = summarizer.summarize(
            listOf(
                ChatMessage(role = ChatRole.USER, content = firstUserMessage),
                ChatMessage(role = ChatRole.ASSISTANT, content = assistantMessage),
                ChatMessage(role = ChatRole.USER, content = secondUserMessage)
            )
        )

        assertEquals("Краткое summary", summary)
        assertEquals(2, languageModel.recordedMessages.size)
        assertEquals(ChatRole.SYSTEM, languageModel.recordedMessages[0].role)
        assertEquals(ChatRole.USER, languageModel.recordedMessages[1].role)
        assertContains(languageModel.recordedMessages[0].content, "summary")
        assertContains(languageModel.recordedMessages[1].content, ChatRole.USER.displayName)
        assertContains(languageModel.recordedMessages[1].content, ChatRole.ASSISTANT.displayName)
        assertContains(languageModel.recordedMessages[1].content, firstUserMessage)
        assertContains(languageModel.recordedMessages[1].content, assistantMessage)
        assertContains(languageModel.recordedMessages[1].content, secondUserMessage)
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
