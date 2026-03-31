package devtools.comparison

import agent.memory.MemoryStrategyOption
import agent.memory.MemoryStrategyType
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyComparisonServiceTest {
    @Test
    fun `builds report and judge payload for all strategies`() {
        val scenario = StrategyComparisonScenario(
            name = "Тестовый сценарий",
            prompts = listOf("p1", "p2")
        )
        val strategies = listOf(
            MemoryStrategyOption(
                type = MemoryStrategyType.NO_COMPRESSION,
                displayName = "Без сжатия",
                description = "Полная история."
            ),
            MemoryStrategyOption(
                type = MemoryStrategyType.SLIDING_WINDOW,
                displayName = "Скользящее окно",
                description = "Только хвост."
            )
        )
        val service = StrategyComparisonService(
            generatedAtProvider = { "2026-03-30T00:00:00Z" }
        )

        val report = service.compare(
            selectedModelId = "timeweb",
            providerModelName = "test-model",
            scenario = scenario,
            strategies = strategies,
            executor = StrategyConversationExecutor { option, _ ->
                StrategyExecutionReport(
                    strategyId = option.id,
                    strategyDisplayName = option.displayName,
                    strategyDescription = option.description,
                    steps = listOf(
                        StrategyComparisonStepReport(
                            stepNumber = 1,
                            userPrompt = "p1",
                            assistantResponse = "Ответ ${option.id}",
                            promptTokensLocal = 10,
                            providerTotalTokens = 12,
                            modelCallCount = 1,
                            internalModelCallCount = 0,
                            requestMessageCount = 2,
                            requestCharacterCount = 20,
                            requestRoles = listOf("SYSTEM", "USER")
                        )
                    )
                )
            }
        )

        assertEquals("Тестовый сценарий", report.scenarioName)
        assertEquals("timeweb", report.selectedModelId)
        assertEquals("test-model", report.providerModelName)
        assertEquals("2026-03-30T00:00:00Z", report.generatedAt)
        assertEquals(listOf("no_compression", "sliding_window"), report.executions.map { it.strategyId })
        assertEquals(listOf("Ответ no_compression", "Ответ sliding_window"), report.judgeInput.candidates.map { it.finalResponse })
    }
}
