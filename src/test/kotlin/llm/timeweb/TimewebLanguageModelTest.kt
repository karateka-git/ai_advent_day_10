package llm.timeweb

import java.io.EOFException
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.PushPromiseHandler
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Optional
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class TimewebLanguageModelTest {
    @Test
    fun `complete retries after temporary eof and returns response`() {
        val httpClient = FakeHttpClient(
            responses = listOf(
                EOFException("connection dropped"),
                FakeHttpResponse(
                    statusCode = 200,
                    body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Готово"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 10,
                        "completion_tokens": 5,
                        "total_tokens": 15
                      }
                    }
                    """.trimIndent()
                )
            )
        )
        val model = TimewebLanguageModel(
            httpClient = httpClient,
            agentId = "agent",
            userToken = "token"
        )

        val response = model.complete(listOf(ChatMessage(ChatRole.USER, "Привет")))

        assertEquals("Готово", response.content)
        assertEquals(2, httpClient.sendCalls)
    }

    @Test
    fun `complete does not retry api status errors`() {
        val httpClient = FakeHttpClient(
            responses = listOf(
                FakeHttpResponse(
                    statusCode = 403,
                    body = """{"error":"agent_not_active"}"""
                )
            )
        )
        val model = TimewebLanguageModel(
            httpClient = httpClient,
            agentId = "agent",
            userToken = "token"
        )

        assertFailsWith<IllegalStateException> {
            model.complete(listOf(ChatMessage(ChatRole.USER, "Привет")))
        }
        assertEquals(1, httpClient.sendCalls)
    }
}

private class FakeHttpClient(
    private val responses: List<Any>
) : HttpClient() {
    var sendCalls: Int = 0
        private set

    override fun <T : Any?> send(
        request: HttpRequest?,
        responseBodyHandler: BodyHandler<T>?
    ): HttpResponse<T> {
        val next = responses[sendCalls++]
        @Suppress("UNCHECKED_CAST")
        return when (next) {
            is Exception -> throw next
            is HttpResponse<*> -> next as HttpResponse<T>
            else -> error("Unsupported fake response: $next")
        }
    }

    override fun <T : Any?> sendAsync(
        request: HttpRequest?,
        responseBodyHandler: BodyHandler<T>?
    ) = throw UnsupportedOperationException()

    override fun <T : Any?> sendAsync(
        request: HttpRequest?,
        responseBodyHandler: BodyHandler<T>?,
        pushPromiseHandler: PushPromiseHandler<T>?
    ) = throw UnsupportedOperationException()

    override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()

    override fun connectTimeout(): Optional<Duration> = Optional.empty()

    override fun followRedirects(): Redirect = Redirect.NEVER

    override fun proxy(): Optional<ProxySelector> = Optional.empty()

    override fun sslContext(): SSLContext = insecureSslContext()

    override fun sslParameters(): SSLParameters = SSLParameters()

    override fun authenticator(): Optional<java.net.Authenticator> = Optional.empty()

    override fun version(): Version = Version.HTTP_1_1

    override fun executor(): Optional<Executor> = Optional.empty()
}

private data class FakeHttpResponse(
    private val statusCode: Int,
    private val body: String
) : HttpResponse<String> {
    override fun statusCode(): Int = statusCode

    override fun request(): HttpRequest = HttpRequest.newBuilder().uri(URI.create("https://example.com")).build()

    override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

    override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

    override fun body(): String = body

    override fun sslSession(): Optional<javax.net.ssl.SSLSession> = Optional.empty()

    override fun uri(): URI = URI.create("https://example.com")

    override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
}

private fun insecureSslContext(): SSLContext {
    val trustAll = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
    )

    return SSLContext.getInstance("TLS").apply {
        init(null, trustAll, SecureRandom())
    }
}
