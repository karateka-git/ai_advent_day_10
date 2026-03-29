import agent.core.AgentTokenStats
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.TokenUsage
import ui.cli.ConsoleTokenStatsFormatter

class ConsoleTokenStatsFormatterTest {
    private val formatter = ConsoleTokenStatsFormatter()

    @Test
    fun `formatPreview returns local token stats block`() {
        val formatted = formatter.formatPreview(
            AgentTokenStats(
                historyTokens = 74,
                promptTokensLocal = 77,
                userPromptTokens = 3,
                apiUsage = TokenUsage(
                    promptTokens = 77,
                    completionTokens = 6,
                    totalTokens = 83
                )
            )
        )

        assertEquals(
            """
            Оценка перед запросом:
              текущее сообщение: 3
              история диалога: 74
              полный запрос: 77
            """.trimIndent().normalizeLineSeparators(),
            formatted?.normalizeLineSeparators()
        )
    }

    @Test
    fun `formatResponse returns api token stats block`() {
        val formatted = formatter.formatResponse(
            AgentTokenStats(
                apiUsage = TokenUsage(
                    promptTokens = 77,
                    completionTokens = 6,
                    totalTokens = 83
                )
            )
        )

        assertEquals(
            """
            Токены ответа:
              запрос: 77
              ответ: 6
              всего: 83
            """.trimIndent().normalizeLineSeparators(),
            formatted?.normalizeLineSeparators()
        )
    }

    private fun String.normalizeLineSeparators(): String =
        replace("\r\n", "\n")
}
