package devtools.comparison

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StrategyComparisonConsoleFormatterTest {
    @Test
    fun `format includes judge section when judge result is available`() {
        val report = StrategyComparisonReport(
            scenarioName = "Сценарий",
            selectedModelId = "timeweb",
            providerModelName = "DeepSeek V3.2",
            generatedAt = "2026-03-31T00:00:00Z",
            executions = listOf(
                StrategyExecutionReport(
                    strategyId = "summary_compression",
                    strategyDisplayName = "Сжатие через summary",
                    strategyDescription = "Описание",
                    steps = listOf(
                        StrategyComparisonStepReport(
                            stepNumber = 1,
                            userPrompt = "p1",
                            assistantResponse = "Ответ",
                            promptTokensLocal = 10,
                            providerPromptTokens = 12,
                            providerCompletionTokens = 8,
                            providerTotalTokens = 20,
                            modelCallCount = 1,
                            internalModelCallCount = 0,
                            requestMessageCount = 2,
                            requestCharacterCount = 30,
                            requestRoles = listOf("SYSTEM", "USER")
                        )
                    )
                )
            ),
            judgeInput = StrategyComparisonJudgeInput(
                scenarioName = "Сценарий",
                prompts = listOf("p1"),
                candidates = listOf(
                    StrategyJudgeCandidate(
                        strategyId = "summary_compression",
                        strategyDisplayName = "Сжатие через summary",
                        finalResponse = "Ответ",
                        totalLocalPromptTokens = 10,
                        totalProviderTokens = 20
                    )
                )
            ),
            judgeResult = StrategyComparisonJudgeResult(
                judgeModelId = "timeweb",
                summary = "Summary лучше держит детали.",
                ranking = listOf("summary_compression"),
                evaluations = listOf(
                    StrategyJudgeEvaluation(
                        strategyId = "summary_compression",
                        qualityScore = 9,
                        stabilityScore = 8,
                        usabilityScore = 7,
                        strengths = listOf("Держит контекст"),
                        weaknesses = listOf("Тратит больше токенов"),
                        verdict = "Хороший баланс качества и стабильности."
                    )
                )
            )
        )

        val formatted = StrategyComparisonConsoleFormatter.format(report, Path.of("report.json"))

        assertTrue(formatted.contains("Финальный вывод judge"))
        assertTrue(formatted.contains("--------------------------------------------------"))
        assertTrue(formatted.contains("Общий итог:"))
        assertTrue(formatted.contains("Рейтинг:"))
        assertTrue(formatted.contains("Оценки:"))
        assertTrue(formatted.contains("Модель-судья:"))
        assertTrue(formatted.contains("  timeweb"))
        assertTrue(formatted.contains("summary_compression: качество=9, стабильность=8, удобство=7"))
        assertTrue(
            formatted.indexOf("summary_compression (Сжатие через summary)") <
                formatted.indexOf("Финальный вывод judge")
        )
    }
}
