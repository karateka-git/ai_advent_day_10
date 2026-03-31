package devtools.comparison

import agent.memory.MemoryStrategyOption
import java.time.Instant

/**
 * Выполняет один и тот же сценарий на конкретной стратегии памяти
 * и возвращает структурированный отчёт о прогоне.
 */
fun interface StrategyConversationExecutor {
    fun execute(
        option: MemoryStrategyOption,
        scenario: StrategyComparisonScenario
    ): StrategyExecutionReport
}

/**
 * Собирает общий отчёт сравнения нескольких стратегий памяти на одном сценарии.
 */
class StrategyComparisonService(
    private val generatedAtProvider: () -> String = { Instant.now().toString() }
) {
    /**
     * Прогоняет сценарий на переданных стратегиях и формирует единый сериализуемый отчёт.
     */
    fun compare(
        selectedModelId: String,
        providerModelName: String,
        scenario: StrategyComparisonScenario,
        strategies: List<MemoryStrategyOption>,
        executor: StrategyConversationExecutor
    ): StrategyComparisonReport =
        compare(
            selectedModelId = selectedModelId,
            providerModelName = providerModelName,
            scenario = scenario,
            strategies = strategies,
            executor = executor,
            onStrategyStarted = { _, _, _ -> },
            onStrategyFinished = { _, _, _, _ -> }
        )

    /**
     * Прогоняет сценарий с колбэками прогресса по стратегиям и возвращает итоговый отчёт.
     */
    fun compare(
        selectedModelId: String,
        providerModelName: String,
        scenario: StrategyComparisonScenario,
        strategies: List<MemoryStrategyOption>,
        executor: StrategyConversationExecutor,
        onStrategyStarted: (option: MemoryStrategyOption, index: Int, total: Int) -> Unit,
        onStrategyFinished: (
            option: MemoryStrategyOption,
            execution: StrategyExecutionReport,
            completedExecutions: List<StrategyExecutionReport>,
            total: Int
        ) -> Unit
    ): StrategyComparisonReport {
        val executions = mutableListOf<StrategyExecutionReport>()

        strategies.forEachIndexed { index, option ->
            onStrategyStarted(option, index + 1, strategies.size)
            val execution = executor.execute(option, scenario)
            executions += execution
            onStrategyFinished(option, execution, executions.toList(), strategies.size)
        }

        return createReport(
            selectedModelId = selectedModelId,
            providerModelName = providerModelName,
            scenario = scenario,
            executions = executions
        )
    }

    /**
     * Строит сериализуемый отчёт из уже собранных результатов прогонов.
     */
    fun createReport(
        selectedModelId: String,
        providerModelName: String,
        scenario: StrategyComparisonScenario,
        executions: List<StrategyExecutionReport>,
        judgeResult: StrategyComparisonJudgeResult? = null
    ): StrategyComparisonReport =
        StrategyComparisonReport(
            scenarioName = scenario.name,
            selectedModelId = selectedModelId,
            providerModelName = providerModelName,
            generatedAt = generatedAtProvider(),
            executions = executions,
            judgeInput = StrategyComparisonJudgeInput(
                scenarioName = scenario.name,
                prompts = scenario.prompts,
                candidates = executions.map { execution ->
                    StrategyJudgeCandidate(
                        strategyId = execution.strategyId,
                        strategyDisplayName = execution.strategyDisplayName,
                        finalResponse = execution.finalResponse,
                        totalLocalPromptTokens = execution.totalLocalPromptTokens,
                        totalProviderTokens = execution.totalProviderTokens
                    )
                }
            ),
            judgeResult = judgeResult
        )
}
