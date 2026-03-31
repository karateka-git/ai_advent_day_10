package devtools.comparison

import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

/**
 * Хранит один зафиксированный запрос к модели вместе с полученным ответом.
 *
 * @property messages сообщения, отправленные в модель.
 * @property response ответ модели на этот запрос.
 */
data class TracedModelRequest(
    val messages: List<ChatMessage>,
    val response: LanguageModelResponse
)

/**
 * Оборачивает реальную модель и записывает все вызовы для последующего анализа.
 */
class TracingLanguageModel(
    private val delegate: LanguageModel
) : LanguageModel {
    private val requests = mutableListOf<TracedModelRequest>()

    override val info: LanguageModelInfo
        get() = delegate.info

    override val tokenCounter: TokenCounter?
        get() = delegate.tokenCounter

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
        val response = delegate.complete(messages)
        requests += TracedModelRequest(
            messages = messages.map { it.copy() },
            response = response
        )
        return response
    }

    /**
     * Возвращает накопленные вызовы и очищает внутренний буфер трассировки.
     */
    fun drainRequests(): List<TracedModelRequest> =
        requests.toList().also { requests.clear() }
}
