package devtools.comparison

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyComparisonJudgeServiceTest {
    private val service = StrategyComparisonJudgeService()

    @Test
    fun `buildUserPrompt includes criteria and response schema`() {
        val prompt = service.buildUserPrompt(
            StrategyComparisonJudgeInput(
                scenarioName = "Сценарий",
                prompts = listOf("p1"),
                candidates = listOf(
                    StrategyJudgeCandidate(
                        strategyId = "no_compression",
                        strategyDisplayName = "Без сжатия",
                        finalResponse = "Ответ"
                    )
                )
            )
        )

        assertTrue(prompt.contains("qualityScore"))
        assertTrue(prompt.contains("stabilityScore"))
        assertTrue(prompt.contains("usabilityScore"))
        assertTrue(prompt.contains("\"ranking\""))
    }

    @Test
    fun `extractJsonObject supports fenced json`() {
        val json = service.extractJsonObject(
            """
            ```json
            {"summary":"ok","ranking":["no_compression"],"evaluations":[]}
            ```
            """.trimIndent()
        )

        assertEquals("""{"summary":"ok","ranking":["no_compression"],"evaluations":[]}""", json)
    }
}
