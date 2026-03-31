package devtools.comparison

import kotlinx.serialization.Serializable

/**
 * Описывает сценарий, который прогоняется на нескольких стратегиях памяти.
 *
 * @property name человекочитаемое название сценария сравнения.
 * @property prompts последовательность пользовательских сообщений для прогона.
 */
@Serializable
data class StrategyComparisonScenario(
    val name: String,
    val prompts: List<String>
)

/**
 * Хранит результат одного шага сценария для конкретной стратегии.
 *
 * @property stepNumber порядковый номер шага в сценарии.
 * @property userPrompt пользовательское сообщение, отправленное на этом шаге.
 * @property assistantResponse итоговый ответ агента на этом шаге.
 * @property historyTokens локальная оценка токенов истории до добавления нового запроса.
 * @property promptTokensLocal локальная оценка токенов полного prompt после добавления запроса.
 * @property userPromptTokens локальная оценка токенов пользовательского сообщения.
 * @property providerPromptTokens prompt-токены по данным провайдера модели.
 * @property providerCompletionTokens completion-токены по данным провайдера модели.
 * @property providerTotalTokens суммарные токены по данным провайдера модели.
 * @property modelCallCount общее число вызовов модели на шаге, включая внутренние.
 * @property internalModelCallCount число внутренних вызовов модели помимо финального ответа.
 * @property requestMessageCount число сообщений в последнем запросе к модели.
 * @property requestCharacterCount суммарная длина контекста последнего запроса в символах.
 * @property requestRoles список ролей сообщений в последнем запросе к модели.
 */
@Serializable
data class StrategyComparisonStepReport(
    val stepNumber: Int,
    val userPrompt: String,
    val assistantResponse: String,
    val historyTokens: Int? = null,
    val promptTokensLocal: Int? = null,
    val userPromptTokens: Int? = null,
    val providerPromptTokens: Int? = null,
    val providerCompletionTokens: Int? = null,
    val providerTotalTokens: Int? = null,
    val modelCallCount: Int,
    val internalModelCallCount: Int,
    val requestMessageCount: Int,
    val requestCharacterCount: Int,
    val requestRoles: List<String>
)

/**
 * Хранит агрегированный результат прогона сценария на одной стратегии памяти.
 *
 * @property strategyId стабильный идентификатор стратегии.
 * @property strategyDisplayName человекочитаемое имя стратегии.
 * @property strategyDescription описание стратегии для отчёта.
 * @property steps детальные результаты по шагам сценария.
 * @property totalLocalPromptTokens суммарная локальная оценка prompt-токенов по всем шагам.
 * @property totalProviderPromptTokens суммарные prompt-токены по данным провайдера.
 * @property totalProviderCompletionTokens суммарные completion-токены по данным провайдера.
 * @property totalProviderTokens суммарные токены по данным провайдера.
 * @property finalResponse финальный ответ стратегии на последнем шаге сценария.
 */
@Serializable
data class StrategyExecutionReport(
    val strategyId: String,
    val strategyDisplayName: String,
    val strategyDescription: String,
    val steps: List<StrategyComparisonStepReport>,
    val totalLocalPromptTokens: Int? = sumNullable(steps.map { it.promptTokensLocal }),
    val totalProviderPromptTokens: Int? = sumNullable(steps.map { it.providerPromptTokens }),
    val totalProviderCompletionTokens: Int? = sumNullable(steps.map { it.providerCompletionTokens }),
    val totalProviderTokens: Int? = sumNullable(steps.map { it.providerTotalTokens }),
    val finalResponse: String = steps.lastOrNull()?.assistantResponse.orEmpty()
)

/**
 * Представляет одного кандидата для будущего judge-сравнения.
 *
 * @property strategyId идентификатор стратегии.
 * @property strategyDisplayName человекочитаемое имя стратегии.
 * @property finalResponse финальный ответ стратегии на сценарий.
 * @property totalLocalPromptTokens суммарная локальная оценка prompt-токенов.
 * @property totalProviderTokens суммарные токены по данным провайдера.
 */
@Serializable
data class StrategyJudgeCandidate(
    val strategyId: String,
    val strategyDisplayName: String,
    val finalResponse: String,
    val totalLocalPromptTokens: Int? = null,
    val totalProviderTokens: Int? = null
)

/**
 * Подготавливает полезную нагрузку для отдельного judge-запроса в LLM.
 *
 * @property scenarioName название сценария сравнения.
 * @property prompts исходная последовательность пользовательских сообщений.
 * @property candidates стратегии-кандидаты для последующего судейства.
 */
@Serializable
data class StrategyComparisonJudgeInput(
    val scenarioName: String,
    val prompts: List<String>,
    val candidates: List<StrategyJudgeCandidate>
)

/**
 * Оценка одной стратегии со стороны LLM judge.
 *
 * @property strategyId идентификатор стратегии.
 * @property qualityScore оценка качества ответа по шкале от 1 до 10.
 * @property stabilityScore оценка сохранения важных деталей по шкале от 1 до 10.
 * @property usabilityScore оценка удобства ответа для пользователя по шкале от 1 до 10.
 * @property strengths сильные стороны стратегии.
 * @property weaknesses слабые стороны стратегии.
 * @property verdict короткий вывод по стратегии.
 */
@Serializable
data class StrategyJudgeEvaluation(
    val strategyId: String,
    val qualityScore: Int,
    val stabilityScore: Int,
    val usabilityScore: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val verdict: String
)

/**
 * Результат дополнительного judge-запроса в LLM.
 *
 * @property judgeModelId идентификатор модели, которая выступала судьёй.
 * @property summary общий краткий вывод по сравнению стратегий.
 * @property ranking ранжирование стратегий от лучшей к худшей.
 * @property evaluations детальные оценки по стратегиям.
 */
@Serializable
data class StrategyComparisonJudgeResult(
    val judgeModelId: String,
    val summary: String,
    val ranking: List<String>,
    val evaluations: List<StrategyJudgeEvaluation>
)

/**
 * Полный сериализуемый отчёт о сравнении стратегий памяти.
 *
 * @property scenarioName название использованного сценария.
 * @property selectedModelId идентификатор выбранной модели в конфигурации приложения.
 * @property providerModelName фактическое имя модели у провайдера.
 * @property generatedAt момент генерации отчёта.
 * @property executions результаты прогонов по стратегиям.
 * @property judgeInput данные, подготовленные для будущего judge-сравнения.
 * @property judgeResult дополнительная качественная оценка от LLM judge, если она была запрошена.
 */
@Serializable
data class StrategyComparisonReport(
    val scenarioName: String,
    val selectedModelId: String,
    val providerModelName: String,
    val generatedAt: String,
    val executions: List<StrategyExecutionReport>,
    val judgeInput: StrategyComparisonJudgeInput,
    val judgeResult: StrategyComparisonJudgeResult? = null
)

/**
 * Складывает только присутствующие значения и возвращает `null`, если все элементы отсутствуют.
 */
private fun sumNullable(values: List<Int?>): Int? {
    val presentValues = values.filterNotNull()
    return presentValues.takeIf { it.isNotEmpty() }?.sum()
}
