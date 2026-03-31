package devtools.comparison

import agent.format.TextResponseFormat
import agent.impl.MrAgent
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.DefaultMemoryManager
import agent.memory.MemoryStrategyFactory
import agent.memory.MemoryStrategyOption
import agent.storage.JsonConversationStore
import java.nio.file.Files
import java.nio.file.Path
import llm.core.LanguageModel

/**
 * Выполняет сценарий сравнения на реальном агенте и собирает метрики по каждому шагу.
 */
class DefaultStrategyConversationExecutor(
    private val baseLanguageModel: LanguageModel,
    private val stateDirectory: Path,
    private val onStepStarted: (option: MemoryStrategyOption, stepNumber: Int, totalSteps: Int) -> Unit = { _, _, _ -> },
    private val onStepFinished: (option: MemoryStrategyOption, step: StrategyComparisonStepReport, totalSteps: Int) -> Unit = { _, _, _ -> }
) : StrategyConversationExecutor {
    override fun execute(
        option: MemoryStrategyOption,
        scenario: StrategyComparisonScenario
    ): StrategyExecutionReport {
        Files.createDirectories(stateDirectory)

        val tracingLanguageModel = TracingLanguageModel(baseLanguageModel)
        val strategy = MemoryStrategyFactory.create(option.type, tracingLanguageModel)
        val storagePath = stateDirectory.resolve("${option.id}.json")
        Files.deleteIfExists(storagePath)

        val memoryManager = DefaultMemoryManager(
            languageModel = tracingLanguageModel,
            systemPrompt = buildSystemPrompt(),
            conversationStore = JsonConversationStore(storagePath),
            memoryStrategy = strategy,
            lifecycleListener = NoOpAgentLifecycleListener
        )
        val agent = MrAgent(
            languageModel = tracingLanguageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            memoryStrategy = strategy,
            memoryManager = memoryManager
        )

        val steps = scenario.prompts.mapIndexed { index, prompt ->
            val stepNumber = index + 1
            onStepStarted(option, stepNumber, scenario.prompts.size)

            val preview = agent.previewTokenStats(prompt)
            val response = agent.ask(prompt)
            val tracedRequests = tracingLanguageModel.drainRequests()
            check(tracedRequests.isNotEmpty()) {
                "Не удалось получить трассировку запросов к модели."
            }
            val finalRequest = tracedRequests.last()

            StrategyComparisonStepReport(
                stepNumber = stepNumber,
                userPrompt = prompt,
                assistantResponse = response.content,
                historyTokens = preview.historyTokens,
                promptTokensLocal = preview.promptTokensLocal,
                userPromptTokens = preview.userPromptTokens,
                providerPromptTokens = sumUsage(tracedRequests) { it.promptTokens },
                providerCompletionTokens = sumUsage(tracedRequests) { it.completionTokens },
                providerTotalTokens = sumUsage(tracedRequests) { it.totalTokens },
                modelCallCount = tracedRequests.size,
                internalModelCallCount = tracedRequests.size - 1,
                requestMessageCount = finalRequest.messages.size,
                requestCharacterCount = finalRequest.messages.sumOf { it.content.length },
                requestRoles = finalRequest.messages.map { it.role.name }
            ).also { step ->
                onStepFinished(option, step, scenario.prompts.size)
            }
        }

        return StrategyExecutionReport(
            strategyId = option.id,
            strategyDisplayName = option.displayName,
            strategyDescription = option.description,
            steps = steps
        )
    }

    private fun buildSystemPrompt(): String =
        "$defaultSystemPrompt\n\nТребования к формату ответа:\n${TextResponseFormat.formatInstruction}"

    private companion object {
        private const val defaultSystemPrompt =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}

/**
 * Суммирует указанную метрику usage по всем запросам шага, если провайдер вернул эти данные.
 */
private fun sumUsage(
    requests: List<TracedModelRequest>,
    selector: (llm.core.model.TokenUsage) -> Int
): Int? {
    val usages = requests.mapNotNull { it.response.usage }
    return usages.takeIf { it.isNotEmpty() }?.sumOf(selector)
}
