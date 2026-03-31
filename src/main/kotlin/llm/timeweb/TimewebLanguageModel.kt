package llm.timeweb

import java.net.URI
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter
import llm.timeweb.mapper.ChatCompletionResponseMapper
import llm.timeweb.model.ChatCompletionRequest
import llm.timeweb.model.ChatCompletionResponse
import llm.timeweb.tokenizer.DeepSeekLocalTokenCounter

private const val MODEL = "DeepSeek V3.2"
private const val DEFAULT_TEMPERATURE = 0.7
private const val MAX_RETRY_ATTEMPTS = 3
private const val API_URL_TEMPLATE =
    "https://agent.timeweb.cloud/api/v1/cloud-ai/agents/%s/v1/chat/completions"

/**
 * Реализация [LanguageModel] для Timeweb.
 */
class TimewebLanguageModel(
    private val httpClient: HttpClient,
    private val agentId: String,
    private val userToken: String,
    private val temperature: Double = DEFAULT_TEMPERATURE
) : LanguageModel {
    private val json = Json { ignoreUnknownKeys = true }
    private val responseMapper = ChatCompletionResponseMapper()

    override val info = LanguageModelInfo(
        name = "TimewebLanguageModel",
        model = MODEL
    )
    override val tokenCounter: TokenCounter = DeepSeekLocalTokenCounter()

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse {
        val requestBody = json.encodeToString(
            ChatCompletionRequest(
                model = MODEL,
                messages = messages,
                temperature = temperature
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL_TEMPLATE.format(agentId)))
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Authorization", "Bearer $userToken")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build()

        val response = sendWithRetry(request)

        if (response.statusCode() !in 200..299) {
            error("API вернул статус ${response.statusCode()}: ${response.body()}")
        }

        val completion = json.decodeFromString<ChatCompletionResponse>(response.body())
        return responseMapper.toLanguageModelResponse(completion)
    }

    /**
     * Повторяет запрос при временных сетевых сбоях, которые можно переждать без смены параметров.
     */
    private fun sendWithRetry(request: HttpRequest): HttpResponse<String> {
        var lastError: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            } catch (error: Exception) {
                if (!error.isRetriableNetworkFailure() || attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw error
                }

                lastError = error
            }
        }

        throw checkNotNull(lastError)
    }
}

/**
 * Отмечает временные сетевые ошибки, при которых повтор запроса обычно безопасен.
 */
private fun Exception.isRetriableNetworkFailure(): Boolean =
    when (this) {
        is java.io.EOFException,
        is java.io.IOException,
        is HttpConnectTimeoutException,
        is java.net.http.HttpTimeoutException,
        is SSLException -> true
        else -> false
    }
